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
#ifndef _ConnectionFactory_
#define _ConnectionFactory_

#include "qpid/sys/ConnectionCodec.h"

namespace qpid {
namespace broker {
class Broker;

class ConnectionFactory : public sys::ConnectionCodec::Factory
{
  public:
    ConnectionFactory(Broker& b);
            
    virtual ~ConnectionFactory();

    sys::ConnectionCodec*
    create(framing::ProtocolVersion, sys::OutputControl&, const std::string& id,
           const qpid::sys::SecuritySettings&);

    sys::ConnectionCodec*
    create(sys::OutputControl&, const std::string& id, const qpid::sys::SecuritySettings&);

  private:
    Broker& broker;
};

}}


#endif
