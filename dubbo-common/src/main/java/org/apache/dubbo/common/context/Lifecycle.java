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
package org.apache.dubbo.common.context;

/**
 * Dubbo 组件生命周期
 * @author Administrator
 * @since 2.7.5
 */
public interface Lifecycle {

    /**
     * 在{@link #start() start}之前初始化容器
     * @throws IllegalStateException
     */
    void initialize() throws IllegalStateException;

    /**
b    * 启动这个容器
     * @throws IllegalStateException
     */
    void start() throws IllegalStateException;

    /**
     * 关闭这个容器
     * @throws IllegalStateException
     */
    void destroy() throws IllegalStateException;
}
