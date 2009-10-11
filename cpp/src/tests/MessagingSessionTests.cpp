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
#include "unit_test.h"
#include "test_tools.h"
#include "BrokerFixture.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/ListContent.h"
#include "qpid/messaging/ListView.h"
#include "qpid/messaging/MapContent.h"
#include "qpid/messaging/MapView.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/MessageListener.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Session.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/Time.h"
#include <boost/assign.hpp>
#include <boost/format.hpp>
#include <string>
#include <vector>

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(MessagingSessionTests)

using namespace qpid::messaging;
using namespace qpid;
using qpid::broker::Broker;

struct BrokerAdmin
{
    qpid::client::Connection connection;
    qpid::client::Session session;

    BrokerAdmin(uint16_t port)
    {
        connection.open("localhost", port);
        session = connection.newSession();
    }

    void createQueue(const std::string& name)
    {
        session.queueDeclare(qpid::client::arg::queue=name);
    }

    void deleteQueue(const std::string& name)
    {
        session.queueDelete(qpid::client::arg::queue=name);
    }

    void createExchange(const std::string& name, const std::string& type)
    {
        session.exchangeDeclare(qpid::client::arg::exchange=name, qpid::client::arg::type=type);
    }

    void deleteExchange(const std::string& name)
    {
        session.exchangeDelete(qpid::client::arg::exchange=name);
    }

    ~BrokerAdmin()
    {
        session.close();
        connection.close();
    }
};

struct MessagingFixture : public BrokerFixture
{
    Connection connection;
    Session session;
    BrokerAdmin admin;

    MessagingFixture(Broker::Options opts = Broker::Options()) :
        BrokerFixture(opts),
        connection(Connection::open((boost::format("amqp:tcp:localhost:%1%") % (broker->getPort(Broker::TCP_TRANSPORT))).str())),
        session(connection.newSession()),
        admin(broker->getPort(Broker::TCP_TRANSPORT)) {}

    ~MessagingFixture()
    {
        session.close();
        connection.close();
    }
};

struct QueueFixture : MessagingFixture
{
    std::string queue;

    QueueFixture(const std::string& name = "test-queue") : queue(name)
    {
        admin.createQueue(queue);
    }

    ~QueueFixture()
    {
        admin.deleteQueue(queue);
    }

};

struct TopicFixture : MessagingFixture
{
    std::string topic;

    TopicFixture(const std::string& name = "test-topic", const std::string& type="fanout") : topic(name)
    {
        admin.createExchange(topic, type);
    }

    ~TopicFixture()
    {
        admin.deleteExchange(topic);
    }

};

struct MultiQueueFixture : MessagingFixture
{
    typedef std::vector<std::string>::const_iterator const_iterator;
    std::vector<std::string> queues;

    MultiQueueFixture(const std::vector<std::string>& names = boost::assign::list_of<std::string>("q1")("q2")("q3")) : queues(names)
    {
        for (const_iterator i = queues.begin(); i != queues.end(); ++i) {
            admin.createQueue(*i);
        }
    }

    ~MultiQueueFixture()
    {
        for (const_iterator i = queues.begin(); i != queues.end(); ++i) {
            admin.deleteQueue(*i);
        }
    }

};

struct MessageDataCollector : MessageListener
{
    std::vector<std::string> messageData;

    void received(Message& message) {
        messageData.push_back(message.getContent());
    }
};

std::vector<std::string> fetch(Receiver& receiver, int count, qpid::sys::Duration timeout=qpid::sys::TIME_SEC*5)
{
    std::vector<std::string> data;
    Message message;
    for (int i = 0; i < count && receiver.fetch(message, timeout); i++) {
        data.push_back(message.getContent());
    }
    return data;
}

QPID_AUTO_TEST_CASE(testSimpleSendReceive)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out("test-message");
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * qpid::sys::TIME_SEC);
    fix.session.acknowledge();
    BOOST_CHECK_EQUAL(in.getContent(), out.getContent());
}

QPID_AUTO_TEST_CASE(testSendReceiveHeaders)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out("test-message");
    for (uint i = 0; i < 10; ++i) {
        out.getHeaders()["a"] = i;
        sender.send(out);
    }
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in;
    for (uint i = 0; i < 10; ++i) {
        BOOST_CHECK(receiver.fetch(in, 5 * qpid::sys::TIME_SEC));
        BOOST_CHECK_EQUAL(in.getContent(), out.getContent());
        BOOST_CHECK_EQUAL(in.getHeaders()["a"].asUint32(), i);
        fix.session.acknowledge();
    }
}

