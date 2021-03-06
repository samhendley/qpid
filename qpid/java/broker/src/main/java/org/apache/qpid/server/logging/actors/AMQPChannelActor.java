/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.logging.actors;

import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.subjects.ChannelLogSubject;
import org.apache.qpid.server.protocol.AMQProtocolSession;

import java.text.MessageFormat;

/**
 * An AMQPChannelActor represtents a connection through the AMQP port with an
 * associated Channel.
 *
 * <p/>
 * This is responsible for correctly formatting the LogActor String in the log
 * <p/>
 * [con:1(user@127.0.0.1/)/ch:1]
 * <p/>
 * To do this it requires access to the IO Layers as well as a Channel
 */
public class AMQPChannelActor extends AbstractActor
{
    private final ChannelLogSubject _logString;

    /**
     * Create a new ChannelActor
     *
     * @param channel    The Channel for this LogActor
     * @param rootLogger The root Logger that this LogActor should use
     */
    public AMQPChannelActor(AMQChannel channel, RootMessageLogger rootLogger)
    {
        super(rootLogger);


        _logString = new ChannelLogSubject(channel);
    }

    public String getLogMessage()
    {
        return _logString.toString();
    }
}

