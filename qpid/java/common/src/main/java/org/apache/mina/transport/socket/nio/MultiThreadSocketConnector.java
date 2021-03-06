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
 */
package org.apache.mina.transport.socket.nio;

import edu.emory.mathcs.backport.java.util.concurrent.Executor;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.ExceptionMonitor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoConnectorConfig;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.common.support.AbstractIoFilterChain;
import org.apache.mina.common.support.DefaultConnectFuture;
import org.apache.mina.util.NamePreservingRunnable;
import org.apache.mina.util.NewThreadExecutor;
import org.apache.mina.util.Queue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * {@link IoConnector} for socket transport (TCP/IP).
 *
 * @author The Apache Directory Project (mina-dev@directory.apache.org)
 * @version $Rev: 619823 $, $Date: 2008-02-08 10:09:37 +0000 (Fri, 08 Feb 2008) $
 */
public class MultiThreadSocketConnector extends SocketConnector
{
    /** @noinspection StaticNonFinalField */
    private static volatile int nextId = 0;

    private final Object lock = new Object();
    private final int id = nextId++;
    private final String threadName = "SocketConnector-" + id;
    private SocketConnectorConfig defaultConfig = new SocketConnectorConfig();
    private final Queue connectQueue = new Queue();
    private final MultiThreadSocketIoProcessor[] ioProcessors;
    private final int processorCount;
    private final Executor executor;

    /** @noinspection FieldAccessedSynchronizedAndUnsynchronized */
    private Selector selector;
    private Worker worker;
    private int processorDistributor = 0;
    private int workerTimeout = 60;  // 1 min.

    /** Create a connector with a single processing thread using a NewThreadExecutor */
    public MultiThreadSocketConnector()
    {
        this(1, new NewThreadExecutor());
    }

    /**
     * Create a connector with the desired number of processing threads
     *
     * @param processorCount Number of processing threads
     * @param executor       Executor to use for launching threads
     */
    public MultiThreadSocketConnector(int processorCount, Executor executor)
    {
        if (processorCount < 1)
        {
            throw new IllegalArgumentException("Must have at least one processor");
        }

        this.executor = executor;
        this.processorCount = processorCount;
        ioProcessors = new MultiThreadSocketIoProcessor[processorCount];

        for (int i = 0; i < processorCount; i++)
        {
            ioProcessors[i] = new MultiThreadSocketIoProcessor("SocketConnectorIoProcessor-" + id + "." + i, executor);
        }
    }

    /**
     * How many seconds to keep the connection thread alive between connection requests
     *
     * @return Number of seconds to keep connection thread alive
     */
    public int getWorkerTimeout()
    {
        return workerTimeout;
    }

    /**
     * Set how many seconds the connection worker thread should remain alive once idle before terminating itself.
     *
     * @param workerTimeout Number of seconds to keep thread alive. Must be >=0
     */
    public void setWorkerTimeout(int workerTimeout)
    {
        if (workerTimeout < 0)
        {
            throw new IllegalArgumentException("Must be >= 0");
        }
        this.workerTimeout = workerTimeout;
    }

    public ConnectFuture connect(SocketAddress address, IoHandler handler, IoServiceConfig config)
    {
        return connect(address, null, handler, config);
    }

