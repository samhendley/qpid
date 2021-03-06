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
package org.apache.qpid.server.logging.subjects;

import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQQueue;

public class BindingLogSubject extends AbstractLogSubject
{

    /**
     * LOG FORMAT for the ChannelLogSubject,
     * Uses a MessageFormat call to insert the requried values according to
     * these indicies:
     *
     * 0 - Virtualhost Name
     * 1 - Exchange Type
     * 2 - Exchange Name
     * 3 - Queue Name
     * 4 - Binding RoutingKey
     */
    protected static String BINDING_FORMAT = "vh(/{0})/ex({1}/{2})/qu({3})/rk({4})";

    /**
     * Create a BindingLogSubject that Logs in the following format.
     *
     * [ vh(/)/ex(amq.direct)/qu(testQueue)/bd(testQueue) ]
     *
     * @param routingKey
     * @param exchange
     * @param queue
     */
    public BindingLogSubject(String routingKey, Exchange exchange,
                             AMQQueue queue)
    {
        setLogStringWithFormat(BINDING_FORMAT, queue.getVirtualHost().getName(),
                               exchange.getTypeShortString(),
                               exchange.getNameShortString(),
                               queue.getNameShortString(),
                               routingKey);
    }

}
