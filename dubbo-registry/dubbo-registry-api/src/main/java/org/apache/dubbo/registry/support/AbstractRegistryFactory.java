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
package org.apache.dubbo.registry.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;
import org.apache.dubbo.registry.RegistryService;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;

/**
 * AbstractRegistryFactory. (SPI, Singleton, ThreadSafe)
 * 抽象的注册中心工厂
 * 1、模板方法体现，定义了基本的框架，先从缓存中取注册中心，取不到再去创建
 * 2、持有所有的注册中心
 * @see org.apache.dubbo.registry.RegistryFactory
 */
public abstract class AbstractRegistryFactory implements RegistryFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRegistryFactory.class);

    /**
     * The lock for the acquisition process of the registry
     * 获取注册中心时的锁
     */
    private static final ReentrantLock LOCK = new ReentrantLock();

    /**
     * Registry Collection Map<RegistryAddress, Registry>
     *  注册中心集合
     */
    private static final Map<String, Registry> REGISTRIES = new HashMap<>();

    /**
     * 注册中心工厂是否已经销毁
     * */
    private static final AtomicBoolean destroyed = new AtomicBoolean(false);

    /**
     * Get all registries
     * 获取全部的注册中心
     * @return all registries
     */
    public static Collection<Registry> getRegistries() {
        return Collections.unmodifiableCollection(REGISTRIES.values());
    }

    public static Registry getRegistry(String key) {
        return REGISTRIES.get(key);
    }

    /**
     * Close all created registries
     * 销毁已经创建的注册中心
     */
    public static void destroyAll() {
        // 防止多次销毁
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Close all registries " + getRegistries());
        }

        // Lock up the registry shutdown process
        // 加锁处理关闭注册中心
        LOCK.lock();
        try {
            for (Registry registry : getRegistries()) {
                try {
                    registry.destroy();
                } catch (Throwable e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }

            // 清空注册中心集合
            REGISTRIES.clear();
        } finally {
            // Release the lock
            LOCK.unlock();
        }
    }

    @Override
    public Registry getRegistry(URL url) {

        // 如果已销毁，返回默认的注册中心，默认的注册中心方法实现都为空
        if (destroyed.get()) {
            LOGGER.warn("All registry instances have been destroyed, failed to fetch any instance. " +
                    "Usually, this means no need to try to do unnecessary redundant resource clearance, all registries has been taken care of.");
            return DEFAULT_NOP_REGISTRY;
        }

        url = URLBuilder.from(url)
                .setPath(RegistryService.class.getName())
                .addParameter(INTERFACE_KEY, RegistryService.class.getName())
                .removeParameters(EXPORT_KEY, REFER_KEY)
                .build();

        // 获取缓存key TODO 这里应该是同一个注册中心拿一次啊
        // zookeeper://qiaolin.vip:8888/org.apache.dubbo.registry.RegistryService
        String key = url.toServiceStringWithoutResolving();

        // Lock the registry access process to ensure a single instance of the registry
        // 加锁去确保注册中心只有一个实例
        LOCK.lock();
        try {

            // 先从缓存中获取
            Registry registry = REGISTRIES.get(key);
            if (registry != null) {
                return registry;
            }

            //create registry by spi/ioc
            // 创建注册中心
            registry = createRegistry(url);

            if (registry == null) {
                throw new IllegalStateException("Can not create registry " + url);
            }

            // 追加到Map中
            REGISTRIES.put(key, registry);
            return registry;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     *  根据url创建注册中心
     * @param url 注册中心url,包含了地址信息
     * @return
     */
    protected abstract Registry createRegistry(URL url);


    /**
     *  默认的注册中心实现，所有的方法都是空实现
     */
    private static Registry DEFAULT_NOP_REGISTRY = new Registry() {
        @Override
        public URL getUrl() {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void destroy() {

        }

        @Override
        public void register(URL url) {

        }

        @Override
        public void unregister(URL url) {

        }

        @Override
        public void subscribe(URL url, NotifyListener listener) {

        }

        @Override
        public void unsubscribe(URL url, NotifyListener listener) {

        }

        @Override
        public List<URL> lookup(URL url) {
            return null;
        }
    };

    // for unit test
    public static void clearRegistryNotDestroy() {
        REGISTRIES.clear();
    }

}
