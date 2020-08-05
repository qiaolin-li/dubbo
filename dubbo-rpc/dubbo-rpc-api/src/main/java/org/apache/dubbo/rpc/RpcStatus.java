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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.URL;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * URL statistics. (API, Cached, ThreadSafe)
 * Rpc请求状态对象
 * 用来统计url或url+method维度的一些调用统计数据，可以来做一些限流工作
 * @see org.apache.dubbo.rpc.filter.ActiveLimitFilter
 * @see org.apache.dubbo.rpc.filter.ExecuteLimitFilter
 * @see org.apache.dubbo.rpc.cluster.loadbalance.LeastActiveLoadBalance
 */
public class RpcStatus {

    /**
     * 基于URL维度的RpcContext集合，key为url，值为对应的一些指标
     */
    private static final ConcurrentMap<String, RpcStatus> SERVICE_STATISTICS = new ConcurrentHashMap<>();

    /**
     * 基于URL + method的RpcContext集合，作用同上
     */
    private static final ConcurrentMap<String, ConcurrentMap<String, RpcStatus>> METHOD_STATISTICS = new ConcurrentHashMap<>();

    /**
     * 这个暂时没有被用到，估计是一个占坑的，可以放点自己想放的东西
     */
    private final ConcurrentMap<String, Object> values = new ConcurrentHashMap<>();

    /**
     *  当前并行的访问数
     */
    private final AtomicInteger active = new AtomicInteger();

    /**
     *  调用总次数
     */
    private final AtomicLong total = new AtomicLong();

    /**
     * 调用总错误数
     */
    private final AtomicInteger failed = new AtomicInteger();

    /**
     * 调用总时间，即所有调用的总时长
     */
    private final AtomicLong totalElapsed = new AtomicLong();

    /**
     * 错误调用总时间，发生错误的调用总时长
     */
    private final AtomicLong failedElapsed = new AtomicLong();

    /**
     * 所有调用中最长的调用时间
     */
    private final AtomicLong maxElapsed = new AtomicLong();

    /**
     * 错误调用中最长的调用时间
     */
    private final AtomicLong failedMaxElapsed = new AtomicLong();

    /**
     *  成功调用中最长的调用事件
     */
    private final AtomicLong succeededMaxElapsed = new AtomicLong();

    private RpcStatus() {}

    /**
     * 通过url构建一个 RpcStatus
     * @param url
     * @return status
     */
    public static RpcStatus getStatus(URL url) {
        // 获取为一个uri
        String uri = url.toIdentityString();

        // 保证uri对应的RpcStatus存在
        RpcStatus status = SERVICE_STATISTICS.get(uri);
        if (status == null) {
            SERVICE_STATISTICS.putIfAbsent(uri, new RpcStatus());
            status = SERVICE_STATISTICS.get(uri);
        }
        return status;
    }

    /**
     * 删除url对应的RpcContext
     * @param url
     */
    public static void removeStatus(URL url) {
        String uri = url.toIdentityString();
        SERVICE_STATISTICS.remove(uri);
    }

    /**
     * 通过url + method来获取RpcStatus
     * @param url
     * @param methodName 接口方法名
     * @return status
     */
    public static RpcStatus getStatus(URL url, String methodName) {
        String uri = url.toIdentityString();
        ConcurrentMap<String, RpcStatus> map = METHOD_STATISTICS.get(uri);
        if (map == null) {
            METHOD_STATISTICS.putIfAbsent(uri, new ConcurrentHashMap<String, RpcStatus>());
            map = METHOD_STATISTICS.get(uri);
        }
        RpcStatus status = map.get(methodName);
        if (status == null) {
            map.putIfAbsent(methodName, new RpcStatus());
            status = map.get(methodName);
        }
        return status;
    }

    /**
     * 通过 url + method来删除RpcContext
     * @param url
     */
    public static void removeStatus(URL url, String methodName) {
        String uri = url.toIdentityString();
        ConcurrentMap<String, RpcStatus> map = METHOD_STATISTICS.get(uri);
        if (map != null) {
            map.remove(methodName);
        }
    }

    /**
     * 增加url + method维度的RpcContext中的当前调用数 active
     * @param url
     * @param methodName 调用的方法名
     */
    public static void beginCount(URL url, String methodName) {
        beginCount(url, methodName, Integer.MAX_VALUE);
    }

    /**
     * 增加url + method维度的RpcContext中的当前调用数 active
     * @param max 超过最大值时，返回false
     * @return 增加调用数成功返回true, 反之false
     */
    public static boolean beginCount(URL url, String methodName, int max) {
        // 保证max大于0
        max = (max <= 0) ? Integer.MAX_VALUE : max;

        // 获取url对应的RpcStatus
        RpcStatus appStatus = getStatus(url);

        // 获取url+method对应的RpcStatus, 两重维度来限制访问哈
        RpcStatus methodStatus = getStatus(url, methodName);

        // 如果当前并行调用数已经为int最大值了，那么返回false,我觉得正常情况是不可能的
        if (methodStatus.active.get() == Integer.MAX_VALUE) {
            return false;
        }

        // 如果当前url+method并行调用数增加之后大于max, 那么回滚url+method维度的调用数，并且返回false
        if (methodStatus.active.incrementAndGet() > max) {
            methodStatus.active.decrementAndGet();
            return false;
        } else {

            // 如果method维度的并行调用数没有问题，那么增加url级别的并行数
            // 看起来url维度的并不影响是否调用
            appStatus.active.incrementAndGet();
            return true;
        }
    }

