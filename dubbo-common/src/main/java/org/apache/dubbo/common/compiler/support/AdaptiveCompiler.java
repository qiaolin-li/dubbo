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
package org.apache.dubbo.common.compiler.support;

import org.apache.dubbo.common.compiler.Compiler;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionLoader;

/**
 * AdaptiveCompiler. (SPI, Singleton, ThreadSafe)
 * 编辑器适配类
 * 根据不同的配置调用不同的编译器，可通过 <dubbo:application compiler="jdk" /> 指定编译器
 */
@Adaptive
public class AdaptiveCompiler implements Compiler {

    // 编译器名称， jdk或javassist
    private static volatile String DEFAULT_COMPILER;

    public static void setDefaultCompiler(String compiler) {
        DEFAULT_COMPILER = compiler;
    }

    @Override
    public Class<?> compile(String code, ClassLoader classLoader) {
        Compiler compiler;

        // 获取Compiler的扩展加载器
        ExtensionLoader<Compiler> loader = ExtensionLoader.getExtensionLoader(Compiler.class);

        // 获取编译器的名称
        String name = DEFAULT_COMPILER;

        // 如果指定了编译器，就使用指定的编译器，否则使用默认编译器 javassist
        if (name != null && name.length() > 0) {
            compiler = loader.getExtension(name);
        } else {
            compiler = loader.getDefaultExtension();
        }

        // 使用编译器进行编译源代码
        return compiler.compile(code, classLoader);
    }

}
