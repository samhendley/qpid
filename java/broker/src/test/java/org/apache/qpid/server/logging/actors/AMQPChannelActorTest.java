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
package org.apache.qpid.server.logging.actors;

import junit.framework.TestCase;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.MockProtocolSession;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.logging.actors.AMQPConnectionActor;
import org.apache.qpid.server.logging.rawloggers.UnitTestMessageLogger;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.RootMessageLoggerImpl;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.AMQChannel;

import java.util.List;

/**
 * Test : AMQPConnectionActorTest
 * Validate the AMQPConnectionActor class.
 *
 * The test creates a new AMQPActor and then logs a message using it.
 *
 * The test then verifies that the logged message was the only one created and
 * that the message contains the required message.
 */
public class AMQPChannelActorTest extends TestCase
{

    LogActor _amqpActor;
    UnitTestMessageLogger _rawLogger;

    public void setUp() throws ConfigurationException, AMQException
    {
        Configuration config = new PropertiesConfiguration();
        ServerConfiguration serverConfig = new ServerConfiguration(config);

        _rawLogger = new UnitTestMessageLogger();
        RootMessageLogger rootLogger =
                new RootMessageLoggerImpl(serverConfig, _rawLogger);

        // Create a single session for this test.
        // Re-use is ok as we are testing the LogActor object is set correctly,
        // not the value of the output.
        AMQProtocolSession session = new MockProtocolSession(new MemoryMessageStore());
        // Use the first Virtualhost that has been defined to initialise
        // the MockProtocolSession. This prevents a NPE when the
        // AMQPActor attempts to lookup the name of the VHost.
        try
        {
            session.setVirtualHost(ApplicationRegistry.getInstance().
                    getVirtualHostRegistry().getVirtualHosts().
                    toArray(new VirtualHost[1])[0]);
        }
        catch (AMQException e)
        {
            fail("Unable to set virtualhost on session:" + e.getMessage());
        }


        AMQChannel channel = new AMQChannel(session, 1, session.getVirtualHost().getMessageStore());

        _amqpActor = new AMQPChannelActor(channel, rootLogger);

    }

    public void tearDown()
    {
        _rawLogger.clearLogMessages();
    }

    /**
     * Test that when logging on behalf of the channel
     * The test sends a message then verifies that it entered the logs.
     *
     * The log message should be fully repalaced (no '{n}' values) and should
     * contain the channel id ('/ch:1') identification.
     */
    public void testChannel()
    {
        final String message = "test logging";

        _amqpActor.message(new LogSubject()
        {
            public String toString()
            {
                return "[AMQPActorTest]";
            }

        }, new LogMessage()
        {
            public String toString()
            {
                return message;
            }
        });

        List<Object> logs = _rawLogger.getLogMessages();

        assertEquals("Message log size not as expected.", 1, logs.size());

        // Verify that the logged message is present in the output
        assertTrue("Message was not found in log message:" + logs.get(0),
                   logs.get(0).toString().contains(message));

        // Verify that the message has the correct type
        assertTrue("Message contains the [con: prefix",
                   logs.get(0).toString().contains("[con:"));
        

        // Verify that all the values were presented to the MessageFormatter
        // so we will not end up with '{n}' entries in the log.
        assertFalse("Verify that the string does not contain any '{'." + logs.get(0),
                    logs.get(0).toString().contains("{"));

        // Verify that the logged message contains the 'ch:1' marker
        assertTrue("Message was not logged as part of channel 1" + logs.get(0),
                   logs.get(0).toString().contains("/ch:1"));

    }

    public void testChannelLoggingOff() throws ConfigurationException, AMQException
    {
        Configuration config = new PropertiesConfiguration();
        config.addProperty("status-updates", "OFF");

        ServerConfiguration serverConfig = new ServerConfiguration(config);

        _rawLogger = new UnitTestMessageLogger();
        RootMessageLogger rootLogger =
                new RootMessageLoggerImpl(serverConfig, _rawLogger);

        // Create a single session for this test.
        // Re-use is ok as we are testing the LogActor object is set correctly,
        // not the value of the output.
        AMQProtocolSession session = new MockProtocolSession(new MemoryMessageStore());
        // Use the first Virtualhost that has been defined to initialise
        // the MockProtocolSession. This prevents a NPE when the
        // AMQPActor attempts to lookup the name of the VHost.
        try
        {
            session.setVirtualHost(ApplicationRegistry.getInstance().
                    getVirtualHostRegistry().getVirtualHosts().
                    toArray(new VirtualHost[1])[0]);
        }
        catch (AMQException e)
        {
            fail("Unable to set virtualhost on session:" + e.getMessage());
        }


        AMQChannel channel = new AMQChannel(session, 1, session.getVirtualHost().getMessageStore());

        _amqpActor = new AMQPChannelActor(channel, rootLogger);

        final String message = "test logging";

        _amqpActor.message(new LogSubject()
        {
            public String toString()
            {
                return "[AMQPActorTest]";
            }

        }, new LogMessage()
        {
            public String toString()
            {
                return message;
            }
        });

        List<Object> logs = _rawLogger.getLogMessages();

        assertEquals("Message log size not as expected.", 0, logs.size());

    }

}