    /**
     * 调用结束时，统计信息
     * @param url
     * @param elapsed 调用时长，毫秒
     * @param succeeded 是否调用成功
     */
    public static void endCount(URL url, String methodName, long elapsed, boolean succeeded) {
        // 统计url维度的
        endCount(getStatus(url), elapsed, succeeded);

        // 统计url+method维度的
        endCount(getStatus(url, methodName), elapsed, succeeded);
    }

    /**
     * 调用结束时，统计信息
     * @param status
     * @param elapsed
     * @param succeeded
     */
    private static void endCount(RpcStatus status, long elapsed, boolean succeeded) {
        // 并行调用数-1
        status.active.decrementAndGet();

        // 总调用数+1
        status.total.incrementAndGet();

        // 更新累计调用时间
        status.totalElapsed.addAndGet(elapsed);

        // 如果当前请求时间比之前的最大请求时长，那么将最大请求时间设置为本地的请求时间
        if (status.maxElapsed.get() < elapsed) {
            status.maxElapsed.set(elapsed);
        }

        // 如果请求是成功的，
        if (succeeded) {
            // 如果请求时间大于最大成功耗时时间，那么覆盖它
            if (status.succeededMaxElapsed.get() < elapsed) {
                status.succeededMaxElapsed.set(elapsed);
            }
        } else {

            // 请求失败，失败计数器+1
            status.failed.incrementAndGet();

            // 失败调用总时长加上我们的调用时长
            status.failedElapsed.addAndGet(elapsed);

            // 如果请求时间大于最大失败耗时时间，那么覆盖它
            if (status.failedMaxElapsed.get() < elapsed) {
                status.failedMaxElapsed.set(elapsed);
            }
        }
    }

    /**
     * set value.
     *
     * @param key
     * @param value
     */
    public void set(String key, Object value) {
        values.put(key, value);
    }

    /**
     * get value.
     *
     * @param key
     * @return value
     */
    public Object get(String key) {
        return values.get(key);
    }

    /**
     * get active.
     *
     * @return active
     */
    public int getActive() {
        return active.get();
    }

    /**
     * get total.
     *
     * @return total
     */
    public long getTotal() {
        return total.longValue();
    }

    /**
     * get total elapsed.
     *
     * @return total elapsed
     */
    public long getTotalElapsed() {
        return totalElapsed.get();
    }

    /**
     * get average elapsed.
     *
     * @return average elapsed
     */
    public long getAverageElapsed() {
        long total = getTotal();
        if (total == 0) {
            return 0;
        }
        return getTotalElapsed() / total;
    }

    /**
     * get max elapsed.
     *
     * @return max elapsed
     */
    public long getMaxElapsed() {
        return maxElapsed.get();
    }

    /**
     * get failed.
     *
     * @return failed
     */
    public int getFailed() {
        return failed.get();
    }

    /**
     * get failed elapsed.
     *
     * @return failed elapsed
     */
    public long getFailedElapsed() {
        return failedElapsed.get();
    }

    /**
     * get failed average elapsed.
     *
     * @return failed average elapsed
     */
    public long getFailedAverageElapsed() {
        long failed = getFailed();
        if (failed == 0) {
            return 0;
        }
        return getFailedElapsed() / failed;
    }

    /**
     * get failed max elapsed.
     *
     * @return failed max elapsed
     */
    public long getFailedMaxElapsed() {
        return failedMaxElapsed.get();
    }

    /**
     * get succeeded.
     *
     * @return succeeded
     */
    public long getSucceeded() {
        return getTotal() - getFailed();
    }

    /**
     * get succeeded elapsed.
     *
     * @return succeeded elapsed
     */
    public long getSucceededElapsed() {
        return getTotalElapsed() - getFailedElapsed();
    }

    /**
     * get succeeded average elapsed.
     *
     * @return succeeded average elapsed
     */
    public long getSucceededAverageElapsed() {
        long succeeded = getSucceeded();
        if (succeeded == 0) {
            return 0;
        }
        return getSucceededElapsed() / succeeded;
    }

    /**
     * get succeeded max elapsed.
     *
     * @return succeeded max elapsed.
     */
    public long getSucceededMaxElapsed() {
        return succeededMaxElapsed.get();
    }

    /**
     * Calculate average TPS (Transaction per second).
     *
     * @return tps
     */
    public long getAverageTps() {
        if (getTotalElapsed() >= 1000L) {
            return getTotal() / (getTotalElapsed() / 1000L);
        }
        return getTotal();
    }


}
