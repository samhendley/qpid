/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#include "qpid/broker/Broker.h"
#include "qpid/broker/DirectExchange.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/HeadersExchange.h"
#include "qpid/broker/MessageStoreModule.h"
#include "qpid/broker/NullMessageStore.h"
#include "qpid/broker/RecoveryManagerImpl.h"
#include "qpid/broker/SaslAuthenticator.h"
#include "qpid/broker/SecureConnectionFactory.h"
#include "qpid/broker/TopicExchange.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/ExpiryPolicy.h"

#include "qmf/org/apache/qpid/broker/Package.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerEcho.h"
#include "qmf/org/apache/qpid/broker/ArgsBrokerQueueMoveMessages.h"
#include "qpid/management/ManagementDirectExchange.h"
#include "qpid/management/ManagementTopicExchange.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/ProtocolFactory.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/ConnectionInputHandlerFactory.h"
#include "qpid/sys/TimeoutHandler.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/Address.h"
#include "qpid/Url.h"
#include "qpid/Version.h"

#include <boost/bind.hpp>

#include <iostream>
#include <memory>

using qpid::sys::ProtocolFactory;
using qpid::sys::Poller;
using qpid::sys::Dispatcher;
using qpid::sys::Thread;
using qpid::framing::FrameHandler;
using qpid::framing::ChannelId;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {

Broker::Options::Options(const std::string& name) :
    qpid::Options(name),
    noDataDir(0),
    port(DEFAULT_PORT),
    workerThreads(5),
    maxConnections(500),
    connectionBacklog(10),
    stagingThreshold(5000000),
    enableMgmt(1),
    mgmtPubInterval(10),
    queueCleanInterval(60*10),//10 minutes
    auth(SaslAuthenticator::available()),
    realm("QPID"),
    replayFlushLimit(0),
    replayHardLimit(0),
    queueLimit(100*1048576/*100M default limit*/),
    tcpNoDelay(false),
    requireEncrypted(false),
    maxSessionRate(0),
    asyncQueueEvents(false)     // Must be false in a cluster.
{
    int c = sys::SystemInfo::concurrency();
    workerThreads=c+1;
    std::string home = getHome();

    if (home.length() == 0)
        dataDir += DEFAULT_DATA_DIR_LOCATION;
    else
        dataDir += home;
    dataDir += DEFAULT_DATA_DIR_NAME;

    addOptions()
        ("data-dir", optValue(dataDir,"DIR"), "Directory to contain persistent data generated by the broker")
        ("no-data-dir", optValue(noDataDir), "Don't use a data directory.  No persistent configuration will be loaded or stored")
        ("port,p", optValue(port,"PORT"), "Tells the broker to listen on PORT")
        ("worker-threads", optValue(workerThreads, "N"), "Sets the broker thread pool size")
        ("max-connections", optValue(maxConnections, "N"), "Sets the maximum allowed connections")
        ("connection-backlog", optValue(connectionBacklog, "N"), "Sets the connection backlog limit for the server socket")
        ("staging-threshold", optValue(stagingThreshold, "N"), "Stages messages over N bytes to disk")
        ("mgmt-enable,m", optValue(enableMgmt,"yes|no"), "Enable Management")
        ("mgmt-pub-interval", optValue(mgmtPubInterval, "SECONDS"), "Management Publish Interval")
        ("queue-purge-interval", optValue(queueCleanInterval, "SECONDS"),
         "Interval between attempts to purge any expired messages from queues")
        ("auth", optValue(auth, "yes|no"), "Enable authentication, if disabled all incoming connections will be trusted")
        ("realm", optValue(realm, "REALM"), "Use the given realm when performing authentication")
        ("default-queue-limit", optValue(queueLimit, "BYTES"), "Default maximum size for queues (in bytes)")
        ("tcp-nodelay", optValue(tcpNoDelay), "Set TCP_NODELAY on TCP connections")
        ("require-encryption", optValue(requireEncrypted), "Only accept connections that are encrypted")
        ("known-hosts-url", optValue(knownHosts, "URL or 'none'"), "URL to send as 'known-hosts' to clients ('none' implies empty list)")
        ("max-session-rate", optValue(maxSessionRate, "MESSAGES/S"), "Sets the maximum message rate per session (0=unlimited)")
        ("async-queue-events", optValue(asyncQueueEvents, "yes|no"), "Set Queue Events async, used for services like replication");
}

const std::string empty;
const std::string amq_direct("amq.direct");
const std::string amq_topic("amq.topic");
const std::string amq_fanout("amq.fanout");
const std::string amq_match("amq.match");
const std::string qpid_management("qpid.management");
const std::string knownHostsNone("none");

Broker::Broker(const Broker::Options& conf) :
    poller(new Poller),
    config(conf),
    managementAgent(conf.enableMgmt ? new ManagementAgent() : 0),
    store(new NullMessageStore),
    acl(0),
    dataDir(conf.noDataDir ? std::string() : conf.dataDir),
    queues(this),
    exchanges(this),
    links(this),
    factory(new SecureConnectionFactory(*this)),
    dtxManager(timer),
    sessionManager(
        qpid::SessionState::Configuration(
            conf.replayFlushLimit*1024, // convert kb to bytes.
            conf.replayHardLimit*1024),
        *this),
    queueCleaner(queues, timer),
    queueEvents(poller,!conf.asyncQueueEvents), 
    recovery(true),
    clusterUpdatee(false),
    expiryPolicy(new ExpiryPolicy),
    connectionCounter(conf.maxConnections),
    getKnownBrokers(boost::bind(&Broker::getKnownBrokersImpl, this))
{
    if (conf.enableMgmt) {
        QPID_LOG(info, "Management enabled");
        managementAgent->configure(dataDir.isEnabled() ? dataDir.getPath() : string(),
                                   conf.mgmtPubInterval, this, conf.workerThreads + 3);
        _qmf::Package packageInitializer(managementAgent.get());

        System* system = new System (dataDir.isEnabled() ? dataDir.getPath() : string(), this);
        systemObject = System::shared_ptr(system);

        mgmtObject = new _qmf::Broker(managementAgent.get(), this, system, conf.port);
        mgmtObject->set_workerThreads(conf.workerThreads);
        mgmtObject->set_maxConns(conf.maxConnections);
        mgmtObject->set_connBacklog(conf.connectionBacklog);
        mgmtObject->set_stagingThreshold(conf.stagingThreshold);
        mgmtObject->set_mgmtPubInterval(conf.mgmtPubInterval);
        mgmtObject->set_version(qpid::version);
        if (dataDir.isEnabled())
            mgmtObject->set_dataDir(dataDir.getPath());
        else
            mgmtObject->clr_dataDir();

        managementAgent->addObject(mgmtObject, 0x1000000000000002LL);

        // Since there is currently no support for virtual hosts, a placeholder object
        // representing the implied single virtual host is added here to keep the
        // management schema correct.
        Vhost* vhost = new Vhost(this, this);
        vhostObject = Vhost::shared_ptr(vhost);
        framing::Uuid uuid(managementAgent->getUuid());
        federationTag = uuid.str();
        vhostObject->setFederationTag(federationTag);

        queues.setParent(vhost);
        exchanges.setParent(vhost);
        links.setParent(vhost);
    } else {
        // Management is disabled so there is no broker management ID.
        // Create a unique uuid to use as the federation tag.
        framing::Uuid uuid(true);
        federationTag = uuid.str();
    }

    QueuePolicy::setDefaultMaxSize(conf.queueLimit);
    queues.setQueueEvents(&queueEvents);

    // Early-Initialize plugins
    Plugin::earlyInitAll(*this);

    // If no plugin store module registered itself, set up the null store.
    if (NullMessageStore::isNullStore(store.get())) 
        setStore();

    exchanges.declare(empty, DirectExchange::typeName); // Default exchange.

    if (store.get() != 0) {
        // The cluster plug-in will setRecovery(false) on all but the first
        // broker to join a cluster.
        if (getRecovery()) {
            RecoveryManagerImpl recoverer(queues, exchanges, links, dtxManager,
                                          conf.stagingThreshold);
            store->recover(recoverer);
        }
        else {
            QPID_LOG(notice, "Cluster recovery: recovered journal data discarded and journal files pushed down");
            store->truncateInit(true); // save old files in subdir
        }
    }

    //ensure standard exchanges exist (done after recovery from store)
    declareStandardExchange(amq_direct, DirectExchange::typeName);
    declareStandardExchange(amq_topic, TopicExchange::typeName);
    declareStandardExchange(amq_fanout, FanOutExchange::typeName);
    declareStandardExchange(amq_match, HeadersExchange::typeName);

    if(conf.enableMgmt) {
        exchanges.declare(qpid_management, ManagementTopicExchange::typeName);
        Exchange::shared_ptr mExchange = exchanges.get(qpid_management);
        Exchange::shared_ptr dExchange = exchanges.get(amq_direct);
        managementAgent->setExchange(mExchange, dExchange);
        boost::dynamic_pointer_cast<ManagementTopicExchange>(mExchange)->setManagmentAgent(managementAgent.get(), 1);

        std::string qmfTopic("qmf.default.topic");
        std::string qmfDirect("qmf.default.direct");

        std::pair<Exchange::shared_ptr, bool> topicPair(exchanges.declare(qmfTopic, ManagementTopicExchange::typeName));
        std::pair<Exchange::shared_ptr, bool> directPair(exchanges.declare(qmfDirect, ManagementDirectExchange::typeName));

        boost::dynamic_pointer_cast<ManagementDirectExchange>(directPair.first)->setManagmentAgent(managementAgent.get(), 2);
        boost::dynamic_pointer_cast<ManagementTopicExchange>(topicPair.first)->setManagmentAgent(managementAgent.get(), 2);

        managementAgent->setExchangeV2(topicPair.first, directPair.first);
    }
    else
        QPID_LOG(info, "Management not enabled");

    /**
     * SASL setup, can fail and terminate startup
     */
    if (conf.auth) {
        SaslAuthenticator::init(qpid::saslName);
        QPID_LOG(info, "SASL enabled");
    } else {
        QPID_LOG(notice, "SASL disabled: No Authentication Performed");
    }

    // Initialize plugins
    Plugin::initializeAll(*this);

    if (managementAgent.get()) managementAgent->pluginsInitialized();

    if (conf.queueCleanInterval) {
        queueCleaner.start(conf.queueCleanInterval * qpid::sys::TIME_SEC);
    }

    //initialize known broker urls (TODO: add support for urls for other transports (SSL, RDMA)):
    if (conf.knownHosts.empty()) {
        boost::shared_ptr<ProtocolFactory> factory = getProtocolFactory(TCP_TRANSPORT);
        if (factory) {
            knownBrokers.push_back ( qpid::Url::getIpAddressesUrl ( factory->getPort() ) );
        }
    } else if (conf.knownHosts != knownHostsNone) {
        knownBrokers.push_back(Url(conf.knownHosts));
    }
}

void Broker::declareStandardExchange(const std::string& name, const std::string& type)
{
    bool storeEnabled = store.get() != NULL;
    std::pair<Exchange::shared_ptr, bool> status = exchanges.declare(name, type, storeEnabled);
    if (status.second && storeEnabled) {
        store->create(*status.first, framing::FieldTable ());
    }
}


boost::intrusive_ptr<Broker> Broker::create(int16_t port)
{
    Options config;
    config.port=port;
    return create(config);
}

boost::intrusive_ptr<Broker> Broker::create(const Options& opts)
{
    return boost::intrusive_ptr<Broker>(new Broker(opts));
}

void Broker::setStore (boost::shared_ptr<MessageStore>& _store)
{
    store.reset(new MessageStoreModule (_store));
    setStore();
}

void Broker::setStore () {
    queues.setStore     (store.get());
    dtxManager.setStore (store.get());
    links.setStore      (store.get());
}

void Broker::run() {
    QPID_LOG(notice, "Broker running");
    Dispatcher d(poller);
    int numIOThreads = config.workerThreads;
    std::vector<Thread> t(numIOThreads-1);

    // Run n-1 io threads
    for (int i=0; i<numIOThreads-1; ++i)
        t[i] = Thread(d);

    // Run final thread
    d.run();

    // Now wait for n-1 io threads to exit
    for (int i=0; i<numIOThreads-1; ++i) {
        t[i].join();
    }
}

void Broker::shutdown() {
    // NB: this function must be async-signal safe, it must not
    // call any function that is not async-signal safe.
    // Any unsafe shutdown actions should be done in the destructor.
    poller->shutdown();
}

Broker::~Broker() {
    shutdown();
    queueEvents.shutdown();
    finalize();                 // Finalize any plugins.
    if (config.auth)
        SaslAuthenticator::fini();
    QPID_LOG(notice, "Shut down");
}

ManagementObject* Broker::GetManagementObject(void) const
{
    return (ManagementObject*) mgmtObject;
}

Manageable* Broker::GetVhostObject(void) const
{
    return vhostObject.get();
}

Manageable::status_t Broker::ManagementMethod (uint32_t methodId,
                                               Args&    args,
                                               string&)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG (debug, "Broker::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
    case _qmf::Broker::METHOD_ECHO :
        status = Manageable::STATUS_OK;
        break;
    case _qmf::Broker::METHOD_CONNECT : {
        _qmf::ArgsBrokerConnect& hp=
            dynamic_cast<_qmf::ArgsBrokerConnect&>(args);

        string transport = hp.i_transport.empty() ? TCP_TRANSPORT : hp.i_transport;
        if (!getProtocolFactory(transport)) {
            QPID_LOG(error, "Transport '" << transport << "' not supported");
            return  Manageable::STATUS_NOT_IMPLEMENTED;
        }
        std::pair<Link::shared_ptr, bool> response =
            links.declare (hp.i_host, hp.i_port, transport, hp.i_durable,
                           hp.i_authMechanism, hp.i_username, hp.i_password);
        if (hp.i_durable && response.second)
            store->create(*response.first);
        status = Manageable::STATUS_OK;
        break;
      }
    case _qmf::Broker::METHOD_QUEUEMOVEMESSAGES : {
        _qmf::ArgsBrokerQueueMoveMessages& moveArgs=
            dynamic_cast<_qmf::ArgsBrokerQueueMoveMessages&>(args);
	if (queueMoveMessages(moveArgs.i_srcQueue, moveArgs.i_destQueue, moveArgs.i_qty))
            status = Manageable::STATUS_OK;
	else
            return Manageable::STATUS_PARAMETER_INVALID;
        break;
      }
   default:
        status = Manageable::STATUS_NOT_IMPLEMENTED;
        break;
    }

    return status;
}

