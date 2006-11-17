/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.store;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.queue.QueueRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple message store that stores the messages in a threadsafe structure in memory.
 */
public class MemoryMessageStore implements MessageStore
{
    private static final Logger _log = Logger.getLogger(MemoryMessageStore.class);

    private static final int DEFAULT_HASHTABLE_CAPACITY = 50000;

    private static final String HASHTABLE_CAPACITY_CONFIG = "hashtable-capacity";

    protected ConcurrentMap<Long, MessageMetaData> _metaDataMap;

    protected ConcurrentMap<Long, List<ContentBody>> _contentBodyMap;

    private final AtomicLong _messageId = new AtomicLong(1);

    public void configure()
    {
        _log.info("Using capacity " + DEFAULT_HASHTABLE_CAPACITY + " for hash tables");
        _metaDataMap = new ConcurrentHashMap<Long, MessageMetaData>(DEFAULT_HASHTABLE_CAPACITY);
        _contentBodyMap = new ConcurrentHashMap<Long, List<ContentBody>>(DEFAULT_HASHTABLE_CAPACITY);
    }

    public void configure(String base, Configuration config)
    {
        int hashtableCapacity = config.getInt(base + "." + HASHTABLE_CAPACITY_CONFIG, DEFAULT_HASHTABLE_CAPACITY);
        _log.info("Using capacity " + hashtableCapacity + " for hash tables");
        _metaDataMap = new ConcurrentHashMap<Long, MessageMetaData>(hashtableCapacity);
        _contentBodyMap = new ConcurrentHashMap<Long, List<ContentBody>>(hashtableCapacity);
    }

    public void configure(QueueRegistry queueRegistry, String base, Configuration config) throws Exception
    {
        configure(base, config);
    }

    public void close() throws Exception
    {
        if (_metaDataMap != null)
        {
            _metaDataMap.clear();
            _metaDataMap = null;
        }
        if (_contentBodyMap != null)
        {
            _contentBodyMap.clear();
            _contentBodyMap = null;
        }
    }

    public void removeMessage(long messageId)
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Removing message with id " + messageId);
        }
        _metaDataMap.remove(messageId);
        _contentBodyMap.remove(messageId);
    }

    public void createQueue(AMQQueue queue) throws AMQException
    {
        // Not required to do anything
    }

    public void removeQueue(String name) throws AMQException
    {
        // Not required to do anything
    }

    public void enqueueMessage(String name, long messageId) throws AMQException
    {
        // Not required to do anything
    }

    public void dequeueMessage(String name, long messageId) throws AMQException
    {
        // Not required to do anything
    }

    public void beginTran() throws AMQException
    {
        // Not required to do anything
    }

    public void commitTran() throws AMQException
    {
        // Not required to do anything
    }

    public void abortTran() throws AMQException
    {
        // Not required to do anything
    }

    public boolean inTran()
    {
        return false;
    }

    public List<AMQQueue> createQueues() throws AMQException
    {
        return null;
    }

    public long getNewMessageId()
    {
        return _messageId.getAndIncrement();
    }

    public void storeContentBodyChunk(long messageId, int index, ContentBody contentBody) throws AMQException
    {
        List<ContentBody> bodyList = _contentBodyMap.get(messageId);
        if (bodyList == null)
        {
            bodyList = new ArrayList<ContentBody>();
            _contentBodyMap.put(messageId, bodyList);
        }

        bodyList.add(index, contentBody);
    }

    public void storeMessageMetaData(long messageId, MessageMetaData messageMetaData) throws AMQException
    {
        _metaDataMap.put(messageId, messageMetaData);
    }

    public MessageMetaData getMessageMetaData(long messageId) throws AMQException
    {
        return _metaDataMap.get(messageId);
    }

    public ContentBody getContentBodyChunk(long messageId, int index) throws AMQException
    {
        List<ContentBody> bodyList = _contentBodyMap.get(messageId);        
        return bodyList.get(index);
    }
}
