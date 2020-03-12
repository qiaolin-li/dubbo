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
package org.apache.dubbo.config.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Parameter
 * TODO 标致类的参数需要被获取？？？？ 2020年3月12日
 * @author
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Parameter {

    /**
     *  属性的键（别名）
     */
    String key() default "";

    /**
     *  获取属性的方法返回值不能为空，或者长度为0
     */
    boolean required() default false;

    /**
     * TODO 是否忽略?? 不注这个注解不完事了？忽略啥？？？
     */
    boolean excluded() default false;

    /**
     *  是否转义，将数据编码
     */
    boolean escaped() default false;

    /**
     *  TODO 是否为属性？ 目前用于事件通知？？http://dubbo.apache.org/zh-cn/docs/user/demos/events-notify.html
     *  反正我是没看出来
     */
    boolean attribute() default false;

    /**
     *  当存在相当key的参数时，是否要将他们拼接起来，
     *  例如 server.filter=aFilter ， server.filter=bFilter,如果这个参数为true,那么最终的参数为 server.filter=aFilter, bFilter
     */
    boolean append() default false;

    /**
     * if {@link #key()} is specified, it will be used as the key for the annotated property when generating url.
     * by default, this key will also be used to retrieve the config value:
     * <pre>
     * {@code
     *  class ExampleConfig {
     *      // Dubbo will try to get "dubbo.example.alias_for_item=xxx" from .properties, if you want to use the original property
     *      // "dubbo.example.item=xxx", you need to set useKeyAsProperty=false.
     *      @Parameter(key = "alias_for_item")
     *      public getItem();
     *  }
     * }
     *
     * </pre>
     *
     * 这个值为true,则表示希望使用没有key所指定的属性时尝试以自己的属性值名获取属性，例如上面这个代码 key为 alias_for_item，
     * 如果没有这个属性，那么他会尝试 item获取属性
     */
    boolean useKeyAsProperty() default true;

}