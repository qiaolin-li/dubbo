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

package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.SPI;

/**
 *  注册中心监听器
 *  <p>
 *  监听注册、解除注册、订阅、解除订阅各种事件
 */

@SPI
public interface RegistryServiceListener {

    /**
     * 注册事件
     * @param url 发起注册的url
     */
    default void onRegister(URL url) {

    }

    /**
     * 解除注册事件
     * @param url 发起解除注册事件的url
     */
    default void onUnregister(URL url) {

    }

    /**
     *  订阅服务事件
     * @param url 发起订阅服务的url
     */
    default void onSubscribe(URL url) {

    }

    /**
     *  解除订阅服务的事件
     * @param url 解除订阅服务的url
     */
    default void onUnsubscribe(URL url) {

    }
}
