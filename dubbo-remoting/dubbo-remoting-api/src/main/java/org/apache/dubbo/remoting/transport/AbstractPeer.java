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
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.Endpoint;
import org.apache.dubbo.remoting.RemotingException;

/**
 * AbstractPeer
 *
 */
public abstract class AbstractPeer implements Endpoint, ChannelHandler {

    /**
     *  通道处理器
     */
    private final ChannelHandler handler;

    /**
     *  持有的url
     */
    private volatile URL url;

    // closing closed means the process is being closed and close is finished
    /**
     * 正在关闭中
     */
    private volatile boolean closing;

    /**
     *  关闭完成
     */
    private volatile boolean closed;


    public AbstractPeer(URL url, ChannelHandler handler) {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        this.url = url;
        this.handler = handler;
    }

    @Override
    public void send(Object message) throws RemotingException {
        // TODO 默认不需要知道是否发生成功？
        // sent="true" 等待消息发出，消息发送失败将抛出异常。
        // sent="false" 不等待消息发出，将消息放入 IO 队列，即刻返回。
        // 详见：http://dubbo.apache.org/zh-cn/docs/user/demos/async-call.html
        send(message, url.getParameter(Constants.SENT_KEY, false));
    }

    @Override
    public void close() {
        // 直接标记已关闭
        closed = true;
    }

    @Override
    public void close(int timeout) {
        close();
    }

    @Override
    public void startClose() {

        // 如果已经关闭，忽略
        if (isClosed()) {
            return;
        }

        // 标记关闭中
        closing = true;
    }

    @Override
    public URL getUrl() {
        return url;
    }

    protected void setUrl(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        this.url = url;
    }

    @Override
    public ChannelHandler getChannelHandler() {

        // 如果通道处理器被装饰过，那么取真实的处理器
        if (handler instanceof ChannelHandlerDelegate) {
            return ((ChannelHandlerDelegate) handler).getHandler();
        } else {
            return handler;
        }
    }

    /**
     * @return ChannelHandler
     */
    @Deprecated
    public ChannelHandler getHandler() {
        return getDelegateHandler();
    }

    /**
     * Return the final handler (which may have been wrapped).
     * This method should be distinguished with getChannelHandler() method
     * 获取被装饰的处理器
     *
     * @return ChannelHandler
     */
    public ChannelHandler getDelegateHandler() {
        return handler;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     *  是否关闭中
     * @return
     */
    public boolean isClosing() {
        return closing && !closed;
    }

    @Override
    public void connected(Channel ch) throws RemotingException {
        // 已关闭，直接忽略
        if (closed) {
            return;
        }

        // 将通道连接事件交给通道处理器处理
        handler.connected(ch);
    }

    @Override
    public void disconnected(Channel ch) throws RemotingException {
        // 连接断开事件
        handler.disconnected(ch);
    }

    @Override
    public void sent(Channel ch, Object msg) throws RemotingException {
        if (closed) {
            return;
        }

        // 发送消息事件
        handler.sent(ch, msg);
    }

    @Override
    public void received(Channel ch, Object msg) throws RemotingException {
        if (closed) {
            return;
        }

        // 消息接收事件
        handler.received(ch, msg);
    }

    @Override
    public void caught(Channel ch, Throwable ex) throws RemotingException {

        // 发生异常时
        handler.caught(ch, ex);
    }
}
