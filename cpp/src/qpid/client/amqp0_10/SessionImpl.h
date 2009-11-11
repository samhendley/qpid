#ifndef QPID_CLIENT_AMQP0_10_SESSIONIMPL_H
#define QPID_CLIENT_AMQP0_10_SESSIONIMPL_H

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
#include "qpid/messaging/SessionImpl.h"
#include "qpid/messaging/Variant.h"
#include "qpid/client/Session.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/client/amqp0_10/AddressResolution.h"
#include "qpid/client/amqp0_10/IncomingMessages.h"
#include "qpid/sys/Mutex.h"

namespace qpid {

namespace messaging {
struct Address;
struct Filter;
class Message;
class Receiver;
class Sender;
class Session;
}

namespace client {
namespace amqp0_10 {

class ConnectionImpl;
class ReceiverImpl;
class SenderImpl;

/**
 * Implementation of the protocol independent Session interface using
 * AMQP 0-10.
 */
class SessionImpl : public qpid::messaging::SessionImpl
{
  public:
    SessionImpl(ConnectionImpl&);
    void commit();
    void rollback();
    void acknowledge();
    void reject(qpid::messaging::Message&);
    void close();
    void sync();
    void flush();
    qpid::messaging::Address createTempQueue(const std::string& baseName);
    qpid::messaging::Sender createSender(const qpid::messaging::Address& address,
                                         const qpid::messaging::VariantMap& options);
    qpid::messaging::Receiver createReceiver(const qpid::messaging::Address& address,
                                             const qpid::messaging::VariantMap& options);
    qpid::messaging::Receiver createReceiver(const qpid::messaging::Address& address, 
                                             const qpid::messaging::Filter& filter,
                                             const qpid::messaging::VariantMap& options);

    void* getLastConfirmedSent();
    void* getLastConfirmedAcknowledged();

    bool fetch(qpid::messaging::Message& message, qpid::sys::Duration timeout);
    qpid::messaging::Message fetch(qpid::sys::Duration timeout);
    bool dispatch(qpid::sys::Duration timeout);

    bool get(ReceiverImpl& receiver, qpid::messaging::Message& message, qpid::sys::Duration timeout);    

    void receiverCancelled(const std::string& name);
    void senderCancelled(const std::string& name);

    uint32_t available();
    uint32_t available(const std::string& destination);

    uint32_t pendingAck();
    uint32_t pendingAck(const std::string& destination);

    void setSession(qpid::client::Session);

    template <class T> bool execute(T& f)
    {
        try {
            qpid::sys::Mutex::ScopedLock l(lock);
            f();
            return true;
        } catch (TransportFailure&) {
            reconnect();
            return false;
        }
    }

    static SessionImpl& convert(qpid::messaging::Session&);

  private:
    typedef std::map<std::string, qpid::messaging::Receiver> Receivers;
    typedef std::map<std::string, qpid::messaging::Sender> Senders;

    qpid::sys::Mutex lock;
    ConnectionImpl& connection;
    qpid::client::Session session;
    AddressResolution resolver;
    IncomingMessages incoming;
    Receivers receivers;
    Senders senders;

    bool acceptAny(qpid::messaging::Message*, bool, IncomingMessages::MessageTransfer&);
    bool accept(ReceiverImpl*, qpid::messaging::Message*, bool, IncomingMessages::MessageTransfer&);
    bool getIncoming(IncomingMessages::Handler& handler, qpid::sys::Duration timeout);
    void reconnect();

    void commitImpl();
    void rollbackImpl();
    void acknowledgeImpl();
    void rejectImpl(qpid::messaging::Message&);
    void closeImpl();
    void syncImpl();
    void flushImpl();
    qpid::messaging::Sender createSenderImpl(const qpid::messaging::Address& address, 
                                             const qpid::messaging::VariantMap& options);
    qpid::messaging::Receiver createReceiverImpl(const qpid::messaging::Address& address, 
                                                 const qpid::messaging::Filter* filter, 
                                                 const qpid::messaging::VariantMap& options);
    uint32_t availableImpl(const std::string* destination);
    uint32_t pendingAckImpl(const std::string* destination);

    //functors for public facing methods (allows locking and retry
    //logic to be centralised)
    struct Command
    {
        SessionImpl& impl;

        Command(SessionImpl& i) : impl(i) {}
    };

    struct Commit : Command
    {
        Commit(SessionImpl& i) : Command(i) {}
        void operator()() { impl.commitImpl(); }
    };

    struct Rollback : Command
    {
        Rollback(SessionImpl& i) : Command(i) {}
        void operator()() { impl.rollbackImpl(); }
    };

    struct Acknowledge : Command
    {
        Acknowledge(SessionImpl& i) : Command(i) {}
        void operator()() { impl.acknowledgeImpl(); }
    };

    struct Sync : Command
    {
        Sync(SessionImpl& i) : Command(i) {}
        void operator()() { impl.syncImpl(); }
    };

    struct Flush : Command
    {
        Flush(SessionImpl& i) : Command(i) {}
        void operator()() { impl.flushImpl(); }
    };

    struct Reject : Command
    {
        qpid::messaging::Message& message;

        Reject(SessionImpl& i, qpid::messaging::Message& m) : Command(i), message(m) {}
        void operator()() { impl.rejectImpl(message); }
    };
    
    struct CreateSender;
    struct CreateReceiver;
    struct PendingAck;
    struct Available;

    //helper templates for some common patterns
    template <class F> bool execute()
    {
        F f(*this);
        return execute(f);
    }
    
    template <class F> void retry()
    {
        while (!execute<F>()) {}
    }
    
    template <class F, class P> bool execute1(P p)
    {
        F f(*this, p);
        return execute(f);
    }

    template <class F, class R, class P> R get1(P p)
    {
        F f(*this, p);
        while (!execute(f)) {}
        return f.result;
    }
};
}}} // namespace qpid::client::amqp0_10

#endif  /*!QPID_CLIENT_AMQP0_10_SESSIONIMPL_H*/