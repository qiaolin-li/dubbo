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
 *  zookeeper链接 状态监听器
 */

public interface StateListener {

    /** session丢失？ */
    int SESSION_LOST = 0;

    /** 已链接 */
    int CONNECTED = 1;

    /** 重新接连完成 */
    int RECONNECTED = 2;

    /** TODO 新的Session创建完成？ */
    int NEW_SESSION_CREATED = 4;

    /** TODO 暂停？ */
    int SUSPENDED = 3;

    /**
     *  链接状态发生改变时调用
     * @param connected 链接状态
     */
    void stateChanged(int connected);

}
