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

#include "ObjectId.h"
#include "qpid/framing/Buffer.h"

using namespace qpid::console;
using namespace qpid;
using namespace std;

ObjectId::ObjectId(framing::Buffer& buffer)
{
    decode(buffer);
}

void ObjectId::decode(framing::Buffer& buffer)
{
    first = buffer.getLongLong();
    second = buffer.getLongLong();
}

void ObjectId::encode(framing::Buffer& buffer)
{
    buffer.putLongLong(first);
    buffer.putLongLong(second);
}

bool ObjectId::operator==(const ObjectId& other) const
{
    return second == other.second && first == other.first;
}

bool ObjectId::operator!=(const ObjectId& other) const
{
    return !(*this == other);
}

bool ObjectId::operator<(const ObjectId& other) const
{
    if (first < other.first)
        return true;
    if (first > other.first)
        return false;
    return second < other.second;
}

bool ObjectId::operator>(const ObjectId& other) const
{
    if (first > other.first)
        return true;
    if (first < other.first)
        return false;
    return second > other.second;
}

bool ObjectId::operator<=(const ObjectId& other) const
{
    return !(*this > other);
}

bool ObjectId::operator>=(const ObjectId& other) const
{
    return !(*this < other);
}

ostream& qpid::console::operator<<(ostream& o, const ObjectId& id)
{
    o << (int) id.getFlags() << "-" << id.getSequence() << "-" << id.getBrokerBank() << "-" <<
        id.getAgentBank() << "-" << id.getObject();
    return o;
}

