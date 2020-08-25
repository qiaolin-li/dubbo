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

import java.util.concurrent.atomic.LongAdder;

/**
 * Judge whether a particular invocation of service provider method should be allowed within a configured time interval.
 * As a state it contain name of key ( e.g. method), last invocation time, interval and rate count.
 * 调用统计信息
 * 通过它来判断在某个周期内是否超过了可调用数
 */
class StatItem {

    /**
     * 对应 URL serviceKey
     */
    private String name;

    /**
     *  最后的重置种子数的时间，为毫秒
     */
    private long lastResetTime;

    /**
     * 周期
     */
    private long interval;

    /**
     * 当前周期已经生成的种子数
     */
    private LongAdder token;

    /**
     * 一个周期有多少种子
     */
    private int rate;

    StatItem(String name, int rate, long interval) {
        this.name = name;
        this.rate = rate;
        this.interval = interval;
        // 最后更新时间为当前
        this.lastResetTime = System.currentTimeMillis();

        // 创建种子计数器
        this.token = buildLongAdder(rate);
    }

    /**
     * 是否超出当前周期的种子数
     * @return
     */
    public boolean isAllowable() {

        long now = System.currentTimeMillis();

        // 如果当前毫秒超出了下一个周期，那么则重置种子数，并记录更新时间
        if (now > lastResetTime + interval) {
            token = buildLongAdder(rate);
            lastResetTime = now;
        }

        // 如果总数小于0，代表没有了种子
        if (token.sum() < 0) {
            return false;
        }

        // 种子数减一
        token.decrement();
        return true;
    }

    public long getInterval() {
        return interval;
    }


    public int getRate() {
        return rate;
    }


    long getLastResetTime() {
        return lastResetTime;
    }

    long getToken() {
        return token.sum();
    }

    @Override
    public String toString() {
        return new StringBuilder(32).append("StatItem ")
                .append("[name=").append(name).append(", ")
                .append("rate = ").append(rate).append(", ")
                .append("interval = ").append(interval).append("]")
                .toString();
    }

    /**
     *  构建LongAdder, 这个东西是以空间换取时间的一种做法，比AtomicLong更加的快速，更加适合大并发量
     * @param rate 初始值
     * @return
     */
    private LongAdder buildLongAdder(int rate) {
        LongAdder adder = new LongAdder();
        adder.add(rate);
        return adder;
    }

}
