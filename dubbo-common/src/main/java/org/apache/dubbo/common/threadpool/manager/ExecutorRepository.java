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
import org.apache.dubbo.common.extension.SPI;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 执行器仓库
 * 用于给我们创建线程池
 */
@SPI("default")
public interface ExecutorRepository {

    /**
     * Called by both Client and Server. TODO, consider separate these two parts.
     * When the Client or Server starts for the first time, generate a new threadpool
     * according to the parameters specified.
     * 如果线程池不存在，那么创建一个线程池，保证只创建一次，方法会永远返回这个创建的线程池
     *
     * @param url
     * @return
     */
    ExecutorService createExecutorIfAbsent(URL url);

    /**
     *  根据url获取到对应的线程池
     * @param url
     * @return
     */
    ExecutorService getExecutor(URL url);

    /**
     * Modify some of the threadpool's properties according to the url, for example, coreSize, maxSize, ...
     * 根据url来修改线程池的一些属性
     *
     * @param url
     * @param executor
     */
    void updateThreadpool(URL url, ExecutorService executor);

    /**
     * Returns a scheduler from the scheduler list, call this method whenever you need a scheduler for a cron job.
     * If your cron cannot burden the possible schedule delay caused by sharing the same scheduler, please consider define a dedicate one.
     *
     * 获取一个线程调度调度器
     * 如果您的cron不能承受共享同一调度器导致的可能的调度延迟，请考虑定义一个专用的调度器。
     *
     * @return
     */
    ScheduledExecutorService nextScheduledExecutor();

    /**
     * 获取服务暴露线程池
     * @return
     */
    ScheduledExecutorService getServiceExporterExecutor();

    /**
     * Get the default shared threadpool.
     * 获取默认的共享线程池
     *
     * @return
     */
    ExecutorService getSharedExecutor();

}