boost::shared_ptr<ProtocolFactory> Broker::getProtocolFactory(const std::string& name) const {
    ProtocolFactoryMap::const_iterator i
        = name.empty() ? protocolFactories.begin() : protocolFactories.find(name);
    if (i == protocolFactories.end()) return boost::shared_ptr<ProtocolFactory>();
    else return i->second;
}

uint16_t Broker::getPort(const std::string& name) const  {
    boost::shared_ptr<ProtocolFactory> factory = getProtocolFactory(name);
    if (factory) {
        return factory->getPort();
    } else {
        throw NoSuchTransportException(QPID_MSG("No such transport: '" << name << "'"));
    }
}

void Broker::registerProtocolFactory(const std::string& name, ProtocolFactory::shared_ptr protocolFactory) {
    protocolFactories[name] = protocolFactory;
}

void Broker::accept() {
    for (ProtocolFactoryMap::const_iterator i = protocolFactories.begin(); i != protocolFactories.end(); i++) {
        i->second->accept(poller, factory.get());
    }
}

void Broker::connect(
    const std::string& host, uint16_t port, const std::string& transport,
    boost::function2<void, int, std::string> failed,
    sys::ConnectionCodec::Factory* f)
{
    boost::shared_ptr<ProtocolFactory> pf = getProtocolFactory(transport);
    if (pf) pf->connect(poller, host, port, f ? f : factory.get(), failed);
    else throw NoSuchTransportException(QPID_MSG("Unsupported transport type: " << transport));
}

void Broker::connect(
    const Url& url,
    boost::function2<void, int, std::string> failed,
    sys::ConnectionCodec::Factory* f)
{
    url.throwIfEmpty();
    const TcpAddress* addr=url[0].get<TcpAddress>();
    connect(addr->host, addr->port, TCP_TRANSPORT, failed, f);
}

uint32_t Broker::queueMoveMessages(
     const std::string& srcQueue,
     const std::string& destQueue,
     uint32_t  qty)
{
  Queue::shared_ptr src_queue = queues.find(srcQueue);
  if (!src_queue)
    return 0;
  Queue::shared_ptr dest_queue = queues.find(destQueue);
  if (!dest_queue)
    return 0;

  return src_queue->move(dest_queue, qty);
}


boost::shared_ptr<sys::Poller> Broker::getPoller() { return poller; }

std::vector<Url>
Broker::getKnownBrokersImpl()
{
    return knownBrokers;
}

void Broker::setClusterTimer(std::auto_ptr<sys::Timer> t) {
    clusterTimer = t;
}

const std::string Broker::TCP_TRANSPORT("tcp");

}} // namespace qpid::broker

