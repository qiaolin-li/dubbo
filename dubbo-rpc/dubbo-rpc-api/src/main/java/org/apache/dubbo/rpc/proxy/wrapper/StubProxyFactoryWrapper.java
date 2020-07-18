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
package org.apache.dubbo.rpc.proxy.wrapper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.GenericService;

import java.lang.reflect.Constructor;

import static org.apache.dubbo.common.constants.CommonConstants.STUB_EVENT_KEY;
import static org.apache.dubbo.rpc.Constants.DEFAULT_STUB_EVENT;
import static org.apache.dubbo.rpc.Constants.IS_SERVER_KEY;
import static org.apache.dubbo.rpc.Constants.LOCAL_KEY;
import static org.apache.dubbo.rpc.Constants.STUB_EVENT_METHODS_KEY;
import static org.apache.dubbo.rpc.Constants.STUB_KEY;

/**
 * StubProxyFactoryWrapper
 * 代理工厂包装器， 用于在获得 invoker之后，存在本地存根时，做一个包装操作
 * stub、local 均为本地存根，目前使用的是 stub， local为旧版本的
 *
 */
public class StubProxyFactoryWrapper implements ProxyFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(StubProxyFactoryWrapper.class);

    private final ProxyFactory proxyFactory;

    private Protocol protocol;

    public StubProxyFactoryWrapper(ProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public <T> T getProxy(Invoker<T> invoker, boolean generic) throws RpcException {
        return proxyFactory.getProxy(invoker, generic);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> T getProxy(Invoker<T> invoker) throws RpcException {
        T proxy = proxyFactory.getProxy(invoker);

        if (GenericService.class == invoker.getInterface()) {
            return proxy;
        }

        URL url = invoker.getUrl();
        // 获取stub、local配置的存根类名
        String stub = url.getParameter(STUB_KEY, url.getParameter(LOCAL_KEY));

        // 不在本地存根，返回proxy即可
        if (ConfigUtils.isNotEmpty(stub)) {
           return proxy;
        }

        Class<?> serviceType = invoker.getInterface();
        // 如果为true或者default,那么会去尝试 类名+Stub/Local
        if (ConfigUtils.isDefault(stub)) {
            if (url.hasParameter(STUB_KEY)) {
                stub = serviceType.getName() + "Stub";
            } else {
                stub = serviceType.getName() + "Local";
            }
        }


        try {
            // 获取存根类class
            Class<?> stubClass = ReflectUtils.forName(stub);
            // 存根类也必须实现当前引用服务接口
            if (!serviceType.isAssignableFrom(stubClass)) {
                throw new IllegalStateException("The stub implementation class " + stubClass.getName() + " not implement interface " + serviceType.getName());
            }
            try {
                /**
                 *  查找存根类构造，  存根类必须拥有一个以当前引用的接口为参数的构造器
                 *  {@link org.apache.dubbo.config.AbstractInterfaceConfig.checkStubAndLocal}中已经检查是否存在
                 */
                Constructor<?> constructor = ReflectUtils.findConstructor(stubClass, serviceType);

                // 包装它，其实就是一个静态代理
                proxy = (T) constructor.newInstance(new Object[]{proxy});

                // TODO 不知道这是干啥的？为啥还要去暴露服务呢？？？？？
                //export stub service
                URLBuilder urlBuilder = URLBuilder.from(url);
                if (url.getParameter(STUB_EVENT_KEY, DEFAULT_STUB_EVENT)) {
                    urlBuilder.addParameter(STUB_EVENT_METHODS_KEY, StringUtils.join(Wrapper.getWrapper(proxy.getClass()).getDeclaredMethodNames(), ","));
                    urlBuilder.addParameter(IS_SERVER_KEY, Boolean.FALSE.toString());
                    try {
                        export(proxy, (Class) invoker.getInterface(), urlBuilder.build());
                    } catch (Exception e) {
                        LOGGER.error("export a stub service error.", e);
                    }
                }
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("No such constructor \"public " + stubClass.getSimpleName() + "(" + serviceType.getName() + ")\" in stub implementation class " + stubClass.getName(), e);
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to create stub implementation class " + stub + " in consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", cause: " + t.getMessage(), t);
            // ignore
        }
        return proxy;
    }

    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) throws RpcException {
        // stub对服务端没有用，虽然可以配置，但是没有任何效果
        return proxyFactory.getInvoker(proxy, type, url);
    }

    private <T> Exporter<T> export(T instance, Class<T> type, URL url) {
        return protocol.export(proxyFactory.getInvoker(instance, type, url));
    }

}
