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
package org.apache.dubbo.remoting.zookeeper;

import org.apache.dubbo.common.URL;

import java.util.List;
import java.util.concurrent.Executor;

/**
 *  zookeeper 客户端接口
 *  他抽象了一个zookeeper客户端应该有的操作，为了适配多种zookeeper客户端操作工具包
 *  如 curator、zkClient
 */

public interface ZookeeperClient {

    /**
     *  创建一个节点
     * @param path 节点的路径
     * @param ephemeral 是否零时节点
     */
    void create(String path, boolean ephemeral);

    /**
     *  删除一个节点
     * @param path 节点路径
     */
    void delete(String path);

    /**
     *  通过父节点获取下面的子节点
     * @param path 父节点
     * @return
     */
    List<String> getChildren(String path);

    /**
     *  增加一个孩子监听器，当路径下的子节点发生变化时调用监听器 TODO 是孩子监视器不
     * @param path 节点路径
     * @param listener 监视器
     * @return
     */
    List<String> addChildListener(String path, ChildListener listener);

    /**
     * 增加数据监听器，当节点的数据改变时调用监听器
     * @param path:    directory. All of child of path will be listened.
     * @param listener
     */
    void addDataListener(String path, DataListener listener);

    /**
     * 增加数据监听器，当节点的数据改变时调用监听器，并使用线程异步执行
     * @param path:    directory. All of child of path will be listened.
     * @param listener
     * @param executor another thread
     */
    void addDataListener(String path, DataListener listener, Executor executor);

    /**
     * 删除一个数据监听器
     * @param path 父节点路径
     * @param listener 需要移除的监听器
     */
    void removeDataListener(String path, DataListener listener);

    /**
     *  删除一个子节点变动监听器
     * @param path 父节点路径
     * @param listener 需要移除的监听器
     */
    void removeChildListener(String path, ChildListener listener);

    /**
     *  增加状态监听器，状态监听器用于监听客户端链接的状态
     * @param listener 状态监听器
     */
    void addStateListener(StateListener listener);

    /**
     *  删除状态监听器
     * @param listener 状态监听器
     */
    void removeStateListener(StateListener listener);

    /**
     *  是否链接上zookeeper
     * @return
     */
    boolean isConnected();

    /**
     *  关闭zookeeper链接
     */
    void close();

    /**
     *  获取url
     * @return
     */
    URL getUrl();

    /**
     *  创建带有的内容节点
     * @param path 节点的路径
     * @param content 节点的内容
     * @param ephemeral 是否零时节点
     */
    void create(String path, String content, boolean ephemeral);

    /**
     *  获取节点的内容
     * @param path 节点的路径
     * @return 节点的内容
     */
    String getContent(String path);

}
