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
package org.apache.dubbo.rpc.filter.tps;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.dubbo.rpc.Constants.TPS_LIMIT_RATE_KEY;
import static org.apache.dubbo.rpc.Constants.TPS_LIMIT_INTERVAL_KEY;
import static org.apache.dubbo.rpc.Constants.DEFAULT_TPS_LIMIT_INTERVAL;

/**
 * DefaultTPSLimiter is a default implementation for tps filter. It is an in memory based implementation for storing
 * tps information. It internally use
 * 默认的TPS限制器
 * 基于内存实现
 *
 * @see org.apache.dubbo.rpc.filter.TpsLimitFilter
 */
public class DefaultTPSLimiter implements TPSLimiter {

    /**
     *  服务与统计项的映射关系
     */
    private final ConcurrentMap<String, StatItem> stats = new ConcurrentHashMap<String, StatItem>();

    @Override
    public boolean isAllowable(URL url, Invocation invocation) {
        // 获得tps限制大小
        int rate = url.getParameter(TPS_LIMIT_RATE_KEY, -1);

        // 获取tps周期，例如在1秒钟之类允许10个tps，那么1秒钟就是tps周期
        long interval = url.getParameter(TPS_LIMIT_INTERVAL_KEY, DEFAULT_TPS_LIMIT_INTERVAL);
        String serviceKey = url.getServiceKey();

        // 如果限制大小存在
        if (rate > 0) {
            // 保证该服务的统计项存在
            StatItem statItem = stats.get(serviceKey);
            if (statItem == null) {
                stats.putIfAbsent(serviceKey, new StatItem(serviceKey, rate, interval));
                statItem = stats.get(serviceKey);
            } else {
                //rate or interval has changed, rebuild
                // 比率（限制的大小）、周期已经修改，需要重新构建统计项
                if (statItem.getRate() != rate || statItem.getInterval() != interval) {
                    stats.put(serviceKey, new StatItem(serviceKey, rate, interval));
                    statItem = stats.get(serviceKey);
                }
            }
            return statItem.isAllowable();
        } else {
            // 这里是为了周期修改考虑的吗？？？？？ TODO
            StatItem statItem = stats.get(serviceKey);
            if (statItem != null) {
                stats.remove(serviceKey);
            }
        }

        return true;
    }

}
