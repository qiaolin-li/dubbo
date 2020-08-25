/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.transport;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.utils.ExecutorUtil;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Client;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.transport.dispatcher.ChannelHandlers;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_CLIENT_THREADPOOL;
import static org.apache.dubbo.common.constants.CommonConstants.THREADPOOL_KEY;

/**
 * AbstractClient
 * 客户端抽象实现
 *
 */
public abstract class AbstractClient extends AbstractEndpoint implements Client {

    /**
     *  客户端线程池名称
     */
    protected static final String CLIENT_THREAD_POOL_NAME = "DubboClientHandler";

    private static final Logger logger = LoggerFactory.getLogger(AbstractClient.class);

    /**
     *  开启、连接、关闭时加锁，防止并发问题
     */
    private final Lock connectLock = new ReentrantLock();

    /**
     * 发送消息时，但连接已经断开，是否需要重连
     */
    private final boolean needReconnect;

    protected volatile ExecutorService executor;

    /**
     *  线程池仓库类
     */
    private ExecutorRepository executorRepository = ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension();

    public AbstractClient(URL url, ChannelHandler handler) throws RemotingException {
        super(url, handler);

        // 是否需要重连
        needReconnect = url.getParameter(Constants.SEND_RECONNECT_KEY, false);

        // 初始化线程池
        initExecutor(url);

        try {
            // 打开客户端
            doOpen();
        } catch (Throwable t) {

            // 失败时关闭
            close();
            throw new RemotingException(url.toInetSocketAddress(), null,
                    "Failed to start " + getClass().getSimpleName() + " " + NetUtils.getLocalAddress()
                            + " connect to the server " + getRemoteAddress() + ", cause: " + t.getMessage(), t);
        }
        try {
            // 连接到服务端
            connect();
            if (logger.isInfoEnabled()) {
                logger.info("Start " + getClass().getSimpleName() + " " + NetUtils.getLocalAddress() + " connect to the server " + getRemoteAddress());
            }
        } catch (RemotingException t) {
            // 如果开启了检查，那么关闭连接
            if (url.getParameter(Constants.CHECK_KEY, true)) {
                close();
                throw t;
            } else {
                logger.warn("Failed to start " + getClass().getSimpleName() + " " + NetUtils.getLocalAddress()
                        + " connect to the server " + getRemoteAddress() + " (check == false, ignore and retry later!), cause: " + t.getMessage(), t);
            }
        } catch (Throwable t) {
            close();
            throw new RemotingException(url.toInetSocketAddress(), null,
                    "Failed to start " + getClass().getSimpleName() + " " + NetUtils.getLocalAddress()
                            + " connect to the server " + getRemoteAddress() + ", cause: " + t.getMessage(), t);
        }
    }

    /**
     *  初始化线程池
     * @param url
     */
    private void initExecutor(URL url) {

        // 设置线程名称
        url = ExecutorUtil.setThreadName(url, CLIENT_THREAD_POOL_NAME);

        // 如果线程池类型不存在，那么默认为cache
        url = url.addParameterIfAbsent(THREADPOOL_KEY, DEFAULT_CLIENT_THREADPOOL);

        // 如果线程池不存在，那么创建它
        executor = executorRepository.createExecutorIfAbsent(url);
    }


    protected static ChannelHandler wrapChannelHandler(URL url, ChannelHandler handler) {
        return ChannelHandlers.wrap(handler, url);
    }

    public InetSocketAddress getConnectAddress() {
        return new InetSocketAddress(NetUtils.filterLocalHost(getUrl().getHost()), getUrl().getPort());
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        // 获取连接通道
        Channel channel = getChannel();

        // 如果通道为空，有可能连接还未建立，取url的ip+port即可
        if (channel == null) {
            return getUrl().toInetSocketAddress();
        }

        // 否则取通道的地址
        return channel.getRemoteAddress();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        Channel channel = getChannel();
        if (channel == null) {
            return InetSocketAddress.createUnresolved(NetUtils.getLocalHost(), 0);
        }
        return channel.getLocalAddress();
    }

    @Override
    public boolean isConnected() {
        Channel channel = getChannel();
        if (channel == null) {
            return false;
        }
        return channel.isConnected();
    }