    public ConnectFuture connect(SocketAddress address, SocketAddress localAddress,
                                 IoHandler handler, IoServiceConfig config)
    {
        if (address == null)
        {
            throw new NullPointerException("address");
        }
        if (handler == null)
        {
            throw new NullPointerException("handler");
        }

        if (!(address instanceof InetSocketAddress))
        {
            throw new IllegalArgumentException("Unexpected address type: "
                                               + address.getClass());
        }

        if (localAddress != null && !(localAddress instanceof InetSocketAddress))
        {
            throw new IllegalArgumentException("Unexpected local address type: "
                                               + localAddress.getClass());
        }

        if (config == null)
        {
            config = getDefaultConfig();
        }

        SocketChannel ch = null;
        boolean success = false;
        try
        {
            ch = SocketChannel.open();
            ch.socket().setReuseAddress(true);
            if (localAddress != null)
            {
                ch.socket().bind(localAddress);
            }

            ch.configureBlocking(false);

            if (ch.connect(address))
            {
                DefaultConnectFuture future = new DefaultConnectFuture();
                newSession(ch, handler, config, future);
                success = true;
                return future;
            }

            success = true;
        }
        catch (IOException e)
        {
            return DefaultConnectFuture.newFailedFuture(e);
        }
        finally
        {
            if (!success && ch != null)
            {
                try
                {
                    ch.close();
                }
                catch (IOException e)
                {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
            }
        }

        ConnectionRequest request = new ConnectionRequest(ch, handler, config);
        synchronized (lock)
        {
            try
            {
                startupWorker();
            }
            catch (IOException e)
            {
                try
                {
                    ch.close();
                }
                catch (IOException e2)
                {
                    ExceptionMonitor.getInstance().exceptionCaught(e2);
                }

                return DefaultConnectFuture.newFailedFuture(e);
            }
        }

        synchronized (connectQueue)
        {
            connectQueue.push(request);
        }
        selector.wakeup();

        return request;
    }

    private synchronized void startupWorker() throws IOException
    {
        if (worker == null)
        {
            selector = Selector.open();
            worker = new Worker();
            executor.execute(new NamePreservingRunnable(worker));
        }
    }

    private void registerNew()
    {
        if (connectQueue.isEmpty())
        {
            return;
        }

        for (; ;)
        {
            ConnectionRequest req;
            synchronized (connectQueue)
            {
                req = (ConnectionRequest) connectQueue.pop();
            }

            if (req == null)
            {
                break;
            }

            SocketChannel ch = req.channel;
            try
            {
                ch.register(selector, SelectionKey.OP_CONNECT, req);
            }
            catch (IOException e)
            {
                req.setException(e);
            }
        }
    }

    private void processSessions(Set keys)
    {
        Iterator it = keys.iterator();

        while (it.hasNext())
        {
            SelectionKey key = (SelectionKey) it.next();

            if (!key.isConnectable())
            {
                continue;
            }

            SocketChannel ch = (SocketChannel) key.channel();
            ConnectionRequest entry = (ConnectionRequest) key.attachment();

            boolean success = false;
            try
            {
                ch.finishConnect();
                newSession(ch, entry.handler, entry.config, entry);
                success = true;
            }
            catch (Throwable e)
            {
                entry.setException(e);
            }
            finally
            {
                key.cancel();
                if (!success)
                {
                    try
                    {
                        ch.close();
                    }
                    catch (IOException e)
                    {
                        ExceptionMonitor.getInstance().exceptionCaught(e);
                    }
                }
            }
        }

        keys.clear();
    }

    private void processTimedOutSessions(Set keys)
    {
        long currentTime = System.currentTimeMillis();
        Iterator it = keys.iterator();

        while (it.hasNext())
        {
            SelectionKey key = (SelectionKey) it.next();

            if (!key.isValid())
            {
                continue;
            }

            ConnectionRequest entry = (ConnectionRequest) key.attachment();

            if (currentTime >= entry.deadline)
            {
                entry.setException(new ConnectException());
                try
                {
                    key.channel().close();
                }
                catch (IOException e)
                {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
                finally
                {
                    key.cancel();
                }
            }
        }
    }

    private void newSession(SocketChannel ch, IoHandler handler, IoServiceConfig config, ConnectFuture connectFuture)
            throws IOException
    {
        MultiThreadSocketSessionImpl session =
                new MultiThreadSocketSessionImpl(this, nextProcessor(), getListeners(),
                                                 config, ch, handler, ch.socket().getRemoteSocketAddress());

        //new interface
//        SocketSessionImpl session = new SocketSessionImpl(
//                this, nextProcessor(), getListeners(),
//                config, ch, handler, ch.socket().getRemoteSocketAddress() );
        try
        {
            getFilterChainBuilder().buildFilterChain(session.getFilterChain());
            config.getFilterChainBuilder().buildFilterChain(session.getFilterChain());
            config.getThreadModel().buildFilterChain(session.getFilterChain());
        }
        catch (Throwable e)
        {
            throw (IOException) new IOException("Failed to create a session.").initCause(e);
        }

        // Set the ConnectFuture of the specified session, which will be
        // removed and notified by AbstractIoFilterChain eventually.
        session.setAttribute( AbstractIoFilterChain.CONNECT_FUTURE, connectFuture );

        // Forward the remaining process to the SocketIoProcessor.
        session.getIoProcessor().addNew(session);
    }

    private MultiThreadSocketIoProcessor nextProcessor()
    {
        return ioProcessors[processorDistributor++ % processorCount];
    }

    private class Worker implements Runnable
    {
        private long lastActive = System.currentTimeMillis();

        public void run()
        {
            Thread.currentThread().setName(MultiThreadSocketConnector.this.threadName);

            for (; ;)
            {
                try
                {
                    int nKeys = selector.select(1000);

                    registerNew();

                    if (nKeys > 0)
                    {
                        processSessions(selector.selectedKeys());
                    }

                    processTimedOutSessions(selector.keys());

                    if (selector.keys().isEmpty())
                    {
                        if (System.currentTimeMillis() - lastActive > workerTimeout * 1000L)
                        {
                            synchronized (lock)
                            {
                                if (selector.keys().isEmpty() &&
                                    connectQueue.isEmpty())
                                {
                                    worker = null;
                                    try
                                    {
                                        selector.close();
                                    }
                                    catch (IOException e)
                                    {
                                        ExceptionMonitor.getInstance().exceptionCaught(e);
                                    }
                                    finally
                                    {
                                        selector = null;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    else
                    {
                        lastActive = System.currentTimeMillis();
                    }
                }
                catch (IOException e)
                {
                    ExceptionMonitor.getInstance().exceptionCaught(e);

                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e1)
                    {
                        ExceptionMonitor.getInstance().exceptionCaught(e1);
                    }
                }
            }
        }
    }

    private class ConnectionRequest extends DefaultConnectFuture
    {
        private final SocketChannel channel;
        private final long deadline;
        private final IoHandler handler;
        private final IoServiceConfig config;

        private ConnectionRequest(SocketChannel channel, IoHandler handler, IoServiceConfig config)
        {
            this.channel = channel;
            long timeout;
            if (config instanceof IoConnectorConfig)
            {
                timeout = ((IoConnectorConfig) config).getConnectTimeoutMillis();
            }
            else
            {
                timeout = ((IoConnectorConfig) getDefaultConfig()).getConnectTimeoutMillis();
            }
            this.deadline = System.currentTimeMillis() + timeout;
            this.handler = handler;
            this.config = config;
        }
    }
}
