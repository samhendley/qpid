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

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.MessageFactory;
import org.apache.qpid.server.transactionlog.TestableTransactionLog;

/** Tests that reference counting works correctly with AMQMessage and the message store */
public class TestReferenceCounting extends TestCase
{
    private TestableTransactionLog _store;

    private StoreContext _storeContext = new StoreContext();

    protected void setUp() throws Exception
    {
        super.setUp();
        _store = new TestableTransactionLog(new TestableMemoryMessageStore().configure());
    }

    /** Check that when the reference count is decremented the message removes itself from the store */
    public void testMessageGetsRemoved() throws AMQException
    {
        ContentHeaderBody chb = createPersistentContentHeader();

        MessagePublishInfo info = new MessagePublishInfoImpl();

        AMQMessage message = (MessageFactory.getInstance()).createMessage(_store, true);
        message.setPublishAndContentHeaderBody(_storeContext, info, chb);

        assertNotNull("Message Metadata did not exist for new message",
                      _store.getMessageMetaData(new StoreContext(), message.getMessageId()));
    }

    private ContentHeaderBody createPersistentContentHeader()
    {
        ContentHeaderBody chb = new ContentHeaderBody();
        BasicContentHeaderProperties bchp = new BasicContentHeaderProperties();
        bchp.setDeliveryMode((byte) 2);
        chb.properties = bchp;
        return chb;
    }

    public void testMessageRemains() throws AMQException
    {

        MessagePublishInfo info = new MessagePublishInfoImpl();

        final ContentHeaderBody chb = createPersistentContentHeader();
        AMQMessage message = (MessageFactory.getInstance()).createMessage(_store, true);
        message.setPublishAndContentHeaderBody(_storeContext, info, chb);

        assertNotNull("Message Metadata did not exist for new message",
                      _store.getMessageMetaData(new StoreContext(), message.getMessageId()));
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TestReferenceCounting.class);
    }
}