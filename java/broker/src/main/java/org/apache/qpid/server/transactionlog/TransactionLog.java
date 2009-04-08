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
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.store.StoreContext;

import java.util.ArrayList;

/**
 * TransactionLog defines the interface for performing transactions.
 * This is used to preserve the state of messages, queues
 * and exchanges in a transactional manner.
 *
 * <p/>All message store, remove, enqueue and dequeue operations are carried out against a {@link StoreContext} which
 * encapsulates the transactional context they are performed in. Many such operations can be carried out in a single
 * transaction.
 *
 * <p/>The storage and removal of queues and exchanges, are not carried out in a transactional context.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities
 * <tr><td> Accept transaction boundary demarcations: Begin, Commit, Abort.
 * <tr><td> Store and remove queues.
 * <tr><td> Store and remove exchanges.
 * <tr><td> Store and remove messages.
 * <tr><td> Bind and unbind queues to exchanges.
 * <tr><td> Enqueue and dequeue messages to queues.
 * <tr><td> Generate message identifiers.
 * </table>
 */
public interface TransactionLog
{
    /**
     * Called after instantiation in order to configure the message store. A particular implementation can define
     * whatever parameters it wants.
     *
     * @param virtualHost The virtual host using by this store
     * @param base        The base element identifier from which all configuration items are relative. For example, if
     *                    the base element is "store", the all elements used by concrete classes will be "store.foo" etc.
     * @param config      The apache commons configuration object.
     *
     * @throws Exception If any error occurs that means the store is unable to configure itself.
     */
    Object configure(VirtualHost virtualHost, String base, VirtualHostConfiguration config) throws Exception;

    /**
     * Called to close and cleanup any resources used by the message store.
     *
     * @throws Exception If the close fails.
     */
    void close() throws Exception;

    /**
     * Places a message onto a specified queue, in a given transactional context.
     *
     * @param context   The transactional context for the operation.
     * @param queues
     *@param messageId The message to enqueue.  @throws AMQException If the operation fails for any reason.  @throws org.apache.qpid.AMQException
     */
    void enqueueMessage(StoreContext context, final ArrayList<AMQQueue> queues, Long messageId) throws AMQException;

    /**
     * Extracts a message from a specified queue, in a given transactional context.
     *
     * @param context   The transactional context for the operation.
     * @param queue
     * @param messageId The message to dequeue.  @throws AMQException If the operation fails for any reason, or if the specified message does not exist.
     */
    void dequeueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException;

    /**
     * Remove the specified message from the log
     *
     * @param context The transactional context for the operation
     * @param messageId The message to remove
     * @throws AMQException
     */
    void removeMessage(StoreContext context, Long messageId) throws AMQException;

    /**
     * Begins a transactional context.
     *
     * @param context The transactional context to begin.
     * @throws AMQException If the operation fails for any reason.
     */
    void beginTran(StoreContext context) throws AMQException;

    /**
     * Commits all operations performed within a given transactional context.
     *
     * @param context The transactional context to commit all operations for.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void commitTran(StoreContext context) throws AMQException;

    /**
     * Abandons all operations performed within a given transactional context.
     *
     * @param context The transactional context to abandon.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void abortTran(StoreContext context) throws AMQException;

    /**
     * Tests a transactional context to see if it has been begun but not yet committed or aborted.
     *
     * @param context The transactional context to test.
     *
     * @return <tt>true</tt> if the transactional context is live, <tt>false</tt> otherwise.
     */
    boolean inTran(StoreContext context);


    /**
     * Stores a chunk of message data.
     *
     * @param context         The transactional context for the operation.
     * @param messageId       The message to store the data for.
     * @param index           The index of the data chunk.
     * @param contentBody     The content of the data chunk.
     * @param lastContentBody Flag to indicate that this is the last such chunk for the message.
     *
     * @throws AMQException If the operation fails for any reason, or if the specified message does not exist.
     */
    void storeContentBodyChunk(StoreContext context, Long messageId, int index, ContentChunk contentBody,
        boolean lastContentBody) throws AMQException;

    /**
     * Stores message meta-data.
     *
     * @param context         The transactional context for the operation.
     * @param messageId       The message to store the data for.
     * @param messageMetaData The message meta data to store.
     *
     * @throws AMQException If the operation fails for any reason, or if the specified message does not exist.
     */
    void storeMessageMetaData(StoreContext context, Long messageId, MessageMetaData messageMetaData) throws AMQException;
//
//    /**
//     * Retrieves message meta-data.
//     *
//     * @param context   The transactional context for the operation.
//     * @param messageId The message to get the meta-data for.
//     *
//     * @return The message meta data.
//     *
//     * @throws AMQException If the operation fails for any reason, or if the specified message does not exist.
//     */
//    MessageMetaData getMessageMetaData(StoreContext context, Long messageId) throws AMQException;
//
//    /**
//     * Retrieves a chunk of message data.
//     *
//     * @param context   The transactional context for the operation.
//     * @param messageId The message to get the data chunk for.
//     * @param index     The offset index of the data chunk within the message.
//     *
//     * @return A chunk of message data.
//     *
//     * @throws AMQException If the operation fails for any reason, or if the specified message does not exist.
//     */
//    ContentChunk getContentBodyChunk(StoreContext context, Long messageId, int index) throws AMQException;

    /**
     * Is this store capable of persisting the data
     *
     * @return true if this store is capable of persisting data
     */
    boolean isPersistent();

}