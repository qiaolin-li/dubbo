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
package org.apache.dubbo.registry.zookeeper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.zookeeper.ChildListener;
import org.apache.dubbo.remoting.zookeeper.StateListener;
import org.apache.dubbo.remoting.zookeeper.ZookeeperClient;
import org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter;
import org.apache.dubbo.rpc.RpcException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_SEPARATOR;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_SEPARATOR;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONSUMERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DEFAULT_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.common.constants.RegistryConstants.PROVIDERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.ROUTERS_CATEGORY;

/**
 * ZookeeperRegistry
 *
 * 操作如下图，F2查看 <br/>
 * <img src="../../../../../../resources/comment/01.jpg">   <br/>
 * 提供者启动时：向 /dubbo/com.qiaolin.BaseService/providers 目录写下自己的url地址    <br/>
 * 消费者启动时：订阅 /dubbo/com.qiaolin.BaseService/providers 目录下提供者的url地址，
 *      并向 /dubbo/com.qiaolin.BaseService/comsumers 目录下写下自己的url地址       <br/>
 * 监控中心启动时： 订阅 /dubbo/com.qiaolin.BaseService 目录下的所有提供者和消费者url地址    <br/>
 *
 * 在上图中，我们可以看到zookeeper的节点层级，自下而上的是：
 * <ul>
 *     <li> Root层：根目录，可通过<dubbo:registry group="dubbo"/>来配置，默认值为dubbo </li>
 *     <li> Service层：服务接口的全名称，为了分组 </li>
 *     <li> Type层：服务接口下不同的url, 分别为 providers(提供者), comsumers(消费者),
 *              routes(路由规则列表), configurations(配置规则列表) </li>
 *     <li> url层：根据不同的规则，放置的不同类型的url, 本质上url中也含有 category参数，
 *              但是zookeeper是基于节点目录订阅的，所以上面分了一个Type目录 </li>
 * </ul>
 * 最后：服务消费者启动成功后，不仅仅订阅了‘providers’分类，还订阅了 routes、configurations分类
 */
public class ZookeeperRegistry extends FailbackRegistry {

    private final static Logger logger = LoggerFactory.getLogger(ZookeeperRegistry.class);

    // 默认根节点
    private final static String DEFAULT_ROOT = "dubbo";

    // 根节点名称
    private final String root;

    // 任意服务 TODO 等下再回顾下这是个什么东西
    private final Set<String> anyServices = new ConcurrentHashSet<>();

    // 订阅 url对应的监听器， NotifyListener 和 ChildListener 监听器的映射关系，
    // Zookeeper并不支持NotifyListener，所以使用了ChildListener来适配
    private final ConcurrentMap<URL, ConcurrentMap<NotifyListener, ChildListener>> zkListeners = new ConcurrentHashMap<>();

    // zookeeper客户端
    private final ZookeeperClient zkClient;

    public ZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        // 注册中心url给父类构造器
        super(url);

