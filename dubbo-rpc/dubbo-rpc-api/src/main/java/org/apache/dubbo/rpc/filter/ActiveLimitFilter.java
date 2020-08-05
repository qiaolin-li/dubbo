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
package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcStatus;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.rpc.Constants.ACTIVES_KEY;

/**
 * ActiveLimitFilter restrict the concurrent client invocation for a service or service's method from client side.
 * To use active limit filter, configured url with <b>actives</b> and provide valid >0 integer value.
 * <pre>
 *     e.g. <dubbo:reference id="demoService" check="false" interface="org.apache.dubbo.demo.DemoService" "actives"="2"/>
 *      In the above example maximum 2 concurrent invocation is allowed.
 *      If there are more than configured (in this example 2) is trying to invoke remote method, then rest of invocation
 *      will wait for configured timeout(default is 0 second) before invocation gets kill by dubbo.
 * </pre>
 * 消费者端并发调用限制Filter
 * 限制当前服务或服务方法的并行调用数量，如：
 * <dubbo:reference id="demoService" check="false" interface="org.apache.dubbo.demo.DemoService" "actives"="2"/>
 * 表示demoService这个接口只能有两个线程并发调用
 * @see Filter
 */
@Activate(group = CONSUMER, value = ACTIVES_KEY)
public class ActiveLimitFilter implements Filter, Filter.Listener {

    /**
     *  调用开始时间key
     */
    private static final String ACTIVELIMIT_FILTER_START_TIME = "activelimit_filter_start_time";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {

        // 获取提供者的url
        URL url = invoker.getUrl();

        // 调用提供者的哪个方法
        String methodName = invocation.getMethodName();

        // 获取并行调用的最大值
        int max = invoker.getUrl().getMethodParameter(methodName, ACTIVES_KEY, 0);

        // 获取url + method维度的RpcStatus，这个为统计对象
        final RpcStatus rpcStatus = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName());

        // 如果超过了配置的最大并行数量
        if (!RpcStatus.beginCount(url, methodName, max)) {

            // 如果配置了超时时间
            long timeout = invoker.getUrl().getMethodParameter(invocation.getMethodName(), TIMEOUT_KEY, 0);

            // 记录开始的时间，等下用来判断是否等待超时
            long start = System.currentTimeMillis();

            // remain为当前
            long remain = timeout;

            // 加锁等待
            synchronized (rpcStatus) {

                // 防止被唤醒之后，名额被别人抢跑了，所以每次唤醒之后得判断一下
                while (!RpcStatus.beginCount(url, methodName, max)) {
                    try {
                        // 等待剩余的描述
                        rpcStatus.wait(remain);
                    } catch (InterruptedException e) {
                        // ignore
                    }

                    // 休眠了多少毫秒
                    long elapsed = System.currentTimeMillis() - start;

                    // 更新还能等待的时间
                    remain = timeout - elapsed;

                    // 如果等待超时，抛出异常，终止调用
                    if (remain <= 0) {
                        throw new RpcException(RpcException.LIMIT_EXCEEDED_EXCEPTION,
                                "Waiting concurrent invoke timeout in client-side for service:  " +
                                        invoker.getInterface().getName() + ", method: " + invocation.getMethodName() +
                                        ", elapsed: " + elapsed + ", timeout: " + timeout + ". concurrent invokes: " +
                                        rpcStatus.getActive() + ". max concurrent invoke limit: " + max);
                    }
                }
            }
        }

        // 设置调用开始时间
        invocation.put(ACTIVELIMIT_FILTER_START_TIME, System.currentTimeMillis());

        // 调用提供者
        return invoker.invoke(invocation);
    }

    @Override
    public void onMessage(Result appResponse, Invoker<?> invoker, Invocation invocation) {

        // 获取方法名
        String methodName = invocation.getMethodName();

        URL url = invoker.getUrl();

        // 最大的并行调用数
        int max = invoker.getUrl().getMethodParameter(methodName, ACTIVES_KEY, 0);

        // 记录统计信息
        RpcStatus.endCount(url, methodName, getElapsed(invocation), true);

        // 通知正在等待调用的线程
        notifyFinish(RpcStatus.getStatus(url, methodName), max);
    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {
        String methodName = invocation.getMethodName();
        URL url = invoker.getUrl();
        int max = invoker.getUrl().getMethodParameter(methodName, ACTIVES_KEY, 0);

        if (t instanceof RpcException) {
            RpcException rpcException = (RpcException) t;

            // 如果是自己上面抛出的异常，就不要去统计信息了，因为都没开始调用
            if (rpcException.isLimitExceed()) {
                return;
            }
        }

        // 记录调用统计信息
        RpcStatus.endCount(url, methodName, getElapsed(invocation), false);

        // 通知正在等待调用的线程
        notifyFinish(RpcStatus.getStatus(url, methodName), max);
    }

    /**
     *  获取调用时长
     * @param invocation
     * @return
     */
    private long getElapsed(Invocation invocation) {
        // 获取调用开始时间
        Object beginTime = invocation.get(ACTIVELIMIT_FILTER_START_TIME);

        // 计算出调用时长
        return beginTime != null ? System.currentTimeMillis() - (Long) beginTime : 0;
    }

    /**
     * 唤醒等待调用的线程
     * @param rpcStatus
     * @param max 最大并行调用数
     */
    private void notifyFinish(final RpcStatus rpcStatus, int max) {
        // 并行数不大于0，不可能出现有人等待的情况
        if (max > 0) {
            // 唤醒所有等待调用的线程
            synchronized (rpcStatus) {
                rpcStatus.notifyAll();
            }
        }
    }
}
