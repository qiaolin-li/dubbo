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
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.GenericService;

import java.lang.reflect.Method;


/**
 * ExceptionInvokerFilter
 * <p>
 * Functions:
 * <ol>
 * <li>unexpected exception will be logged in ERROR level on provider side. Unexpected exception are unchecked
 * exception not declared on the interface</li>
 * <li>Wrap the exception not introduced in API package into RuntimeException. Framework will serialize the outer exception but stringnize its cause in order to avoid of possible serialization problem on client side</li>
 * </ol>
 *
 * 异常处理Filter
 * 功能：
 * 1、没有申明的异常，打印日志警告
 * 2、异常不在接口所在的jar、不是jdk的异常、不是dubbo的异常，就会被包装成一个RuntimeException，
 *  避免消费端没有这个异常类，导致出现异常
 */
@Activate(group = CommonConstants.PROVIDER)
public class ExceptionFilter implements Filter, Filter.Listener {
    private Logger logger = LoggerFactory.getLogger(ExceptionFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        return invoker.invoke(invocation);
    }

    @Override
    public void onMessage(Result appResponse, Invoker<?> invoker, Invocation invocation) {

        // 存在异常并且不是泛化调用
        if (appResponse.hasException() && GenericService.class != invoker.getInterface()) {
            try {

                // 获取异常对象
                Throwable exception = appResponse.getException();

                // directly throw if it's checked exception
                // 如果是检查异常，直接抛出
                if (!(exception instanceof RuntimeException) && (exception instanceof Exception)) {
                    return;
                }

                // directly throw if the exception appears in the signature
                // 如果方法上面有申明这个异常，那么直接抛出
                try {
                    Method method = invoker.getInterface().getMethod(invocation.getMethodName(), invocation.getParameterTypes());
                    Class<?>[] exceptionClassses = method.getExceptionTypes();
                    for (Class<?> exceptionClass : exceptionClassses) {
                        if (exception.getClass().equals(exceptionClass)) {
                            return;
                        }
                    }
                } catch (NoSuchMethodException e) {
                    return;
                }

                // for the exception not found in method's signature, print ERROR message in server's log.
                // 对于没有找到方法上的签名的异常，在服务端打印异常异常
                logger.error("Got unchecked and undeclared exception which called by " +
                        RpcContext.getContext().getRemoteHost() + ". service: " +
                        invoker.getInterface().getName() + ", method: " + invocation.getMethodName() +
                        ", exception: " + exception.getClass().getName() + ": " + exception.getMessage(),
                        exception);

                // directly throw if exception class and interface class are in the same jar file.
                // 如果异常类所在的文件和当前接口所在的文件在同一个jar中，那么客户端肯定有这个类，直接抛出即可
                String serviceFile = ReflectUtils.getCodeBase(invoker.getInterface());
                String exceptionFile = ReflectUtils.getCodeBase(exception.getClass());
                if (serviceFile == null || exceptionFile == null || serviceFile.equals(exceptionFile)) {
                    return;
                }

                // directly throw if it's JDK exception
                // 如果是JDK的异常，那么直接抛出，JDK的异常消费者肯定存在
                String className = exception.getClass().getName();
                if (className.startsWith("java.") || className.startsWith("javax.")) {
                    return;
                }

                // directly throw if it's dubbo exception
                // 如果是Dubbo的异常，消费者肯定也有，直接抛出即可
                if (exception instanceof RpcException) {
                    return;
                }

                // otherwise, wrap with RuntimeException and throw back to the client
                // 否则，包装成运行时异常之后抛出给客户端
                appResponse.setException(new RuntimeException(StringUtils.toString(exception)));
            } catch (Throwable e) {
                logger.warn("Fail to ExceptionFilter when called by " + RpcContext.getContext().getRemoteHost() + ". service: " + invoker.getInterface().getName() + ", method: " + invocation.getMethodName() + ", exception: " + e.getClass().getName() + ": " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void onError(Throwable e, Invoker<?> invoker, Invocation invocation) {
        logger.error("Got unchecked and undeclared exception which called by " + RpcContext.getContext().getRemoteHost() + ". service: " + invoker.getInterface().getName() + ", method: " + invocation.getMethodName() + ", exception: " + e.getClass().getName() + ": " + e.getMessage(), e);
    }

    // For test purpose
    public void setLogger(Logger logger) {
        this.logger = logger;
    }
}

