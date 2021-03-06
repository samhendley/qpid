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
#include "qpid/client/amqp0_10/FailoverUpdates.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Session.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "qpid/log/Statement.h"
#include "qpid/Exception.h"
#include "qpid/Url.h"
#include <vector>

namespace qpid {
namespace client {
namespace amqp0_10 {

using namespace qpid::messaging;

struct FailoverUpdatesImpl : qpid::sys::Runnable
{
    Connection connection;
    Session session;
    Receiver receiver;
    qpid::sys::Thread thread;
    volatile bool quit;

    FailoverUpdatesImpl(Connection& c) : connection(c), quit(false)
    {
        session = connection.newSession("failover-updates");
        receiver = session.createReceiver("amq.failover");
        thread = qpid::sys::Thread(*this);
    }

    void run()
    {
        try {
            Message message;
            while (!quit && receiver.fetch(message)) {
                connection.setOption("urls", message.getHeaders()["amq.failover"]);
                session.acknowledge();
            }
        } catch (const qpid::TransportFailure& e) {
            QPID_LOG(warning, "Failover updates stopped on loss of connection. " << e.what());
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Failover updates stopped due to exception: " << e.what());
        }
        receiver.close();
        session.close();
    }

    void wait()
    {
        quit = true;
        thread.join();
    }
};

FailoverUpdates::FailoverUpdates(Connection& connection) : impl(new FailoverUpdatesImpl(connection)) {}
FailoverUpdates::~FailoverUpdates() { if (impl) { impl->wait(); delete impl; } }
FailoverUpdates::FailoverUpdates(const FailoverUpdates&) : impl(0) {}
FailoverUpdates& FailoverUpdates::operator=(const FailoverUpdates&) { return *this; }


}}} // namespace qpid::client::amqp0_10
