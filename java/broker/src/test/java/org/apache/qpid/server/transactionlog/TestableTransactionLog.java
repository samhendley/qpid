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
package org.apache.qpid.server.transactionlog;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.TestTransactionLog;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestableTransactionLog extends BaseTransactionLog implements TestTransactionLog
{
    protected Map<Long, List<AMQQueue>> _singleEnqueuedIDstoQueue = new HashMap<Long, List<AMQQueue>>();

    public TestableTransactionLog()
    {
        super(null);
    }

    public TestableTransactionLog(TransactionLog delegate)
    {
        super(delegate);
        if (delegate instanceof BaseTransactionLog)
        {
            _delegate = ((BaseTransactionLog) delegate).getDelegate();
        }

    }

    /**
     * Override the BaseTranasactionLog to record the single enqueues of a message so we can perform references counting
     *
     * @param context   The transactional context for the operation.
     * @param queues
     * @param messageId The message to enqueue.  @throws AMQException If the operation fails for any reason.  @throws org.apache.qpid.AMQException
     *
     * @throws AMQException
     */
    @Override
    public void enqueueMessage(StoreContext context, ArrayList<AMQQueue> queues, Long messageId) throws AMQException
    {
        if (queues.size() == 1)
        {
            _singleEnqueuedIDstoQueue.put(messageId, queues);
        }

        super.enqueueMessage(context, queues, messageId);
    }

    /**
     * Override the BaseTranasactionLog to record the single enqueues of a message so we can perform references counting
     *
     * @param context   The transactional context for the operation.
     * @param queue
     * @param messageId The message to enqueue.  @throws AMQException If the operation fails for any reason.  @throws org.apache.qpid.AMQException
     *
     * @throws AMQException
     */
    @Override
    public void dequeueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException
    {
        if (_singleEnqueuedIDstoQueue.containsKey(messageId))
        {
            _singleEnqueuedIDstoQueue.remove(messageId);
        }

        super.dequeueMessage(context, queue, messageId);
    }

    @Override
    public Object configure(VirtualHost virtualHost, String base, VirtualHostConfiguration config) throws Exception
    {
        if (_delegate != null)
        {
            TransactionLog configuredLog = (TransactionLog) _delegate.configure(virtualHost, base, config);

            // Unwrap any BaseTransactionLog
            if (configuredLog instanceof BaseTransactionLog)
            {
                _delegate = ((BaseTransactionLog) configuredLog).getDelegate();
            }
        }
        else
        {
            String delegateClass = config.getStoreConfiguration().getString("delegate");
            Class clazz = Class.forName(delegateClass);
            Object o = clazz.newInstance();

            if (!(o instanceof TransactionLog))
            {
                throw new ClassCastException("TransactionLog class must implement " + TransactionLog.class + ". Class " + clazz +
                                             " does not.");
            }
            _delegate = (TransactionLog) o;

            // If a TransactionLog uses the BaseTransactionLog then it will return this object.
            _delegate.configure(virtualHost, base, config);
        }
        return this;
    }

    public void setBaseTransactionLog(BaseTransactionLog base)
    {
        throw new RuntimeException("TestableTransactionLog is unable to swap BaseTransactionLogs");
    }

    public List<AMQQueue> getMessageReferenceMap(Long messageID)
    {
        List<AMQQueue> result = _idToQueues.get(messageID);

        if (result == null)
        {
            result = _singleEnqueuedIDstoQueue.get(messageID);
        }

        return result;
    }

    public MessageMetaData getMessageMetaData(StoreContext context, Long messageId) throws AMQException
    {
        if (_delegate instanceof TestTransactionLog)
        {
            return ((TestTransactionLog) _delegate).getMessageMetaData(context, messageId);
        }
        else
        {
            return null;
        }
    }

    public ContentChunk getContentBodyChunk(StoreContext context, Long messageId, int index) throws AMQException
    {
        if (_delegate instanceof TestTransactionLog)
        {
            return ((TestTransactionLog) _delegate).getContentBodyChunk(context, messageId, index);
        }
        else
        {
            return null;
        }
    }

    public long getMessageMetaDataSize()
    {
        if (_delegate instanceof TestTransactionLog)
        {
            return ((TestTransactionLog) _delegate).getMessageMetaDataSize();
        }
        else
        {
            return 0;
        }
    }
}