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

#include "qpid/client/Connector.h"

#include "qpid/Exception.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/SecurityLayer.h"

#include <map>

namespace qpid {
namespace client {

using namespace qpid::sys;
using namespace qpid::framing;

namespace {
    typedef std::map<std::string, Connector::Factory*> ProtocolRegistry;

    ProtocolRegistry& theProtocolRegistry() {
        static ProtocolRegistry protocolRegistry;

        return protocolRegistry;
    } 
}

Connector* Connector::create(const std::string& proto,
                             boost::shared_ptr<Poller> p,
                             framing::ProtocolVersion v, const ConnectionSettings& s, ConnectionImpl* c)
{
    ProtocolRegistry::const_iterator i = theProtocolRegistry().find(proto);
    if (i==theProtocolRegistry().end()) {
        throw Exception(QPID_MSG("Unknown protocol: " << proto));
    }
    return (i->second)(p, v, s, c);
}

void Connector::registerFactory(const std::string& proto, Factory* connectorFactory)
{
    ProtocolRegistry::const_iterator i = theProtocolRegistry().find(proto);
    if (i!=theProtocolRegistry().end()) {
        QPID_LOG(error, "Tried to register protocol: " << proto << " more than once");
    }
    theProtocolRegistry()[proto] = connectorFactory;
}

void Connector::activateSecurityLayer(std::auto_ptr<qpid::sys::SecurityLayer>)
{
}


}} // namespace qpid::client
