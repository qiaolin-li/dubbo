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
package org.apache.dubbo.remoting;

import org.apache.dubbo.common.URL;

import java.net.InetSocketAddress;

/**
 * Endpoint. (API/SPI, Prototype, ThreadSafe)
 *  端
 *  我现在的理解是，无论是server还是client都可以看成一个端
 *
 * @see org.apache.dubbo.remoting.Channel
 * @see org.apache.dubbo.remoting.Client
 * @see RemotingServer
 */
public interface Endpoint {

    /**
     * get url.
     *
     * @return url
     */
    URL getUrl();

    /**
     * get channel handler.
     * 获取通道处理器
     *
     * @return channel handler
     */
    ChannelHandler getChannelHandler();

    /**
     * get local address.
     * 获取本地地址
     *
     * @return local address.
     */
    InetSocketAddress getLocalAddress();

    /**
     * send message.
     * 发送消息的方法
     *
     * @param message
     * @throws RemotingException
     */
    void send(Object message) throws RemotingException;

    /**
     * send message.
     * 发送消息的方法，TODO 不管消息有没有发送成功？
     *
     * @param message
     * @param sent    already sent to socket? 是否已经发送到套接字
     */
    void send(Object message, boolean sent) throws RemotingException;

    /**
     * close the channel.
     * 关闭端, 这里可能是client、也可能是server、channel等
     */
    void close();

    /**
     * Graceful close the channel.
     * 优雅的关闭
     */
    void close(int timeout);

    /**
     * TODO 更加优雅的关闭？没有时间限制的那种？
     */
    void startClose();

    /**
     * is closed.
     * 端是否已经关闭
     *
     * @return closed
     */
    boolean isClosed();

}