QPID_AUTO_TEST_CASE(testSenderError)
{
    MessagingFixture fix;
    //TODO: this is the wrong type for the exception; define explicit set in messaging namespace
    BOOST_CHECK_THROW(fix.session.createSender("NonExistentAddress"), qpid::framing::NotFoundException);
}

QPID_AUTO_TEST_CASE(testReceiverError)
{
    MessagingFixture fix;
    //TODO: this is the wrong type for the exception; define explicit set in messaging namespace
    BOOST_CHECK_THROW(fix.session.createReceiver("NonExistentAddress"), qpid::framing::NotFoundException);
}

QPID_AUTO_TEST_CASE(testSimpleTopic)
{
    TopicFixture fix;

    Sender sender = fix.session.createSender(fix.topic);
    Message msg("one");
    sender.send(msg);
    Receiver sub1 = fix.session.createReceiver(fix.topic);
    sub1.setCapacity(10u);
    sub1.start();
    msg.setContent("two");
    sender.send(msg);
    Receiver sub2 = fix.session.createReceiver(fix.topic);
    sub2.setCapacity(10u);
    sub2.start();
    msg.setContent("three");
    sender.send(msg);
    Receiver sub3 = fix.session.createReceiver(fix.topic);
    sub3.setCapacity(10u);
    sub3.start();
    msg.setContent("four");
    sender.send(msg);
    BOOST_CHECK_EQUAL(fetch(sub2, 2), boost::assign::list_of<std::string>("three")("four"));
    sub2.cancel();

    msg.setContent("five");
    sender.send(msg);
    BOOST_CHECK_EQUAL(fetch(sub1, 4), boost::assign::list_of<std::string>("two")("three")("four")("five"));
    BOOST_CHECK_EQUAL(fetch(sub3, 2), boost::assign::list_of<std::string>("four")("five"));
    Message in;
    BOOST_CHECK(!sub2.fetch(in, 0));//TODO: or should this raise an error?


    //TODO: check pending messages...
}

QPID_AUTO_TEST_CASE(testSessionFetch)
{
    MultiQueueFixture fix;

    for (uint i = 0; i < fix.queues.size(); i++) {
        Receiver r = fix.session.createReceiver(fix.queues[i]);
        r.setCapacity(10u);
        r.start();//TODO: add Session::start
    }

    for (uint i = 0; i < fix.queues.size(); i++) {
        Sender s = fix.session.createSender(fix.queues[i]);
        Message msg((boost::format("Message_%1%") % (i+1)).str());
        s.send(msg);
    }

    for (uint i = 0; i < fix.queues.size(); i++) {
        Message msg;
        BOOST_CHECK(fix.session.fetch(msg, qpid::sys::TIME_SEC));
        BOOST_CHECK_EQUAL(msg.getContent(), (boost::format("Message_%1%") % (i+1)).str());
    }
}

QPID_AUTO_TEST_CASE(testSessionDispatch)
{
    MultiQueueFixture fix;

    MessageDataCollector collector;
    for (uint i = 0; i < fix.queues.size(); i++) {
        Receiver r = fix.session.createReceiver(fix.queues[i]);
        r.setListener(&collector);
        r.setCapacity(10u);
        r.start();//TODO: add Session::start
    }

    for (uint i = 0; i < fix.queues.size(); i++) {
        Sender s = fix.session.createSender(fix.queues[i]);
        Message msg((boost::format("Message_%1%") % (i+1)).str());
        s.send(msg);
    }

    while (fix.session.dispatch(qpid::sys::TIME_SEC)) ;

    BOOST_CHECK_EQUAL(collector.messageData, boost::assign::list_of<std::string>("Message_1")("Message_2")("Message_3"));
}


QPID_AUTO_TEST_CASE(testMapMessage)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out;
    MapContent content(out);
    content["abc"] = "def";
    content["pi"] = 3.14f;
    content.encode();
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * qpid::sys::TIME_SEC);
    MapView view(in);
    BOOST_CHECK_EQUAL(view["abc"].asString(), "def");
    BOOST_CHECK_EQUAL(view["pi"].asFloat(), 3.14f);
    fix.session.acknowledge();
}

QPID_AUTO_TEST_CASE(testListMessage)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message out;
    ListContent content(out);
    content.push_back(Variant("abc"));
    content.push_back(Variant(1234));
    content.push_back(Variant("def"));
    content.push_back(Variant(56.789));
    content.encode();
    sender.send(out);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * qpid::sys::TIME_SEC);
    ListView view(in);
    BOOST_CHECK_EQUAL(view.size(), content.size());
    BOOST_CHECK_EQUAL(view.front().asString(), "abc");
    BOOST_CHECK_EQUAL(view.back().asDouble(), 56.789);

    ListView::const_iterator i = view.begin();
    BOOST_CHECK(i != view.end());
    BOOST_CHECK_EQUAL(i->asString(), "abc");
    BOOST_CHECK(++i != view.end());
    BOOST_CHECK_EQUAL(i->asInt64(), 1234);
    BOOST_CHECK(++i != view.end());
    BOOST_CHECK_EQUAL(i->asString(), "def");
    BOOST_CHECK(++i != view.end());
    BOOST_CHECK_EQUAL(i->asDouble(), 56.789);
    BOOST_CHECK(++i == view.end());

    fix.session.acknowledge();
}

