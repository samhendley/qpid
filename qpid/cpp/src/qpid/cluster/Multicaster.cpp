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

#include "Multicaster.h"
#include "Cpg.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/LatencyMetric.h"
#include "qpid/framing/AMQBody.h"
#include "qpid/framing/AMQFrame.h"

namespace qpid {
namespace cluster {

Multicaster::Multicaster(Cpg& cpg_, 
                         const boost::shared_ptr<sys::Poller>& poller,
                         boost::function<void()> onError_) :
    onError(onError_), cpg(cpg_), 
    queue(boost::bind(&Multicaster::sendMcast, this, _1), poller),
    holding(true)
{
    queue.start();
}

void Multicaster::mcastControl(const framing::AMQBody& body, const ConnectionId& id) {
    QPID_LOG(trace, "MCAST " << id << ": " << body);
    mcast(Event::control(body, id));
}

void Multicaster::mcastControl(const framing::AMQFrame& frame, const ConnectionId& id) {
    QPID_LOG(trace, "MCAST " << id << ": " << frame);
    mcast(Event::control(frame, id));
}

void Multicaster::mcastBuffer(const char* data, size_t size, const ConnectionId& id) {
    Event e(DATA, id, size);
    memcpy(e.getData(), data, size);
    QPID_LOG(trace, "MCAST " << e);
    mcast(e);
}

void Multicaster::mcast(const Event& e) {
    {
        sys::Mutex::ScopedLock l(lock);
        if (e.getType() == DATA && e.isConnection() && holding) {
            holdingQueue.push_back(e); 
            return;
        }
    }
    QPID_LATENCY_INIT(e);
    queue.push(e);
}


void Multicaster::sendMcast(PollableEventQueue::Queue& values) {
    try {
        PollableEventQueue::Queue::iterator i = values.begin();
        while( i != values.end()) {
            QPID_LATENCY_RECORD("mcast send queue", *i);
            iovec iov = i->toIovec();
            if (!cpg.mcast(&iov, 1)) {
                // cpg didn't send because of CPG flow control.
                break; 
            }
            ++i;
        }
        values.erase(values.begin(), i); // Erase sent events.
    }
    catch (const std::exception& e) {
        QPID_LOG(critical, "Multicast error: " << e.what());
        queue.stop();
        onError();
    }
}

void Multicaster::release() {
    sys::Mutex::ScopedLock l(lock);
    holding = false;
    std::for_each(holdingQueue.begin(), holdingQueue.end(), boost::bind(&Multicaster::mcast, this, _1));
    holdingQueue.clear();
}

void Multicaster::selfDeliver(const Event& e) {
    sys::Mutex::ScopedLock l(lock);
    QPID_LATENCY_RECORD("cpg self deliver", e);
}

}} // namespace qpid::cluster