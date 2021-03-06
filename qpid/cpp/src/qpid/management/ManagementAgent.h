#ifndef _ManagementAgent_
#define _ManagementAgent_

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
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/Options.h"
#include "qpid/broker/Exchange.h"
#include "qpid/framing/Uuid.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Timer.h"
#include "qpid/broker/ConnectionToken.h"
#include "qpid/management/ManagementObject.h"
#include "qpid/management/ManagementEvent.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/Agent.h"
#include <qpid/framing/AMQFrame.h>
#include <memory>
#include <string>
#include <map>

namespace qpid {
namespace management {

struct IdAllocator;

class ManagementAgent
{
private:

    int threadPoolSize;

public:
    typedef enum {
    SEV_EMERG = 0,
    SEV_ALERT = 1,
    SEV_CRIT  = 2,
    SEV_ERROR = 3,
    SEV_WARN  = 4,
    SEV_NOTE  = 5,
    SEV_INFO  = 6,
    SEV_DEBUG = 7,
    SEV_DEFAULT = 8
    } severity_t;


    ManagementAgent ();
    virtual ~ManagementAgent ();

    /** Called before plugins are initialized */
    void configure       (const std::string& dataDir, uint16_t interval,
                          qpid::broker::Broker* broker, int threadPoolSize);
    /** Called after plugins are initialized. */
    void pluginsInitialized();

    /** Called by cluster to suppress management output during update. */
    void suppress(bool s) { suppressed = s; }

    void setInterval(uint16_t _interval) { interval = _interval; }
    void setExchange(qpid::broker::Exchange::shared_ptr mgmtExchange,
                     qpid::broker::Exchange::shared_ptr directExchange);
    void setExchangeV2(qpid::broker::Exchange::shared_ptr topicExchange,
                       qpid::broker::Exchange::shared_ptr directExchange);

    int  getMaxThreads   () { return threadPoolSize; }
    QPID_BROKER_EXTERN void registerClass   (const std::string& packageName,
                                             const std::string& className,
                                             uint8_t*    md5Sum,
                                             ManagementObject::writeSchemaCall_t schemaCall);
    QPID_BROKER_EXTERN void registerEvent   (const std::string& packageName,
                                             const std::string& eventName,
                                             uint8_t*    md5Sum,
                                             ManagementObject::writeSchemaCall_t schemaCall);
    QPID_BROKER_EXTERN ObjectId addObject   (ManagementObject* object,
                                             uint64_t          persistId = 0);
    QPID_BROKER_EXTERN void raiseEvent(const ManagementEvent& event,
                                       severity_t severity = SEV_DEFAULT);
    QPID_BROKER_EXTERN void clientAdded     (const std::string& routingKey);

    QPID_BROKER_EXTERN void clusterUpdate();

    bool dispatchCommand (qpid::broker::Deliverable&       msg,
                          const std::string&         routingKey,
                          const framing::FieldTable* args);

    const framing::Uuid& getUuid() const { return uuid; }

    void setAllocator(std::auto_ptr<IdAllocator> allocator);
    uint64_t allocateId(Manageable* object);

    /** Disallow a method. Attempts to call it will receive an exception with message. */
    void disallow(const std::string& className, const std::string& methodName, const std::string& message);

    /** Serialize my schemas as a binary blob into schemaOut */
    void exportSchemas(std::string& schemaOut);

    /** Serialize my remote-agent map as a binary blob into agentsOut */
    void exportAgents(std::string& agentsOut);

    /** Decode a serialized schemas and add to my schema cache */
    void importSchemas(framing::Buffer& inBuf);

    /** Decode a serialized agent map */
    void importAgents(framing::Buffer& inBuf);

    // these are in support of the managementSetup-state stuff, for synch'ing clustered brokers
    uint64_t getNextObjectId(void) { return nextObjectId; }
    void setNextObjectId(uint64_t o) { nextObjectId = o; }

    uint16_t getBootSequence(void) { return bootSequence; }
    void setBootSequence(uint16_t b) { bootSequence = b; }

private:
    struct Periodic : public qpid::sys::TimerTask
    {
        ManagementAgent& agent;

        Periodic (ManagementAgent& agent, uint32_t seconds);
        virtual ~Periodic ();
        void fire ();
    };

    //  Storage for tracking remote management agents, attached via the client
    //  management agent API.
    //
    struct RemoteAgent : public Manageable
    {
        ManagementAgent&  agent;
        uint32_t          brokerBank;
        uint32_t          agentBank;
        std::string       routingKey;
        ObjectId          connectionRef;
        qmf::org::apache::qpid::broker::Agent*    mgmtObject;
        RemoteAgent(ManagementAgent& _agent) : agent(_agent), mgmtObject(0) {}
        ManagementObject* GetManagementObject (void) const { return mgmtObject; }

        virtual ~RemoteAgent ();
        void encode(framing::Buffer& buffer) const;
        void decode(framing::Buffer& buffer);
        uint32_t encodedSize() const;
    };

    // TODO: Eventually replace string with entire reply-to structure.  reply-to
    //       currently assumes that the exchange is "amq.direct" even though it could
    //       in theory be specified differently.
    typedef std::map<ObjectId, RemoteAgent*> RemoteAgentMap;
    typedef std::vector<std::string>         ReplyToVector;

    //  Storage for known schema classes:
    //
    //  SchemaClassKey     -- Key elements for map lookups
    //  SchemaClassKeyComp -- Comparison class for SchemaClassKey
    //  SchemaClass        -- Non-key elements for classes
    //
    struct SchemaClassKey
    {
        std::string name;
        uint8_t     hash[16];