QPID_AUTO_TEST_CASE(testReject)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    Message m1("reject-me");
    sender.send(m1);
    Message m2("accept-me");
    sender.send(m2);
    Receiver receiver = fix.session.createReceiver(fix.queue);
    Message in = receiver.fetch(5 * qpid::sys::TIME_SEC);
    BOOST_CHECK_EQUAL(in.getContent(), m1.getContent());
    fix.session.reject(in);
    in = receiver.fetch(5 * qpid::sys::TIME_SEC);
    BOOST_CHECK_EQUAL(in.getContent(), m2.getContent());
    fix.session.acknowledge();
}

QPID_AUTO_TEST_CASE(testAvailable)
{
    MultiQueueFixture fix;

    Receiver r1 = fix.session.createReceiver(fix.queues[0]);
    r1.setCapacity(100);
    r1.start();

    Receiver r2 = fix.session.createReceiver(fix.queues[1]);
    r2.setCapacity(100);
    r2.start();

    Sender s1 = fix.session.createSender(fix.queues[0]);
    Sender s2 = fix.session.createSender(fix.queues[1]);

    for (uint i = 0; i < 10; ++i) {
        s1.send(Message((boost::format("A_%1%") % (i+1)).str()));
    }
    for (uint i = 0; i < 5; ++i) {
        s2.send(Message((boost::format("B_%1%") % (i+1)).str()));
    }
    qpid::sys::sleep(1);//is there any avoid an arbitrary sleep while waiting for messages to be dispatched?
    for (uint i = 0; i < 5; ++i) {
        BOOST_CHECK_EQUAL(fix.session.available(), 15u - 2*i);
        BOOST_CHECK_EQUAL(r1.available(), 10u - i);
        BOOST_CHECK_EQUAL(r1.fetch().getContent(), (boost::format("A_%1%") % (i+1)).str());
        BOOST_CHECK_EQUAL(r2.available(), 5u - i);
        BOOST_CHECK_EQUAL(r2.fetch().getContent(), (boost::format("B_%1%") % (i+1)).str());
        fix.session.acknowledge();
    }
    for (uint i = 5; i < 10; ++i) {
        BOOST_CHECK_EQUAL(fix.session.available(), 10u - i);
        BOOST_CHECK_EQUAL(r1.available(), 10u - i);
        BOOST_CHECK_EQUAL(r1.fetch().getContent(), (boost::format("A_%1%") % (i+1)).str());
    }
}

QPID_AUTO_TEST_CASE(testPendingAck)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    for (uint i = 0; i < 10; ++i) {
        sender.send(Message((boost::format("Message_%1%") % (i+1)).str()));
    }
    Receiver receiver = fix.session.createReceiver(fix.queue);
    for (uint i = 0; i < 10; ++i) {
        BOOST_CHECK_EQUAL(receiver.fetch().getContent(), (boost::format("Message_%1%") % (i+1)).str());
    }
    BOOST_CHECK_EQUAL(fix.session.pendingAck(), 0u);
    fix.session.acknowledge();
    BOOST_CHECK_EQUAL(fix.session.pendingAck(), 10u);
    fix.session.sync();
    BOOST_CHECK_EQUAL(fix.session.pendingAck(), 0u);
}

QPID_AUTO_TEST_CASE(testPendingSend)
{
    QueueFixture fix;
    Sender sender = fix.session.createSender(fix.queue);
    for (uint i = 0; i < 10; ++i) {
        sender.send(Message((boost::format("Message_%1%") % (i+1)).str()));
    }
    //Note: this test relies on 'inside knowledge' of the sender
    //implementation and the fact that the simple test case makes it
    //possible to predict when completion information will be sent to
    //the client. TODO: is there a better way of testing this?
    BOOST_CHECK_EQUAL(sender.pending(), 10u);
    fix.session.sync();
    BOOST_CHECK_EQUAL(sender.pending(), 0u);

    Receiver receiver = fix.session.createReceiver(fix.queue);
    for (uint i = 0; i < 10; ++i) {
        BOOST_CHECK_EQUAL(receiver.fetch().getContent(), (boost::format("Message_%1%") % (i+1)).str());
    }
    fix.session.acknowledge();
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests