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
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.utils.ExecutorUtil;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.RemotingServer;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.remoting.Constants.ACCEPTS_KEY;
import static org.apache.dubbo.remoting.Constants.DEFAULT_ACCEPTS;
import static org.apache.dubbo.remoting.Constants.DEFAULT_IDLE_TIMEOUT;
import static org.apache.dubbo.remoting.Constants.IDLE_TIMEOUT_KEY;

/**
 * AbstractServer
 * 抽象服务端实现
 */
public abstract class AbstractServer extends AbstractEndpoint implements RemotingServer {

    protected static final String SERVER_THREAD_POOL_NAME = "DubboServerHandler";
    private static final Logger logger = LoggerFactory.getLogger(AbstractServer.class);

    /**
     * 线程池，TODO 暂时不知道他是做什么的
     */
    ExecutorService executor;

    /**
     * 本机的地址，一般为0.0.0.0:20880
     */
    private InetSocketAddress localAddress;

    /**
     * 服务器绑定的地址
     */
    private InetSocketAddress bindAddress;

    /**
     *  可接受的通道数，为0时不限制
     */
    private int accepts;

    /**
     *  通道空闲超时时间
     */
    private int idleTimeout;

    /**
     *  执行器仓库，用于缓存线程池，避免多次创建；享元模式
     */
    private ExecutorRepository executorRepository = ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension();

    /**
     * 构建一个服务器对象
     * @param url 开启服务器的url
     * @param handler 时间处理器
     * @throws RemotingException
     */
    public AbstractServer(URL url, ChannelHandler handler) throws RemotingException {

        // 交给父类
        super(url, handler);

        // 获取本地地址
        localAddress = getUrl().toInetSocketAddress();

        // 获取服务器绑定的ip+端口
        String bindIp = getUrl().getParameter(Constants.BIND_IP_KEY, getUrl().getHost());
        int bindPort = getUrl().getParameter(Constants.BIND_PORT_KEY, getUrl().getPort());

        // 如果配置了任意地址，或者地址是非法的，使用任意地址 0.0.0.0
        if (url.getParameter(ANYHOST_KEY, false) || NetUtils.isInvalidLocalHost(bindIp)) {
            bindIp = ANYHOST_VALUE;
        }

        // 获取绑定服务器的地址
        bindAddress = new InetSocketAddress(bindIp, bindPort);

        // 获取服务器最大可以接受的连接数，默认为0，TODO 应该是不限制哈
        this.accepts = url.getParameter(ACCEPTS_KEY, DEFAULT_ACCEPTS);

        // 获取连接空闲超时时间
        this.idleTimeout = url.getParameter(IDLE_TIMEOUT_KEY, DEFAULT_IDLE_TIMEOUT);
        try {
            // 打开服务器
            doOpen();
            if (logger.isInfoEnabled()) {
                logger.info("Start " + getClass().getSimpleName() + " bind " + getBindAddress() + ", export " + getLocalAddress());
            }
        } catch (Throwable t) {
            throw new RemotingException(url.toInetSocketAddress(), null, "Failed to bind " + getClass().getSimpleName()
                    + " on " + getLocalAddress() + ", cause: " + t.getMessage(), t);
        }

        // 获取到当前服务器的线程池，相同的服务器端口只会开启一次线程池
        executor = executorRepository.createExecutorIfAbsent(url);
    }

    protected abstract void doOpen() throws Throwable;

    protected abstract void doClose() throws Throwable;

    @Override
    public void reset(URL url) {
        if (url == null) {
            return;
        }
        try {
            // 更新最大接收数
            if (url.hasParameter(ACCEPTS_KEY)) {
                int a = url.getParameter(ACCEPTS_KEY, 0);
                if (a > 0) {
                    this.accepts = a;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        try {
            // 更新连接空闲超时时间
            if (url.hasParameter(IDLE_TIMEOUT_KEY)) {
                int t = url.getParameter(IDLE_TIMEOUT_KEY, 0);
                if (t > 0) {
                    this.idleTimeout = t;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }

        // 恐怕url更新的是线程池相关的参数，也需要更新一下
        executorRepository.updateThreadpool(url, executor);

        // 将url中的参数更新一下
        super.setUrl(getUrl().addParameters(url.getParameters()));
    }

    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        // 获取所有的客户端
        Collection<Channel> channels = getChannels();

        // 循环发送消息 TODO 有这种场景吗？？
        for (Channel channel : channels) {

            // 防止通道已经关闭
            if (channel.isConnected()) {
                channel.send(message, sent);
            }
        }
    }

    @Override
    public void close() {
        if (logger.isInfoEnabled()) {
            logger.info("Close " + getClass().getSimpleName() + " bind " + getBindAddress() + ", export " + getLocalAddress());
        }

        // 先关闭线程池
        ExecutorUtil.shutdownNow(executor, 100);
        try {

            // 调用父类的关闭逻辑
            super.close();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            // 进行关闭处理
            doClose();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void close(int timeout) {
        ExecutorUtil.gracefulShutdown(executor, timeout);
        close();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public int getAccepts() {
        return accepts;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    @Override
    public void connected(Channel ch) throws RemotingException {
        // If the server has entered the shutdown process, reject any new connection
        // 如果服务器正在关闭或者已经关闭，拒接任何连接
        if (this.isClosing() || this.isClosed()) {
            logger.warn("Close new channel " + ch + ", cause: server is closing or has been closed. For example, receive a new connect request while in shutdown process.");
            ch.close();
            return;
        }

        // 获取所有的连接，如果超出连接数，则不允许这个连接建立
        Collection<Channel> channels = getChannels();
        if (accepts > 0 && channels.size() > accepts) {
            logger.error("Close channel " + ch + ", cause: The server " + ch.getLocalAddress() + " connections greater than max config " + accepts);

            // 不允许连接建立，关闭它
            ch.close();
            return;
        }

        // 如果没有超出，则调用父级的连接逻辑
        super.connected(ch);
    }

    @Override
    public void disconnected(Channel ch) throws RemotingException {

        Collection<Channel> channels = getChannels();

        // TODO 全部连接断开之后，搞这个日志干啥呢？所有消费者挂了会出现这个问题吗
        if (channels.isEmpty()) {
            logger.warn("All clients has disconnected from " + ch.getLocalAddress() + ". You can graceful shutdown now.");
        }
        super.disconnected(ch);
    }

}