    @Override
    public Object getAttribute(String key) {
        Channel channel = getChannel();
        if (channel == null) {
            return null;
        }
        return channel.getAttribute(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        Channel channel = getChannel();
        if (channel == null) {
            return;
        }
        channel.setAttribute(key, value);
    }

    @Override
    public void removeAttribute(String key) {
        Channel channel = getChannel();
        if (channel == null) {
            return;
        }
        channel.removeAttribute(key);
    }

    @Override
    public boolean hasAttribute(String key) {
        Channel channel = getChannel();
        if (channel == null) {
            return false;
        }
        return channel.hasAttribute(key);
    }

    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        // 如果开启可以重连，并且通道不是连接状态，那么先去连接
        if (needReconnect && !isConnected()) {
            connect();
        }
        Channel channel = getChannel();
        //TODO Can the value returned by getChannel() be null? need improvement.
        if (channel == null || !channel.isConnected()) {
            throw new RemotingException(this, "message can not send, because channel is closed . url:" + getUrl());
        }
        channel.send(message, sent);
    }

    /**
     *  连接到服务端
     * @throws RemotingException
     */
    protected void connect() throws RemotingException {


        // 加锁，防止多次连接
        connectLock.lock();

        try {

            // 防止拿到锁时，其他线程已经做过连接工作了
            if (isConnected()) {
                return;
            }

            // 真正的连接方法
            doConnect();

            // 如果经过连接的方法还未连接，说明有问题，得抛异常
            if (!isConnected()) {
                throw new RemotingException(this, "Failed connect to server " + getRemoteAddress() + " from " + getClass().getSimpleName() + " "
                        + NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion()
                        + ", cause: Connect wait timeout: " + getConnectTimeout() + "ms.");

            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("Successed connect to server " + getRemoteAddress() + " from " + getClass().getSimpleName() + " "
                            + NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion()
                            + ", channel is " + this.getChannel());
                }
            }

        } catch (RemotingException e) {
            throw e;

        } catch (Throwable e) {
            throw new RemotingException(this, "Failed connect to server " + getRemoteAddress() + " from " + getClass().getSimpleName() + " "
                    + NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion()
                    + ", cause: " + e.getMessage(), e);

        } finally {
            // 释放锁
            connectLock.unlock();
        }
    }

    public void disconnect() {

        // 加锁，防止多个线程来断开连接
        connectLock.lock();
        try {
            try {
                Channel channel = getChannel();
                if (channel != null) {
                    channel.close();
                }
            } catch (Throwable e) {
                logger.warn(e.getMessage(), e);
            }
            try {
                doDisConnect();
            } catch (Throwable e) {
                logger.warn(e.getMessage(), e);
            }
        } finally {

            // 释放锁
            connectLock.unlock();
        }
    }

    @Override
    public void reconnect() throws RemotingException {
        // 如果连接已经断开，才会进行重连
        if (!isConnected()) {

            // 加锁，防止并发操作
            connectLock.lock();
            try {

                // 防止其他线程已经重连
                if (!isConnected()) {
                    // 先去断开连接，可能有些自定义逻辑哈
                    disconnect();

                    // 然后进行连接
                    connect();
                }
            } finally {

                // 释放锁
                connectLock.unlock();
            }
        }
    }

    @Override
    public void close() {

        try {
            // 调用父类关闭逻辑, 将close改为true
            super.close();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }

        try {

            // 线程池不为空，需要关闭线程池
            if (executor != null) {
                ExecutorUtil.shutdownNow(executor, 100);
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }

        try {

            // 断开连接
            disconnect();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }

        try {

            // 进行关闭
            doClose();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void close(int timeout) {
        // 优雅关闭线程池
        ExecutorUtil.gracefulShutdown(executor, timeout);

        // 关闭自己的一些东西
        close();
    }

    @Override
    public String toString() {
        return getClass().getName() + " [" + getLocalAddress() + " -> " + getRemoteAddress() + "]";
    }

    /**
     * Open client.
     *
     * @throws Throwable
     */
    protected abstract void doOpen() throws Throwable;

    /**
     * Close client.
     *
     * @throws Throwable
     */
    protected abstract void doClose() throws Throwable;

    /**
     * Connect to server.
     *
     * @throws Throwable
     */
    protected abstract void doConnect() throws Throwable;

    /**
     * disConnect to server.
     *
     * @throws Throwable
     */
    protected abstract void doDisConnect() throws Throwable;

    /**
     * Get the connected channel.
     *
     * @return channel
     */
    protected abstract Channel getChannel();
}
