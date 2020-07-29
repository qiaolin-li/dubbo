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

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

/**
 * Set the current execution thread class loader to service interface's class loader.
 * 将当前线程的类加载器设置成服务接口的类加载器
 * 见：https://github.com/apache/dubbo/issues/1406
 * https://github.com/apache/dubbo/issues/178
 * 我想是不是怕你的在接口逻辑中去加载了某些类，但是因为类加载器不是我们的加载器，导致有些类加载不到或加载错误
 */
@Activate(group = CommonConstants.PROVIDER, order = -30000)
public class ClassLoaderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 保存线程原有类加载器
        ClassLoader ocl = Thread.currentThread().getContextClassLoader();

        // 将线程类加载器设置为接口的类加载器
        Thread.currentThread().setContextClassLoader(invoker.getInterface().getClassLoader());
        try {
            return invoker.invoke(invocation);
        } finally {

            // 调用完毕后恢复线程原有类加载器
            Thread.currentThread().setContextClassLoader(ocl);
        }
    }

}
