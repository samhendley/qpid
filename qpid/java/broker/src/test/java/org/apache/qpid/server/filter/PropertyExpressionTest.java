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
package org.apache.qpid.server.filter;

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.MockQueueEntry;
import org.apache.qpid.server.registry.ApplicationRegistry;

public class PropertyExpressionTest extends TestCase
{

    public void tearDown() throws Exception
    {
        //Ensure we close the registry that the MockQueueEntry will create
        ApplicationRegistry.remove(1);
    }


    public void testJMSRedelivered()
    {
        PropertyExpression<AMQException> pe = new PropertyExpression<AMQException>("JMSRedelivered");

        MockQueueEntry queueEntry = new MockQueueEntry();

        try
        {
            assertEquals("MockQueueEntry.redelivered should initialy be false", Boolean.FALSE, pe.evaluate(queueEntry));
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }

        queueEntry.setRedelivered(true);

        try
        {
            assertEquals("MockQueueEntry.redelivered not updated", Boolean.TRUE, pe.evaluate(queueEntry));
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }
}