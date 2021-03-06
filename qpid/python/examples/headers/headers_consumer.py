#!/usr/bin/env python
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
"""
 headers_consumer.py

 This AMQP client reads messages from two message
 queues named "first" and "second".
"""

import qpid
import sys
import os
from random import randint
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message, RangedSet, uuid4
from qpid.queue import Empty


#----- Initialization --------------------------------------

#  Set parameters for login

host="127.0.0.1"
port=5672
user="guest"
password="guest"

# If an alternate host or port has been specified, use that instead
# (this is used in our unit tests)
if len(sys.argv) > 1 :
  host=sys.argv[1]
if len(sys.argv) > 2 :
  port=int(sys.argv[2])

#  Create a connection.
socket = connect(host, port)
connection = Connection (sock=socket, username=user, password=password)
connection.start()
session = connection.session(str(uuid4()))

#----- Read from queue --------------------------------------------

# Now let's create two local client queues and tell them to read
# incoming messages.

# The consumer tag identifies the client-side queue.

local_queue_name_first  = "local_queue_first"
local_queue_name_second = "local_queue_second"

queue_first  = session.incoming(local_queue_name_first)
queue_second = session.incoming(local_queue_name_second)

# Call message_subscribe() to tell the broker to deliver messages
# from the AMQP queue to these local client queues. The broker will
# start delivering messages as soon as credit is allocated using
# queue.start().

session.message_subscribe(queue="first",  destination=local_queue_name_first)
session.message_subscribe(queue="second", destination=local_queue_name_second)

queue_first.start()
queue_second.start()

#  Initialize 'final' and 'content', variables used to identify the last message.

final = "That's all, folks!"   # In a message body, signals the last message
content = ""		       # Content of the last message read

message = None
while content != final:
        message = queue_first.get(timeout=10)
        content = message.body          
        session.message_accept(RangedSet(message.id))
        print content

content = ""
while content != final:
        message = queue_second.get(timeout=10)
        content = message.body          
        session.message_accept(RangedSet(message.id))
        print content

#----- Cleanup ------------------------------------------------

# Clean up before exiting so there are no open threads.
#

session.close(timeout=10)
