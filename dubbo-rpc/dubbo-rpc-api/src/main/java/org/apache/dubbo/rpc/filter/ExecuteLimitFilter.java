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
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcStatus;

import static org.apache.dubbo.rpc.Constants.EXECUTES_KEY;


/**
 *
 * The maximum parallel execution request count per method per service for the provider.If the max configured
 * <b>executes</b> is set to 10 and if invoke request where it is already 10 then it will throws exception. It
 * continue the same behaviour un till it is <10.
 * 提供者并行执行限流Filter
 */
@Activate(group = CommonConstants.PROVIDER, value = EXECUTES_KEY)
public class ExecuteLimitFilter implements Filter, Filter.Listener {

    /**
     *  执行开始时间key
     */
    private static final String EXECUTELIMIT_FILTER_START_TIME = "execugtelimit_filter_start_time";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        URL url = invoker.getUrl();
        String methodName = invocation.getMethodName();

        // 获取最大并行执行数
        int max = url.getMethodParameter(methodName, EXECUTES_KEY, 0);

        // 如果超过最大并行数，直接抛异常，结束调用
        if (!RpcStatus.beginCount(url, methodName, max)) {
            throw new RpcException(RpcException.LIMIT_EXCEEDED_EXCEPTION,
                    "Failed to invoke method " + invocation.getMethodName() + " in provider " +
                            url + ", cause: The service using threads greater than <dubbo:service executes=\"" + max +
                            "\" /> limited.");
        }

        // 设置调用开始时间
        invocation.put(EXECUTELIMIT_FILTER_START_TIME, System.currentTimeMillis());
        try {
            return invoker.invoke(invocation);
        } catch (Throwable t) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new RpcException("unexpected exception when ExecuteLimitFilter", t);
            }
        }
    }

    @Override
    public void onMessage(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        // 调用成功时，记录调用统计信息
        RpcStatus.endCount(invoker.getUrl(), invocation.getMethodName(), getElapsed(invocation), true);
    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {
        if (t instanceof RpcException) {
            RpcException rpcException = (RpcException) t;

            // 如果是限流异常，那么不需要记录调用信息
            if (rpcException.isLimitExceed()) {
                return;
            }
        }

        // 记录调用统计信息
        RpcStatus.endCount(invoker.getUrl(), invocation.getMethodName(), getElapsed(invocation), false);
    }

    /**
     *  获取调用时长
     * @param invocation
     * @return
     */
    private long getElapsed(Invocation invocation) {
        Object beginTime = invocation.get(EXECUTELIMIT_FILTER_START_TIME);
        return beginTime != null ? System.currentTimeMillis() - (Long) beginTime : 0;
    }
}
