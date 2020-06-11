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

package org.apache.dubbo.registry.retry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.timer.Timeout;
import org.apache.dubbo.common.timer.Timer;
import org.apache.dubbo.common.timer.TimerTask;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.registry.support.FailbackRegistry;

import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.registry.Constants.DEFAULT_REGISTRY_RETRY_PERIOD;
import static org.apache.dubbo.registry.Constants.DEFAULT_REGISTRY_RETRY_TIMES;
import static org.apache.dubbo.registry.Constants.REGISTRY_RETRY_PERIOD_KEY;
import static org.apache.dubbo.registry.Constants.REGISTRY_RETRY_TIMES_KEY;

/**
 * AbstractRetryTask
 * 抽象的重试任务
 */
public abstract class AbstractRetryTask implements TimerTask {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * url for retry task
     * 重试任务的URL
     */
    protected final URL url;

    /**
     * registry for this task
     * 重试任务的注册中心
     */
    protected final FailbackRegistry registry;

    /**
     * retry period
     * 重试间隔
     */
    final long retryPeriod;

    /**
     * define the most retry times
     * 定义最多的重试次数 TODO 是次数吗？
     */
    private final int retryTimes;

    /**
     * task name for this task
     * 任务名称
     */
    private final String taskName;

    /**
     * times of retry.
     * retry task is execute in single thread so that the times is not need volatile.
     * 已经重试的次数，重试的任务是一个单线程执行的，所以不需要 volatile
     */
    private int times = 1;

    /**
     *  是否已取消
     */
    private volatile boolean cancel;

    AbstractRetryTask(URL url, FailbackRegistry registry, String taskName) {
        if (url == null || StringUtils.isBlank(taskName)) {
            throw new IllegalArgumentException();
        }
        this.url = url;
        this.registry = registry;
        this.taskName = taskName;
        cancel = false;

        // 重试间隔
        this.retryPeriod = url.getParameter(REGISTRY_RETRY_PERIOD_KEY, DEFAULT_REGISTRY_RETRY_PERIOD);

        // 最多的重试次数
        this.retryTimes = url.getParameter(REGISTRY_RETRY_TIMES_KEY, DEFAULT_REGISTRY_RETRY_TIMES);
    }

    /**
     * 取消任务
     */
    public void cancel() {
        cancel = true;
    }

    /**
     *  任务是否已经取消
     * @return
     */
    public boolean isCancel() {
        return cancel;
    }


    protected void reput(Timeout timeout, long tick) {
        if (timeout == null) {
            throw new IllegalArgumentException();
        }

        Timer timer = timeout.timer();

        // 防止定时器、超市取消、任务取消
        if (timer.isStop() || timeout.isCancelled() || isCancel()) {
            return;
        }

        // 增加重试次数
        times++;

        // 重新加入到重试任务中
        timer.newTimeout(timeout.task(), tick, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        // 任务是否已经取消
        if (timeout.isCancelled() || timeout.timer().isStop() || isCancel()) {
            // other thread cancel this timeout or stop the timer.
            return;
        }
        if (times > retryTimes) {
            // reach the most times of retry.
            // 重试次数过多
            logger.warn("Final failed to execute task " + taskName + ", url: " + url + ", retry " + retryTimes + " times.");
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info(taskName + " : " + url);
        }
        try {
            // 去重试任务
            doRetry(url, registry, timeout);
        } catch (Throwable t) { // Ignore all the exceptions and wait for the next retry
            logger.warn("Failed to execute task " + taskName + ", url: " + url + ", waiting for again, cause:" + t.getMessage(), t);
            // reput this task when catch exception.
            // 失败时重新防止此任务
            reput(timeout, retryPeriod);
        }
    }

    /**
     *  去重试任务
     * @param url
     * @param registry
     * @param timeout
     */
    protected abstract void doRetry(URL url, FailbackRegistry registry, Timeout timeout);
}