        // 检验 host正确性，host不能为 0.0.0.0 或者 anyHost不能等于false
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }

        // 获取根目录 可通过 <dubbo:registry group="hehe" /> 来指定
        String group = url.getParameter(GROUP_KEY, DEFAULT_ROOT);

        // 保证根目录以/开头
        if (!group.startsWith(PATH_SEPARATOR)) {
            group = PATH_SEPARATOR + group;
        }

        this.root = group;

        // 获取zookeeper链接
        zkClient = zookeeperTransporter.connect(url);

        // 增加zookeeper状态监听器，方便做出对应的操作
        zkClient.addStateListener((state) -> {

            // zk断线重连时
            if (state == StateListener.RECONNECTED) {
                logger.warn("Trying to fetch the latest urls, in case there're provider changes during connection loss.\n" +
                        " Since ephemeral ZNode will not get deleted for a connection lose, " +
                        "there's no need to re-register url of this instance.");

                // 重新发起订阅，或者说重试订阅
                // 这里为什么不用重新发起注册呢？因为出现短线重连时，一般都没有清理掉零时节点 TODO 不知道是这个理解不
                ZookeeperRegistry.this.fetchLatestAddresses();
            } else if (state == StateListener.NEW_SESSION_CREATED) {
                logger.warn("Trying to re-register urls and re-subscribe listeners of this instance to registry...");
                try {
                    // 出现 新session创建时，需要重新注册和订阅
                    ZookeeperRegistry.this.recover();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            } else if (state == StateListener.SESSION_LOST) {

                logger.warn("Url of this instance will be deleted from registry soon. " +
                        "Dubbo client will try to re-register once a new session is created.");
            } else if (state == StateListener.SUSPENDED) {

            } else if (state == StateListener.CONNECTED) {

            }
        });
    }

    @Override
    public boolean isAvailable() {
        // zk是否可用
        return zkClient.isConnected();
    }

    @Override
    public void destroy() {
        super.destroy();
        try {
            // 销毁zk链接
            zkClient.close();
        } catch (Exception e) {
            logger.warn("Failed to close zookeeper client " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void doRegister(URL url) {
        try {
            // 注册节点，根据参数 dynamic来判断是否是一个零时节点
            zkClient.create(toUrlPath(url), url.getParameter(DYNAMIC_KEY, true));
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void doUnregister(URL url) {
        try {
            // 删除节点
            zkClient.delete(toUrlPath(url));
        } catch (Throwable e) {
            throw new RpcException("Failed to unregister " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void doSubscribe(final URL url, final NotifyListener listener) {
        boolean b = false;
        if(b){
            URL url1 = url.setServiceInterface(ANY_VALUE);
            this.doSubscribe(url1, new NotifyListener() {
                @Override
                public void notify(List<URL> urls) {
                    System.out.println(urls);
                }
            });
        }
        try {
            // 如果是订阅所有接口的数据，一般监控中心才用这个
            if (ANY_VALUE.equals(url.getServiceInterface())) {

                // 根节点名称
                String root = toRootPath();

                // 获取url的监听器映射关系
                ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);

                // 如果不存在映射关系，加一个
                if (listeners == null) {
                    zkListeners.putIfAbsent(url, new ConcurrentHashMap<>());
                    listeners = zkListeners.get(url);
                }

                // 获取当前 NotifyListener对应的 ChildListener
                ChildListener zkListener = listeners.get(listener);

                // 不存在就创建
                if (zkListener == null) {
                    listeners.putIfAbsent(listener, (parentPath, currentChilds) -> {

                        // 变更的数据， 一般是url哈
                        for (String child : currentChilds) {
                            // url解码
                            child = URL.decode(child);

                            // 如果不在已存在的服务列表中，那么去订阅这个接口
                             if (!anyServices.contains(child)) {
                                anyServices.add(child);
                                subscribe(url.setPath(child).addParameters(INTERFACE_KEY, child,
                                        Constants.CHECK_KEY, String.valueOf(false)), listener);
                            }
                        }
                    });
                    zkListener = listeners.get(listener);
                }
                zkClient.create(root, false);
                List<String> services = zkClient.addChildListener(root, zkListener);
                if (CollectionUtils.isNotEmpty(services)) {
                    for (String service : services) {
                        service = URL.decode(service);
                        anyServices.add(service);
                        subscribe(url.setPath(service).addParameters(INTERFACE_KEY, service,
                                Constants.CHECK_KEY, String.valueOf(false)), listener);
                    }
                }
            } else {
                List<URL> urls = new ArrayList<>();
                for (String path : toCategoriesPath(url)) {
                    ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                    if (listeners == null) {
                        zkListeners.putIfAbsent(url, new ConcurrentHashMap<>());
                        listeners = zkListeners.get(url);
                    }
                    ChildListener zkListener = listeners.get(listener);
                    if (zkListener == null) {
                        listeners.putIfAbsent(listener, (parentPath, currentChilds) -> ZookeeperRegistry.this.notify(url, listener, toUrlsWithEmpty(url, parentPath, currentChilds)));
                        zkListener = listeners.get(listener);
                    }
                    zkClient.create(path, false);
                    List<String> children = zkClient.addChildListener(path, zkListener);
                    if (children != null) {
                        urls.addAll(toUrlsWithEmpty(url, path, children));
                    }
                }
                notify(url, listener, urls);
            }
        } catch (Throwable e) {
            throw new RpcException("Failed to subscribe " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void doUnsubscribe(URL url, NotifyListener listener) {
        ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
        if (listeners != null) {
            ChildListener zkListener = listeners.get(listener);
            if (zkListener != null) {
                if (ANY_VALUE.equals(url.getServiceInterface())) {
                    String root = toRootPath();
                    zkClient.removeChildListener(root, zkListener);
                } else {
                    for (String path : toCategoriesPath(url)) {
                        zkClient.removeChildListener(path, zkListener);
                    }
                }
            }
        }
    }

    @Override
    public List<URL> lookup(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("lookup url == null");
        }
        try {
            List<String> providers = new ArrayList<>();
            for (String path : toCategoriesPath(url)) {
                List<String> children = zkClient.getChildren(path);
                if (children != null) {
                    providers.addAll(children);
                }
            }
            return toUrlsWithoutEmpty(url, providers);
        } catch (Throwable e) {
            throw new RpcException("Failed to lookup " + url + " from zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    private String toRootDir() {
        if (root.equals(PATH_SEPARATOR)) {
            return root;
        }
        return root + PATH_SEPARATOR;
    }

    private String toRootPath() {
        return root;
    }

    private String toServicePath(URL url) {
        String name = url.getServiceInterface();
        if (ANY_VALUE.equals(name)) {
            return toRootPath();
        }
        return toRootDir() + URL.encode(name);
    }

    private String[] toCategoriesPath(URL url) {
        String[] categories;
        if (ANY_VALUE.equals(url.getParameter(CATEGORY_KEY))) {
            categories = new String[]{PROVIDERS_CATEGORY, CONSUMERS_CATEGORY, ROUTERS_CATEGORY, CONFIGURATORS_CATEGORY};
        } else {
            categories = url.getParameter(CATEGORY_KEY, new String[]{DEFAULT_CATEGORY});
        }
        String[] paths = new String[categories.length];
        for (int i = 0; i < categories.length; i++) {
            paths[i] = toServicePath(url) + PATH_SEPARATOR + categories[i];
        }
        return paths;
    }

    private String toCategoryPath(URL url) {
        return toServicePath(url) + PATH_SEPARATOR + url.getParameter(CATEGORY_KEY, DEFAULT_CATEGORY);
    }

    private String toUrlPath(URL url) {
        return toCategoryPath(url) + PATH_SEPARATOR + URL.encode(url.toFullString());
    }

    private List<URL> toUrlsWithoutEmpty(URL consumer, List<String> providers) {
        List<URL> urls = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(providers)) {
            for (String provider : providers) {
                provider = URL.decode(provider);
                if (provider.contains(PROTOCOL_SEPARATOR)) {
                    URL url = URL.valueOf(provider);
                    if (UrlUtils.isMatch(consumer, url)) {
                        urls.add(url);
                    }
                }
            }
        }
        return urls;
    }

    private List<URL> toUrlsWithEmpty(URL consumer, String path, List<String> providers) {
        List<URL> urls = toUrlsWithoutEmpty(consumer, providers);
        if (urls == null || urls.isEmpty()) {
            int i = path.lastIndexOf(PATH_SEPARATOR);
            String category = i < 0 ? path : path.substring(i + 1);
            URL empty = URLBuilder.from(consumer)
                    .setProtocol(EMPTY_PROTOCOL)
                    .addParameter(CATEGORY_KEY, category)
                    .build();
            urls.add(empty);
        }
        return urls;
    }

    /**
     * When zookeeper connection recovered from a connection loss, it need to fetch the latest provider list.
     * re-register watcher is only a side effect and is not mandate.
     */
    private void fetchLatestAddresses() {
        // subscribe
        Map<URL, Set<NotifyListener>> recoverSubscribed = new HashMap<>(getSubscribed());
        if (!recoverSubscribed.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Fetching the latest urls of " + recoverSubscribed.keySet());
            }
            for (Map.Entry<URL, Set<NotifyListener>> entry : recoverSubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    addFailedSubscribed(url, listener);
                }
            }
        }
    }

}
