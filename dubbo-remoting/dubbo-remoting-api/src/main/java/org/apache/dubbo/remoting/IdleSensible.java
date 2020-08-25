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

package org.apache.dubbo.remoting;

/**
 * Indicate whether the implementation (for both server and client) has the ability to sense and handle idle connection.
 * If the server has the ability to handle idle connection, it should close the connection when it happens, and if
 * the client has the ability to handle idle connection, it should send the heartbeat to the server.
 * 指示实现(针对服务器和客户端)是否能够检测和处理空闲连接。如果服务器有能力处理空闲连接，
 * 它应该在空闲连接发生时关闭连接，如果客户机有能力处理空闲连接，它应该向服务器发送心跳。
 */
public interface IdleSensible {
    /**
     * Whether the implementation can sense and handle the idle connection.
     * By default it's false, the implementation
     * relies on dedicated timer to take care of idle connection.
     * 该实现是否有能力感知并处理控线连接， 默认为false，依赖专用计时器来处理控线连接
     * @return whether has the ability to handle idle connection 是否有能力处理空闲
     *
     */
    default boolean canHandleIdle() {
        return false;
    }
}
