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

using System;
using System.Configuration;
using System.IO;
using System.Text;
using System.Threading;
using org.apache.qpid.client;
using org.apache.qpid.transport;

namespace org.apache.qpid.example.direct
{
    /// <summary>
    /// This program is one of three programs designed to be used
    /// together. These programs use the "amq.direct" exchange.
    /// 
    /// Producer:
    /// 
    /// Publishes to a broker, specifying a routing key.
    /// 
    /// Listener (this program):
    /// 
    /// Reads from a queue on the broker using a message listener.
    /// 
    /// </summary>
    public class Listener
    {        
        private static void Main(string[] args)
        {
            string host = ConfigurationManager.AppSettings["Host"];
            int port = int.Parse(ConfigurationManager.AppSettings["Port"]);
            string virtualhost = ConfigurationManager.AppSettings["VirtualHost"];
            string username = ConfigurationManager.AppSettings["Username"];
            string password = ConfigurationManager.AppSettings["Password"];

            Client connection = new Client();
            try
            {
                connection.Connect(host, port, virtualhost, username, password);
                IClientSession session = connection.CreateSession(50000);

                //--------- Main body of program --------------------------------------------
                // Create a queue named "message_queue", and route all messages whose
                // routing key is "routing_key" to this newly created queue.

                session.QueueDeclare("message_queue");
                session.ExchangeBind("message_queue", "amq.direct", "routing_key");
         
                lock (session)
                {
                    // Create a listener and subscribe it to the queue named "message_queue"
                    IMessageListener listener = new MessageListener(session);
                    session.AttachMessageListener(listener, "message_queue");                              
                    session.MessageSubscribe("message_queue");
                    // Receive messages until all messages are received
                    Monitor.Wait(session);
                }

                //---------------------------------------------------------------------------

                connection.Close();
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: \n" + e.StackTrace);
            }
        }
    }

    public class MessageListener : IMessageListener
    {
        private readonly IClientSession _session;
        private readonly RangeSet _range = new RangeSet();
        public MessageListener(IClientSession session)
        {            
            _session = session;
        }

        public void MessageTransfer(IMessage m)
        {
            BinaryReader reader = new BinaryReader(m.Body, Encoding.UTF8);
            byte[] body = new byte[m.Body.Length - m.Body.Position];
            reader.Read(body, 0, body.Length);
            ASCIIEncoding enc = new ASCIIEncoding();
            string message = enc.GetString(body);
            Console.WriteLine("Message: " + message);
            // Add this message to the list of message to be acknowledged 
            _range.Add(m.Id);       
            if( message.Equals("That's all, folks!") )
            {
                // Acknowledge all the received messages 
                _session.MessageAccept(_range);     
                lock(_session)
                {
                    Monitor.Pulse(_session);
                }
            }
        }
    }
}
