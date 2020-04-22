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
package org.apache.dubbo.remoting.zookeeper;

/**
 * 监听节点数据的改变
 * 当被监听的节点数据发生改变时调用
 */
public interface DataListener {

    /**
     *  数据发生改变时
     * @param path 改变数据节点的数据
     * @param value 改变后的值 TODO
     * @param eventType TODO 事件类型？不懂
     */
    void dataChanged(String path, Object value, EventType eventType);
}
