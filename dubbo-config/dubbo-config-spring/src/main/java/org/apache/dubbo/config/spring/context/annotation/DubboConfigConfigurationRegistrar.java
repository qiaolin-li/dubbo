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
package org.apache.dubbo.config.spring.context.annotation;

import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.spring.beans.factory.annotation.DubboConfigAliasPostProcessor;
import org.apache.dubbo.config.spring.context.config.NamePropertyDefaultValueDubboConfigBeanCustomizer;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import static com.alibaba.spring.util.AnnotatedBeanDefinitionRegistryUtils.registerBeans;
import static com.alibaba.spring.util.BeanRegistrar.registerInfrastructureBean;
import static org.apache.dubbo.config.spring.context.config.NamePropertyDefaultValueDubboConfigBeanCustomizer.BEAN_NAME;

/**
 * Dubbo {@link AbstractConfig Config} {@link ImportBeanDefinitionRegistrar register}, which order can be configured
 * Dubbo配置Bean 注册器
 * @see EnableDubboConfig
 * @see DubboConfigConfiguration
 * @see Ordered
 * @since 2.5.8
 */
public class DubboConfigConfigurationRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableDubboConfig.class.getName()));

        // 单配置与多配置，参见：https://segmentfault.com/a/1190000012661402#articleHeader4
        boolean multiple = attributes.getBoolean("multiple");

        // 单配置绑定
        registerBeans(registry, DubboConfigConfiguration.Single.class);

        // 多配置 Since 2.6.6 https://github.com/apache/dubbo/issues/3193
        if (multiple) {
            registerBeans(registry, DubboConfigConfiguration.Multiple.class);
        }

        // 注册配置别名后置处理器
        registerDubboConfigAliasPostProcessor(registry);

        // 注册name属性不存在时，使用beanName设置
        registerDubboConfigBeanCustomizers(registry);

    }

    /**
     * 配置Bean如果name属性没有值，那么将beanName设置为name
     * @param registry
     */
    private void registerDubboConfigBeanCustomizers(BeanDefinitionRegistry registry) {
        registerInfrastructureBean(registry, BEAN_NAME, NamePropertyDefaultValueDubboConfigBeanCustomizer.class);
    }

    /**
     * Register {@link DubboConfigAliasPostProcessor}
     * 这个PostProcessor用于给配置类取个别名
     * @param registry {@link BeanDefinitionRegistry}
     * @since 2.7.4 [Feature] https://github.com/apache/dubbo/issues/5093
     */
    private void registerDubboConfigAliasPostProcessor(BeanDefinitionRegistry registry) {
        registerInfrastructureBean(registry, DubboConfigAliasPostProcessor.BEAN_NAME, DubboConfigAliasPostProcessor.class);
    }

}
