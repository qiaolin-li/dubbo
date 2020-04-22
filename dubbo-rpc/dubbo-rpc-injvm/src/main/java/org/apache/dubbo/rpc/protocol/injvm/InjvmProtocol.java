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
package org.apache.dubbo.rpc.protocol.injvm;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.protocol.AbstractProtocol;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.util.Map;

import static org.apache.dubbo.rpc.Constants.SCOPE_KEY;
import static org.apache.dubbo.rpc.Constants.SCOPE_LOCAL;
import static org.apache.dubbo.rpc.Constants.SCOPE_REMOTE;
import static org.apache.dubbo.rpc.Constants.GENERIC_KEY;
import static org.apache.dubbo.rpc.Constants.LOCAL_PROTOCOL;

/**
 * InjvmProtocol
 * 本地服务暴露协议
 */
public class InjvmProtocol extends AbstractProtocol implements Protocol {

    public static final String NAME = LOCAL_PROTOCOL;

    // 本地默认端口
    public static final int DEFAULT_PORT = 0;

    // 利用SPI产生的实例
    private static InjvmProtocol INSTANCE;

    public InjvmProtocol() {
        INSTANCE = this;
    }

    public static InjvmProtocol getInjvmProtocol() {
        if (INSTANCE == null) {
            ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(InjvmProtocol.NAME); // load
        }
        return INSTANCE;
    }

    static Exporter<?> getExporter(Map<String, Exporter<?>> map, URL key) {
        Exporter<?> result = null;

        if (!key.getServiceKey().contains("*")) {
            result = map.get(key.getServiceKey());
        } else {
            if (CollectionUtils.isNotEmptyMap(map)) {
                for (Exporter<?> exporter : map.values()) {
                    if (UrlUtils.isServiceKeyMatch(key, exporter.getInvoker().getUrl())) {
                        result = exporter;
                        break;
                    }
                }
            }
        }

        if (result == null) {
            return null;
        } else if (ProtocolUtils.isGeneric(
                result.getInvoker().getUrl().getParameter(GENERIC_KEY))) {
            return null;
        } else {
            return result;
        }
    }

    @Override
    public int getDefaultPort() {
        return DEFAULT_PORT;
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        return new InjvmExporter<T>(invoker, invoker.getUrl().getServiceKey(), exporterMap);
    }

    @Override
    public <T> Invoker<T> protocolBindingRefer(Class<T> serviceType, URL url) throws RpcException {
        // 本地引用invoker, exporterMap就是服务导出时存放的map
        return new InjvmInvoker<T>(serviceType, url, url.getServiceKey(), exporterMap);
    }

    /**
     *  判断url是不是本地引用
     * @param url
     * @return
     */
    public boolean isInjvmRefer(URL url) {

        String scope = url.getParameter(SCOPE_KEY);

        // Since injvm protocol is configured explicitly, we don't need to set any extra flag, use normal refer process.
        // scope=local 或者 parameter.injvm=true
        if (SCOPE_LOCAL.equals(scope) || (url.getParameter(LOCAL_PROTOCOL, false))) {
            // if it's declared as local reference
            // 'scope=local' is equivalent to 'injvm=true', injvm will be deprecated in the future release
            return true;
        } else if (SCOPE_REMOTE.equals(scope)) {
            // it's declared as remote reference
            return false;
        } else if (url.getParameter(GENERIC_KEY, false)) {
            // generic invocation is not local reference
            // 泛化引用不是本地调用
            return false;
        } else if (getExporter(exporterMap, url) != null) {
            // by default, go through local reference if there's the service exposed locally
            // 从本地已经暴露的服务中寻找当前需要引用的服务，如果找到暴露器，说明本地已经暴露过，则为本地暴露
            return true;
        } else {
            return false;
        }
    }
}
