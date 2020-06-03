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

import java.util.List;

/**
 * RegistryService. (SPI, Prototype, ThreadSafe)
 * 注册服务
 * @see org.apache.dubbo.registry.Registry
 * @see org.apache.dubbo.registry.RegistryFactory#getRegistry(URL)
 *
 * 注册的类型
 * 路由规则：http://dubbo.apache.org/zh-cn/docs/user/demos/routing-rule.html
 * 配置规则：http://dubbo.apache.org/zh-cn/docs/user/demos/config-rule.html
 **/
public interface RegistryService {

    /**
     * Register data, such as : provider service, consumer address, route rule, override rule and other data.
     * <p>
     * Registering is required to support the contract:<br>
     * 1. When the URL sets the check=false parameter. When the registration fails, the exception is not thrown and retried in the background. Otherwise, the exception will be thrown.<br>
     * 2. When URL sets the dynamic=false parameter, it needs to be stored persistently, otherwise, it should be deleted automatically when the registrant has an abnormal exit.<br>
     * 3. When the URL sets category=routers, it means classified storage, the default category is providers, and the data can be notified by the classified section. <br>
     * 4. When the registry is restarted, network jitter, data can not be lost, including automatically deleting data from the broken line.<br>
     * 5. Allow URLs which have the same URL but different parameters to coexist,they can't cover each other.<br>
     *
     * 注册数据，例如： 提供者服务、消费者地址、路由规则、覆盖规则和其他数据（TODO 配置信息吧）
     * <p>
     * 注册必须要支持如下几点：
     * 1. 当URL参数 check=false时，注册失败不会抛出异常并且会后台重试注册，否则立即抛出异常
     * 2. 当URL参数 dynamic=false时， 需要持久化存储，否则需要删除掉不正确的退出（断电或其他）的注册者
     * 3. 当URL参数 category=routers， 它意味着要分类存储，这个默认的分类是 provider, 这些数据可以由分类通知 TODO 分类通知？？
     * 4. 注册中心重启时，网络抖动时，数据不能丢失,包含断线自动删除的数据
     * 5. 允许URL相同但是参数不同的的URL同时存在，不能覆盖他
     *
     * @param url 注册信心,不能为空: dubbo://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     */
    void register(URL url);

    /**
     * Unregister
     * <p>
     * Unregistering is required to support the contract:<br>
     * 1. If it is the persistent stored data of dynamic=false, the registration data can not be found, then the IllegalStateException is thrown, otherwise it is ignored.<br>
     * 2. Unregister according to the full url match.<br>
     *
     * 取消注册
     * <p>
     * 取消注册需要支持如下：
     * 1. 如果是持久化数据则 dynamic=false, 找不到数据则抛出 IllegalStateException ,否则忽略
     * 2. 按照全URL匹配的方式取消注册，因为注册的时候我们可以接受url相同但是参数不同的URL
     *
     * @param url Registration information , is not allowed to be empty, e.g: dubbo://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     */
    void unregister(URL url);

    /**
     * Subscribe to eligible registered data and automatically push when the registered data is changed.
     * <p>
     * Subscribing need to support contracts:<br>
     * 1. When the URL sets the check=false parameter. When the registration fails, the exception is not thrown and retried in the background. <br>
     * 2. When URL sets category=routers, it only notifies the specified classification data. Multiple classifications are separated by commas, and allows asterisk to match, which indicates that all categorical data are subscribed.<br>
     * 3. Allow interface, group, version, and classifier as a conditional query, e.g.: interface=org.apache.dubbo.foo.BarService&version=1.0.0<br>
     * 4. And the query conditions allow the asterisk to be matched, subscribe to all versions of all the packets of all interfaces, e.g. :interface=*&group=*&version=*&classifier=*<br>
     * 5. When the registry is restarted and network jitter, it is necessary to automatically restore the subscription request.<br>
     * 6. Allow URLs which have the same URL but different parameters to coexist,they can't cover each other.<br>
     * 7. The subscription process must be blocked, when the first notice is finished and then returned.<br>
     *
     * 订阅需要的数据，并且在数据发生改变时自动通知
     * <p>
     * 订阅需要满足的契约
     * 1.当URL中 check=false,订阅失败时不会抛出异常并且后台重试
     * 2.当URL中 category=routers, 只通知指定类型，如果想订阅多个类型，可以使用,号分隔，可以接受*号匹配，表示订阅所有分类的数据
     * 3.允许interface, group, version, classifier作为查询条件， 例：interface=org.apache.dubbo.foo.BarService&version=1.0.0
     * 4.并且查询条件允许*号匹配，订阅所有的接口所有的分组所有的版本, 例：interface=*&group=*&version=*&classifier=*
     * 5.当注册中心重启或网络抖动时，需要自动的恢复这些订阅请求
     * 6.允许URL相同，但是参数不同的URL存在，不能去覆盖它
     * 7.这个订阅流程必须要阻塞完成，等待第一次完成之后返回
     *
     * @param url      Subscription condition, not allowed to be empty, e.g. consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @param listener A listener of the change event, not allowed to be empty
     */
    void subscribe(URL url, NotifyListener listener);

    /**
     * Unsubscribe
     * <p>
     * Unsubscribing is required to support the contract:<br>
     * 1. If don't subscribe, ignore it directly.<br>
     * 2. Unsubscribe by full URL match.<br>
     *
     * 取消订阅
     * <p>
     * 取消订阅需要满足如下要求：
     * 1.如果没有订阅，那么直接忽略
     * 2.取消订阅需要完整的URL匹配
     *
     * @param url      Subscription condition, not allowed to be empty, e.g. consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @param listener A listener of the change event, not allowed to be empty
     */
    void unsubscribe(URL url, NotifyListener listener);

    /**
     * Query the registered data that matches the conditions. Corresponding to the push mode of the subscription, this is the pull mode and returns only one result.
     *
     * 查询匹配条件注册数据，与订阅的推模式相比，这里为拉模式，只返回一次结果
     * @param url Query condition, is not allowed to be empty, e.g. consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @return The registered information list, which may be empty, the meaning is the same as the parameters of {@link org.apache.dubbo.registry.NotifyListener#notify(List<URL>)}.
     * @see org.apache.dubbo.registry.NotifyListener#notify(List)
     */
    List<URL> lookup(URL url);

}