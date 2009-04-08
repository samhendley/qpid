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
package org.apache.qpid.server.store;

import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.server.transactionlog.BaseTransactionLog;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.AMQException;

import java.util.Map;
import java.util.List;

public interface TestTransactionLog extends TransactionLog
{
    public void setBaseTransactionLog(BaseTransactionLog base);

    public List<AMQQueue> getMessageReferenceMap(Long messageID);
    public MessageMetaData getMessageMetaData(StoreContext context, Long messageId) throws AMQException;
    public ContentChunk getContentBodyChunk(StoreContext context, Long messageId, int index) throws AMQException;
    public long getMessageMetaDataSize();
    public TransactionLog getDelegate();
}