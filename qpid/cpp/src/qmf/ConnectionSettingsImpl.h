#ifndef _QmfConnectionSettingsImpl_
#define _QmfConnectionSettingsImpl_

/*
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
 */

#include "qmf/ConnectionSettings.h"
#include "qmf/Value.h"
#include "qpid/client/ConnectionSettings.h"
#include <string>
#include <map>

namespace qmf {

    class ConnectionSettingsImpl {
        ConnectionSettings* envelope;
        qpid::client::ConnectionSettings clientSettings;
        mutable std::string attrString;
        int retryDelayMin;
        int retryDelayMax;
        int retryDelayFactor;
        
    public:
        ConnectionSettingsImpl(ConnectionSettings* e);
        ConnectionSettingsImpl(ConnectionSettings* e, const std::string& url);
        ~ConnectionSettingsImpl() {}
        void setAttr(const std::string& key, const Value& value);
        Value getAttr(const std::string& key) const;
        const std::string& getAttrString() const;
        void transportTcp(uint16_t port);
        void transportSsl(uint16_t port);
        void transportRdma(uint16_t port);
        void authAnonymous(const std::string& username);
        void authPlain(const std::string& username, const std::string& password);
        void authGssapi(const std::string& serviceName, uint32_t minSsf, uint32_t maxSsf);
        void setRetry(int delayMin, int delayMax, int delayFactor);

        const qpid::client::ConnectionSettings& getClientSettings() const;
        void getRetrySettings(int* delayMin, int* delayMax, int* delayFactor) const;
    };

}

#endif