        void encode(framing::Buffer& buffer) const;
        void decode(framing::Buffer& buffer);
        uint32_t encodedSize() const;
    };

    struct SchemaClassKeyComp
    {
        bool operator() (const SchemaClassKey& lhs, const SchemaClassKey& rhs) const
        {
            if (lhs.name != rhs.name)
                return lhs.name < rhs.name;
            else
                for (int i = 0; i < 16; i++)
                    if (lhs.hash[i] != rhs.hash[i])
                        return lhs.hash[i] < rhs.hash[i];
            return false;
        }
    };


    struct SchemaClass
    {
        uint8_t  kind;
        ManagementObject::writeSchemaCall_t writeSchemaCall;
        std::string data;
        uint32_t pendingSequence;

        SchemaClass(uint8_t _kind=0, uint32_t seq=0) :
            kind(_kind), writeSchemaCall(0), pendingSequence(seq) {}
        SchemaClass(uint8_t _kind, ManagementObject::writeSchemaCall_t call) :
            kind(_kind), writeSchemaCall(call), pendingSequence(0) {}
        bool hasSchema () { return (writeSchemaCall != 0) || !data.empty(); }
        void appendSchema (framing::Buffer& buf);

        void encode(framing::Buffer& buffer) const;
        void decode(framing::Buffer& buffer);
        uint32_t encodedSize() const;
    };

    typedef std::map<SchemaClassKey, SchemaClass, SchemaClassKeyComp> ClassMap;
    typedef std::map<std::string, ClassMap> PackageMap;

    RemoteAgentMap               remoteAgents;
    PackageMap                   packages;

    //
    // Protected by userLock
    //
    ManagementObjectMap          managementObjects;
    ManagementObjectVector       deletedManagementObjects;

    //
    // Protected by addLock
    //
    ManagementObjectMap          newManagementObjects;
    ManagementObjectVector       newDeletedManagementObjects;

    framing::Uuid                uuid;

    //
    // Lock hierarchy:  If a thread needs to take both addLock and userLock,
    // it MUST take userLock first, then addLock.
    //
    sys::Mutex userLock;
    sys::Mutex addLock;

    qpid::broker::Exchange::shared_ptr mExchange;
    qpid::broker::Exchange::shared_ptr dExchange;
    qpid::broker::Exchange::shared_ptr v2Topic;
    qpid::broker::Exchange::shared_ptr v2Direct;
    std::string                  dataDir;
    uint16_t                     interval;
    qpid::broker::Broker*        broker;
    qpid::sys::Timer*            timer;
    uint16_t                     bootSequence;
    uint32_t                     nextObjectId;
    uint32_t                     brokerBank;
    uint32_t                     nextRemoteBank;
    uint32_t                     nextRequestSequence;
    bool                         clientWasAdded;
    const uint64_t               startTime;
    bool                         suppressed;

    std::auto_ptr<IdAllocator> allocator;

    typedef std::pair<std::string,std::string> MethodName;
    typedef std::map<MethodName, std::string> DisallowedMethods;
    DisallowedMethods disallowed;


#   define MA_BUFFER_SIZE 65536
    char inputBuffer[MA_BUFFER_SIZE];
    char outputBuffer[MA_BUFFER_SIZE];
    char eventBuffer[MA_BUFFER_SIZE];

    void writeData ();
    void periodicProcessing (void);
    void deleteObjectNowLH(const ObjectId& oid);
    void encodeHeader       (framing::Buffer& buf, uint8_t  opcode, uint32_t  seq = 0);
    bool checkHeader        (framing::Buffer& buf, uint8_t *opcode, uint32_t *seq);
    void sendBuffer         (framing::Buffer&             buf,
                             uint32_t                     length,
                             qpid::broker::Exchange::shared_ptr exchange,
                             std::string                  routingKey);
    void moveNewObjectsLH();

    bool authorizeAgentMessageLH(qpid::broker::Message& msg);
    void dispatchAgentCommandLH(qpid::broker::Message& msg);

    PackageMap::iterator findOrAddPackageLH(std::string name);
    void addClassLH(uint8_t                      kind,
                    PackageMap::iterator         pIter,
                    const std::string&           className,
                    uint8_t*                     md5Sum,
                    ManagementObject::writeSchemaCall_t schemaCall);
    void encodePackageIndication (framing::Buffer&     buf,
                                  PackageMap::iterator pIter);
    void encodeClassIndication (framing::Buffer&     buf,
                                PackageMap::iterator pIter,
                                ClassMap::iterator   cIter);
    bool     bankInUse (uint32_t bank);
    uint32_t allocateNewBank ();
    uint32_t assignBankLH (uint32_t requestedPrefix);
    void deleteOrphanedAgentsLH();
    void sendCommandComplete (std::string replyToKey, uint32_t sequence,
                              uint32_t code = 0, std::string text = std::string("OK"));
    void handleBrokerRequestLH  (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handlePackageQueryLH   (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handlePackageIndLH     (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleClassQueryLH     (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleClassIndLH       (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleSchemaRequestLH  (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleSchemaResponseLH (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleAttachRequestLH  (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence, const qpid::broker::ConnectionToken* connToken);
    void handleGetQueryLH       (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence);
    void handleMethodRequestLH  (framing::Buffer& inBuffer, std::string replyToKey, uint32_t sequence, const qpid::broker::ConnectionToken* connToken);

    size_t validateSchema(framing::Buffer&, uint8_t kind);
    size_t validateTableSchema(framing::Buffer&);
    size_t validateEventSchema(framing::Buffer&);
    ManagementObjectMap::iterator numericFind(const ObjectId& oid);
    std::string debugSnapshot();
};

}}
            
#endif  /*!_ManagementAgent_*/
