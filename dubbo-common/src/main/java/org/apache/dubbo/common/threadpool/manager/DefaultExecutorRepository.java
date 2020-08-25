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
package org.apache.dubbo.common.threadpool.manager;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.ThreadPool;
import org.apache.dubbo.common.utils.NamedThreadFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.EXECUTOR_SERVICE_COMPONENT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.THREADS_KEY;

/**
 * Consider implementing {@code Licycle} to enable executors shutdown when the process stops.
 * 线程池仓库默认实现
 */
public class DefaultExecutorRepository implements ExecutorRepository {
    private static final Logger logger = LoggerFactory.getLogger(DefaultExecutorRepository.class);

    private int DEFAULT_SCHEDULER_SIZE = Runtime.getRuntime().availableProcessors();

    /**
     * 单实例的共享线程池
     */
    private final ExecutorService SHARED_EXECUTOR = Executors.newCachedThreadPool(new NamedThreadFactory("DubboSharedHandler", true));

    /**
     * 线程调度池，TODO 现在这个东西没屌用，是否未来有规划
     * 详见：https://github.com/apache/dubbo/issues/6633
     */
    private Ring<ScheduledExecutorService> scheduledExecutors = new Ring<>();

    /**
     * 用于服务暴露线程池
     */
    private ScheduledExecutorService serviceExporterExecutor;

    /**
     * 应该是客户端、重连的一个线程池，但是现在没用到
     */
    private ScheduledExecutorService reconnectScheduledExecutor;

    private ConcurrentMap<String, ConcurrentMap<Integer, ExecutorService>> data = new ConcurrentHashMap<>();

    public DefaultExecutorRepository() {
//        for (int i = 0; i < DEFAULT_SCHEDULER_SIZE; i++) {
//            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Dubbo-framework-scheduler"));
//            scheduledExecutors.addItem(scheduler);
//        }
//
//        reconnectScheduledExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Dubbo-reconnect-scheduler"));

        // 创建线程调度池
        serviceExporterExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("Dubbo-exporter-scheduler"));
    }

    /**
     * Get called when the server or client instance initiating.
     * 在Server或Client初始化时调用该实例
     * @param url
     * @return
     */
    public synchronized ExecutorService createExecutorIfAbsent(URL url) {

        // 容器名称，默认为 ExecutorService类名, 如果url是客户端侧的，那么容器名称为Consumer
        String componentKey = EXECUTOR_SERVICE_COMPONENT_KEY;
        if (CONSUMER_SIDE.equalsIgnoreCase(url.getParameter(SIDE_KEY))) {
            componentKey = CONSUMER_SIDE;
        }

        // 获取容器key对应的 线程池map
        Map<Integer, ExecutorService> executors = data.computeIfAbsent(componentKey, k -> new ConcurrentHashMap<>());

        // 每个不同的端口都开了不同的线程池
        Integer portKey = url.getPort();

        // 如果端口对应的线程池不存在，那么创建它
        ExecutorService executor = executors.computeIfAbsent(portKey, k -> createExecutor(url));

        // If executor has been shut down, create a new one
        // 如果线程池已经关闭，那么重新创建一个，并放入map中缓存
        if (executor.isShutdown() || executor.isTerminated()) {

            // remove旧的 TODO 为什么要先remove呢？ 覆盖行不行呢？看起来也是没问题的
            executors.remove(portKey);
            executor = createExecutor(url);
            executors.put(portKey, executor);
        }
        return executor;
    }

    @Override
    public ExecutorService getExecutor(URL url) {

        // 获取容器key
        String componentKey = EXECUTOR_SERVICE_COMPONENT_KEY;
        if (CONSUMER_SIDE.equalsIgnoreCase(url.getParameter(SIDE_KEY))) {

            // url 为客户端侧的话，容器key为consumer TODO 为什么客户端要单独分开呢？
            componentKey = CONSUMER_SIDE;
        }
        Map<Integer, ExecutorService> executors = data.get(componentKey);

        /**
         * It's guaranteed that this method is called after {@link #createExecutorIfAbsent(URL)},
         * so data should already
         * have Executor instances generated and stored
         * 可以保证此方法是在 {@link #createExecutorIfAbsent(URL)}方法调用之后调用，
         * TODO 既然这样，为什么要判断空呢？并且下面不存在该端口的线程池时又要创建它？
         */
        if (executors == null) {
            logger.warn("No available executors, this is not expected, framework should call createExecutorIfAbsent first " +
                    "before coming to here.");
            return null;
        }

        // 如果线程池不存在，那么创建他
        Integer portKey = url.getPort();
        ExecutorService executor = executors.get(portKey);
        if (executor != null) {
            if (executor.isShutdown() || executor.isTerminated()) {
                executors.remove(portKey);
                executor = createExecutor(url);
                executors.put(portKey, executor);
            }
        }
        return executor;
    }

    @Override
    public void updateThreadpool(URL url, ExecutorService executor) {
        try {
            // 1、url中必须要有 threads这个参数，这个是线程数
            // 2、执行器必须是 ThreadPoolExecutor的实例
            // 3、执行器不能关闭了
            if (url.hasParameter(THREADS_KEY)
                    && executor instanceof ThreadPoolExecutor && !executor.isShutdown()) {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;

                // 如果线程数
                int threads = url.getParameter(THREADS_KEY, 0);

                // 获取线程池当前的最大线程数
                int max = threadPoolExecutor.getMaximumPoolSize();

                // 获取线程池当前核心线程数
                int core = threadPoolExecutor.getCorePoolSize();

                // 如果待更新的线程数大于0， 并且和当前线程的最大线程数、核心线程数不相等，那么设置它
                if (threads > 0 && (threads != max || threads != core)) {
                    if (threads < core) {
                        threadPoolExecutor.setCorePoolSize(threads);
                        if (core == max) {
                            threadPoolExecutor.setMaximumPoolSize(threads);
                        }
                    } else {
                        threadPoolExecutor.setMaximumPoolSize(threads);
                        if (core == max) {
                            threadPoolExecutor.setCorePoolSize(threads);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }

    @Override
    public ScheduledExecutorService nextScheduledExecutor() {
        return scheduledExecutors.pollItem();
    }

    @Override
    public ScheduledExecutorService getServiceExporterExecutor() {
        return serviceExporterExecutor;
    }

    @Override
    public ExecutorService getSharedExecutor() {
        return SHARED_EXECUTOR;
    }

    private ExecutorService createExecutor(URL url) {

        // 通过spi创建不同的线程池
        return (ExecutorService) ExtensionLoader.getExtensionLoader(ThreadPool.class).getAdaptiveExtension().getExecutor(url);
    }

}
