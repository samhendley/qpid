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

#include "ObjectImpl.h"
#include "ValueImpl.h"
#include <qpid/sys/Time.h>

using namespace std;
using namespace qmf;
using namespace qpid::sys;
using qpid::framing::Buffer;

ObjectImpl::ObjectImpl(Object* e, const SchemaObjectClass* type) :
    envelope(e), objectClass(type), createTime(uint64_t(Duration(now()))),
    destroyTime(0), lastUpdatedTime(createTime)
{
    int propCount = objectClass->getPropertyCount();
    int statCount = objectClass->getStatisticCount();
    int idx;

    for (idx = 0; idx < propCount; idx++) {
        const SchemaProperty* prop = objectClass->getProperty(idx);
        properties[prop->getName()] = ValuePtr(new Value(prop->getType()));
    }

    for (idx = 0; idx < statCount; idx++) {
        const SchemaStatistic* stat = objectClass->getStatistic(idx);
        statistics[stat->getName()] = ValuePtr(new Value(stat->getType()));
    }
}

ObjectImpl::ObjectImpl(const SchemaObjectClass* type, Buffer& buffer) :
    envelope(new Object(this)), objectClass(type), createTime(uint64_t(Duration(now()))),
    destroyTime(0), lastUpdatedTime(createTime)
{
    int propCount = objectClass->getPropertyCount();
    int statCount = objectClass->getStatisticCount();
    int idx;
    set<string> excludes;

    parsePresenceMasks(buffer, excludes);
    for (idx = 0; idx < propCount; idx++) {
        const SchemaProperty* prop = objectClass->getProperty(idx);
        if (excludes.count(prop->getName()) != 0) {
            properties[prop->getName()] = ValuePtr(new Value(prop->getType()));
        } else {
            ValueImpl* pval = new ValueImpl(prop->getType(), buffer);
            properties[prop->getName()] = ValuePtr(pval->envelope);
        }
    }

    for (idx = 0; idx < statCount; idx++) {
        const SchemaStatistic* stat = objectClass->getStatistic(idx);
        ValueImpl* sval = new ValueImpl(stat->getType(), buffer);
        statistics[stat->getName()] = ValuePtr(sval->envelope);
    }
}

ObjectImpl::~ObjectImpl()
{
}

void ObjectImpl::destroy()
{
    destroyTime = uint64_t(Duration(now()));
    // TODO - flag deletion
}

Value* ObjectImpl::getValue(const string& key)
{
    map<string, ValuePtr>::const_iterator iter;

    iter = properties.find(key);
    if (iter != properties.end())
        return iter->second.get();

    iter = statistics.find(key);
    if (iter != statistics.end())
        return iter->second.get();

    return 0;
}

void ObjectImpl::parsePresenceMasks(Buffer& buffer, set<string>& excludeList)
{
    int propCount = objectClass->getPropertyCount();
    excludeList.clear();
    uint8_t bit = 0;
    uint8_t mask = 0;

    for (int idx = 0; idx < propCount; idx++) {
        const SchemaProperty* prop = objectClass->getProperty(idx);
        if (prop->isOptional()) {
            if (bit == 0) {
                mask = buffer.getOctet();
                bit = 1;
            }
            if ((mask & bit) == 0)
                excludeList.insert(string(prop->getName()));
            if (bit == 0x80)
                bit = 0;
            else
                bit = bit << 1;
        }
    }
}

void ObjectImpl::encodeSchemaKey(qpid::framing::Buffer& buffer) const
{
    buffer.putShortString(objectClass->getPackage());
    buffer.putShortString(objectClass->getName());
    buffer.putBin128(const_cast<uint8_t*>(objectClass->getHash()));
}

void ObjectImpl::encodeManagedObjectData(qpid::framing::Buffer& buffer) const
{
    buffer.putLongLong(lastUpdatedTime);
    buffer.putLongLong(createTime);
    buffer.putLongLong(destroyTime);
    objectId->impl->encode(buffer);
}

void ObjectImpl::encodeProperties(qpid::framing::Buffer& buffer) const
{
    int propCount = objectClass->getPropertyCount();
    uint8_t bit = 0;
    uint8_t mask = 0;
    ValuePtr value;

    for (int idx = 0; idx < propCount; idx++) {
        const SchemaProperty* prop = objectClass->getProperty(idx);
        if (prop->isOptional()) {
            value = properties[prop->getName()];
            if (bit == 0)
                bit = 1;
            if (!value->isNull())
                mask |= bit;
            if (bit == 0x80) {
                buffer.putOctet(mask);
                bit = 0;
                mask = 0;
            } else
                bit = bit << 1;
        }
    }
    if (bit != 0) {
        buffer.putOctet(mask);
    }

    for (int idx = 0; idx < propCount; idx++) {
        const SchemaProperty* prop = objectClass->getProperty(idx);
        value = properties[prop->getName()];
        if (!prop->isOptional() || !value->isNull()) {
            value->impl->encode(buffer);
        }
    }
}

void ObjectImpl::encodeStatistics(qpid::framing::Buffer& buffer) const
{
    int statCount = objectClass->getStatisticCount();
    for (int idx = 0; idx < statCount; idx++) {
        const SchemaStatistic* stat = objectClass->getStatistic(idx);
        ValuePtr value = statistics[stat->getName()];
        value->impl->encode(buffer);
    }
}

//==================================================================
// Wrappers
//==================================================================

Object::Object(const SchemaObjectClass* type) : impl(new ObjectImpl(this, type)) {}

Object::Object(ObjectImpl* i) : impl(i) {}

Object::~Object()
{
    delete impl;
}

void Object::destroy()
{
    impl->destroy();
}

const ObjectId* Object::getObjectId() const
{
    return impl->getObjectId();
}

void Object::setObjectId(ObjectId* oid)
{
    impl->setObjectId(oid);
}

const SchemaObjectClass* Object::getClass() const
{
    return impl->getClass();
}

Value* Object::getValue(char* key)
{
    return impl->getValue(key);
}
