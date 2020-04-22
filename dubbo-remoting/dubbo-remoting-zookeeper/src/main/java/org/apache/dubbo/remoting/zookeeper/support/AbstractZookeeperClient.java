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
package org.apache.dubbo.remoting.zookeeper.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.remoting.zookeeper.ChildListener;
import org.apache.dubbo.remoting.zookeeper.DataListener;
import org.apache.dubbo.remoting.zookeeper.StateListener;
import org.apache.dubbo.remoting.zookeeper.ZookeeperClient;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

/**
 *  抽象zookeeper客户端，提供一些默认的实现
 * @param <TargetDataListener> 真正的数据监听器类型
 * @param <TargetChildListener> 真正的子节点改变监听器
 */

public abstract class AbstractZookeeperClient<TargetDataListener, TargetChildListener> implements ZookeeperClient {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractZookeeperClient.class);

    /** 连接默认的超时时间 */
    protected int DEFAULT_CONNECTION_TIMEOUT_MS = 5 * 1000;

    /** session默认的超时时间 */
    protected int DEFAULT_SESSION_TIMEOUT_MS = 60 * 1000;

    /**
     * TODO 这里应该是创建 zookeeper链接的时候的那个url
     * */
    private final URL url;

    /**
     * 客户端状态监听器，
     **/
    private final Set<StateListener> stateListeners = new CopyOnWriteArraySet<>();

    /**
     * 子节点变动监听器
     * <监听的父节点, <子节点监听器，目标子节点监听器>>
     */
    private final ConcurrentMap<String, ConcurrentMap<ChildListener, TargetChildListener>> childListeners = new ConcurrentHashMap<>();

    /**
     * 节点数据变动监听器
     * <监听的父节点, <子节点监听器，目标节点数据监听器>>
     */
    private final ConcurrentMap<String, ConcurrentMap<DataListener, TargetDataListener>> listeners = new ConcurrentHashMap<>();

    /**
     * 客户端链接是否已经关闭
     */
    private volatile boolean closed = false;

    /**
     * 持久化节点缓存，可减少和zookeeper打交道
     * */
    private final Set<String>  persistentExistNodePath = new ConcurrentHashSet<>();

    public AbstractZookeeperClient(URL url) {
        this.url = url;
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public void delete(String path){
        //never mind if ephemeral
        // 即使是零时节点也不要紧，TODO 这是先删除本地缓存的吗？ 答：是的
        persistentExistNodePath.remove(path);

        // 真正删除zookeeper上的节点
        deletePath(path);
    }


    @Override
    public void create(String path, boolean ephemeral) {

        // 不是零时节点
        if (!ephemeral) {
            // 看本地是否存在，存在直接返回，不用创建了
            if(persistentExistNodePath.contains(path)){
                return;
            }
            // 看zookeeper中是否存在，存在直接返回，不用创建了
            if (checkExists(path)) {
                // 如果zookeeper中存在，那么缓存到本地，避免多次创建
                persistentExistNodePath.add(path);
                return;
            }
        }

        // 看节点是否有父节点  xxx/xxx : 有  xxx : 无
        int i = path.lastIndexOf('/');
        if (i > 0) {
            // 有父节点先去创建父节点，这里可能会有一个递归 当path = /xx/zz/hh/ddd
            create(path.substring(0, i), false);
        }

        if (ephemeral) {
            // 如果是零时节点
            createEphemeral(path);
        } else {

            // 持久化节点
            createPersistent(path);

            // 在本地缓存下，避免经常和zookeeper打交道
            persistentExistNodePath.add(path);
        }
    }

    @Override
    public void addStateListener(StateListener listener) {
        stateListeners.add(listener);
    }

    @Override
    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    public Set<StateListener> getSessionListeners() {
        return stateListeners;
    }

    @Override
    public List<String> addChildListener(String path, final ChildListener listener) {

        // 获取当前节点的所有监听器集合
        ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);

        // 监听器集合为空，给他加一个
        if (listeners == null) {

            // 这里解决了并发问题，避免多个线程put了不同的map，只会有一个能放入成功
            childListeners.putIfAbsent(path, new ConcurrentHashMap<>());

            // 重新获取，
            listeners = childListeners.get(path);
        }

        // 通过listener获取 目标子节点监听器
        TargetChildListener targetListener = listeners.get(listener);

        // 当目标子监听器为空时，创建一个
        if (targetListener == null) {
            listeners.putIfAbsent(listener, createTargetChildListener(path, listener));
            targetListener = listeners.get(listener);
        }

        // 增加监听器
        return addTargetChildListener(path, targetListener);
    }

    @Override
    public void addDataListener(String path, DataListener listener) {
        this.addDataListener(path, listener, null);
    }

    @Override
    public void addDataListener(String path, DataListener listener, Executor executor) {
        ConcurrentMap<DataListener, TargetDataListener> dataListenerMap = listeners.get(path);
        if (dataListenerMap == null) {
            listeners.putIfAbsent(path, new ConcurrentHashMap<DataListener, TargetDataListener>());
            dataListenerMap = listeners.get(path);
        }
        TargetDataListener targetListener = dataListenerMap.get(listener);

        // 数据监听器为空，则创建一个
        if (targetListener == null) {
            dataListenerMap.putIfAbsent(listener, createTargetDataListener(path, listener));
            targetListener = dataListenerMap.get(listener);
        }

        // 增加数据监听器
        addTargetDataListener(path, targetListener, executor);
    }

    @Override
    public void removeDataListener(String path, DataListener listener ){

        // 获取当前节点的监听器 TODO 为什么 还要DataListener呢？ TargetDataListener中不就包含
        ConcurrentMap<DataListener, TargetDataListener> dataListenerMap = listeners.get(path);
        if (dataListenerMap != null) {

            // 删除 listener，
            TargetDataListener targetListener = dataListenerMap.remove(listener);
            if(targetListener != null){
                // 取消目标监听器
                removeTargetDataListener(path, targetListener);
            }
        }
    }

    @Override
    public void removeChildListener(String path, ChildListener listener) {
        ConcurrentMap<ChildListener, TargetChildListener> listeners = childListeners.get(path);
        if (listeners != null) {

            // 删除 listener，
            TargetChildListener targetListener = listeners.remove(listener);
            if (targetListener != null) {

                // 取消节点上的子节点改变监听器
                removeTargetChildListener(path, targetListener);
            }
        }
    }

    /**
     *  当zookeeper客户端链接状态发生改变时调用
     * @param state 客户端链接状态
     */
    protected void stateChanged(int state) {

        // 通知我们自定义的监听器，这个方法其实是为了兼容更多的zookeeper客户端框架
        for (StateListener sessionListener : getSessionListeners()) {
            sessionListener.stateChanged(state);
        }
    }

    @Override
    public void close() {

        // 防止多次关闭
        if (closed) {
            return;
        }

        // 标记已关闭
        closed = true;
        try {

            // 真正去关闭客户端链接
            doClose();
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }

    @Override
    public void create(String path, String content, boolean ephemeral) {

        // 如果已经存在，那么先删除他
        if (checkExists(path)) {
            delete(path);
        }

        int i = path.lastIndexOf('/');
        if (i > 0) {
            // 如果自己有父节点，那么先去创建它
            create(path.substring(0, i), false);
        }

        // 根据条件来创建节点，零时节点 or 持久化节点
        if (ephemeral) {
            createEphemeral(path, content);
        } else {
            createPersistent(path, content);
        }
    }

    @Override
    public String getContent(String path) {

        // 如果不存在这个节点，直接返回null
        if (!checkExists(path)) {
            return null;
        }

        // 存在节点，去zookeeper服务器获取值
        return doGetContent(path);
    }

    /**
     * 真正关闭连接的方法
     */
    protected abstract void doClose();

    /**
     * 创建一个持久化节点
     * @param path 节点的路径
     */
    protected abstract void createPersistent(String path);

    /**
     * 创建一个零时节点
     * @param path 节点的路径
     */
    protected abstract void createEphemeral(String path);

    /**
     * 创建一个带内容的持久化节点
     * @param path 节点的路径
     * @param data 节点的数据
     */
    protected abstract void createPersistent(String path, String data);

    /**
     * 创建一个带内容的零时节点
     * @param path 节点的路径
     * @param data 节点的数据
     */
    protected abstract void createEphemeral(String path, String data);

    /**
     *  检查节点是否存在
     * @param path 节点的路径
     * @return 是否存在
     */
    protected abstract boolean checkExists(String path);

    /**
     *  创建目标子节点监听器 TODO 目标？
     * @param path 要监听的节点路径
     * @param listener 监听器
     * @return 目标监听器
     */
    protected abstract TargetChildListener createTargetChildListener(String path, ChildListener listener);

    /**
     *  给节点增加目标子节点监听器
     * @param path 需要监听的节点
     * @param listener 监听器
     * @return
     */
    protected abstract List<String> addTargetChildListener(String path, TargetChildListener listener);

    /**
     *  创建目标数据改动监听器
     * @param path 需要监听的路径
     * @param listener 数据监听器
     * @return
     */
    protected abstract TargetDataListener createTargetDataListener(String path, DataListener listener);

    /**
     *  增加数据监听器
     * @param path 需要监听的目录
     * @param listener 监听器
     */
    protected abstract void addTargetDataListener(String path, TargetDataListener listener);

    /**
     *  增加数据监听器
     * @param path 需要监听的目录
     * @param listener 监听器
     * @param executor 执行器
     */
    protected abstract void addTargetDataListener(String path, TargetDataListener listener, Executor executor);

    /**
     *  从目标节点删除数据改变监听器
     * @param path 需要删除数据改变监听器的节点
     * @param listener 需要删除的数据改变监听器
     */
    protected abstract void removeTargetDataListener(String path, TargetDataListener listener);

    /**
     *  从目标节点删除子节点改变监听器
     * @param path 需要删除子节点改变监听器的节点
     * @param listener 需要删除的子节点改变监听器
     */
    protected abstract void removeTargetChildListener(String path, TargetChildListener listener);

    /**
     *  获取节点的值
     * @param path
     * @return
     */
    protected abstract String doGetContent(String path);

    /**
     * we invoke the zookeeper client to delete the node
     * 真正删除zookeeper上的节点
     * @param path the node path 节点的路径
     */
    protected abstract void deletePath(String path);

}
