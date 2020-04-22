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
package org.apache.dubbo.rpc.protocol.dubbo;

import org.apache.dubbo.common.Parameters;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.remoting.exchange.ExchangeHandler;
import org.apache.dubbo.remoting.exchange.Exchangers;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.dubbo.remoting.Constants.SEND_RECONNECT_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.LAZY_CONNECT_INITIAL_STATE_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DEFAULT_LAZY_CONNECT_INITIAL_STATE;

/**
 * dubbo protocol support class.
 * 懒加载 客户端链接
 */
@SuppressWarnings("deprecation")
final class LazyConnectExchangeClient implements ExchangeClient {

    /**
     * when this warning rises from invocation, program probably have bug.
     */
    protected static final String REQUEST_WITH_WARNING_KEY = "lazyclient_request_with_warning";
    private final static Logger logger = LoggerFactory.getLogger(LazyConnectExchangeClient.class);

    /**
     *  请求时，是否需要告警
     */
    protected final boolean requestWithWarning;

    /**
     * 服务端的url
     */
    private final URL url;

    /**
     * 请求处理器
     * */
    private final ExchangeHandler requestHandler;
    private final Lock connectLock = new ReentrantLock();

    /**
     *  告警的频率
     */
    private final int warning_period = 5000;

    /**
     * lazy connect, initial state for connection
     * 链接是否已经初始化，因为是懒加载，所以需要有个标识
     */
    private final boolean initialState;

    /**
     * 真正的客户端链接
     * */
    private volatile ExchangeClient client;


    private AtomicLong warningcount = new AtomicLong(0);

    public LazyConnectExchangeClient(URL url, ExchangeHandler requestHandler) {
        // lazy connect, need set send.reconnect = true, to avoid channel bad status.
        // 懒加载链接，需要设置 send-reconnect =true, 防止通道不良状态 TODO 是说有的时候发送失败会重新链接吗
        this.url = url.addParameter(SEND_RECONNECT_KEY, Boolean.TRUE.toString());
        this.requestHandler = requestHandler;

        // 懒加载拦截初始化状态，。默认为啥为true ？ TODO
        this.initialState = url.getParameter(LAZY_CONNECT_INITIAL_STATE_KEY, DEFAULT_LAZY_CONNECT_INITIAL_STATE);

        // 请求时，是否需要告警 TODO 告警什么
        this.requestWithWarning = url.getParameter(REQUEST_WITH_WARNING_KEY, false);
    }

    /**
     *  初始化客户端
     * @throws RemotingException
     */
    private void initClient() throws RemotingException {

        // 客户端被初始化过，直接返回
        if (client != null) {
            return;
        }

        if (logger.isInfoEnabled()) {
            logger.info("Lazy connect to " + url);
        }

        // 加锁，防止多个线程初始化
        connectLock.lock();
        try {
            // 防止在抢到锁之前，别人已经初始化链接了
            if (client != null) {
                return;
            }

            // 创建连接
            this.client = Exchangers.connect(url, requestHandler);
        } finally {
            connectLock.unlock();
        }
    }

    @Override
    public CompletableFuture<Object> request(Object request) throws RemotingException {
        // 先去警告
        warning();
        initClient();
        return client.request(request);
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        if (client == null) {
            return InetSocketAddress.createUnresolved(url.getHost(), url.getPort());
        } else {
            return client.getRemoteAddress();
        }
    }

    @Override
    public CompletableFuture<Object> request(Object request, int timeout) throws RemotingException {
        warning();
        initClient();
        return client.request(request, timeout);
    }

    @Override
    public CompletableFuture<Object> request(Object request, ExecutorService executor) throws RemotingException {
        // 告警 TODO 为啥每次请求都去告警？ 正常的请求也告警码
        warning();

        // 初始化客户端
        initClient();

        // 调用代理的client
        return client.request(request, executor);
    }

    @Override
    public CompletableFuture<Object> request(Object request, int timeout, ExecutorService executor) throws RemotingException {
        warning();
        initClient();
        return client.request(request, timeout, executor);
    }

    /**
     * If {@link #REQUEST_WITH_WARNING_KEY} is configured, then warn once every 5000 invocations.
     * 如果配置了警告，5000次打印一下警告日志
     */
    private void warning() {
        // 一旦开启这个配置，那不是每5000次调用都打印这个错误，TODO 有什么意义呢？？？？？
        if (requestWithWarning) {
            if (warningcount.get() % warning_period == 0) {
                logger.warn(new IllegalStateException("safe guard client , should not be called ,must have a bug."));
            }
            warningcount.incrementAndGet();
        }
    }

    @Override
    public ChannelHandler getChannelHandler() {
        checkClient();
        return client.getChannelHandler();
    }

    @Override
    public boolean isConnected() {
        if (client == null) {
            return initialState;
        } else {
            return client.isConnected();
        }
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        if (client == null) {
            return InetSocketAddress.createUnresolved(NetUtils.getLocalHost(), 0);
        } else {
            return client.getLocalAddress();
        }
    }

    @Override
    public ExchangeHandler getExchangeHandler() {
        return requestHandler;
    }

    @Override
    public void send(Object message) throws RemotingException {
        initClient();
        client.send(message);
    }

    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        initClient();
        client.send(message, sent);
    }

    @Override
    public boolean isClosed() {
        if (client != null) {
            return client.isClosed();
        } else {
            return true;
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public void close(int timeout) {
        if (client != null) {
            client.close(timeout);
        }
    }

    @Override
    public void startClose() {
        if (client != null) {
            client.startClose();
        }
    }

    @Override
    public void reset(URL url) {
        checkClient();
        client.reset(url);
    }

    @Override
    @Deprecated
    public void reset(Parameters parameters) {
        reset(getUrl().addParameters(parameters.getParameters()));
    }

    @Override
    public void reconnect() throws RemotingException {
        checkClient();
        client.reconnect();
    }

    @Override
    public Object getAttribute(String key) {
        if (client == null) {
            return null;
        } else {
            return client.getAttribute(key);
        }
    }

    @Override
    public void setAttribute(String key, Object value) {
        checkClient();
        client.setAttribute(key, value);
    }

    @Override
    public void removeAttribute(String key) {
        checkClient();
        client.removeAttribute(key);
    }

    @Override
    public boolean hasAttribute(String key) {
        if (client == null) {
            return false;
        } else {
            return client.hasAttribute(key);
        }
    }

    private void checkClient() {
        if (client == null) {
            throw new IllegalStateException(
                    "LazyConnectExchangeClient state error. the client has not be init .url:" + url);
        }
    }
}
