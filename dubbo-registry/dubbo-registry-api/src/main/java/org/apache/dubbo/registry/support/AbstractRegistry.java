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
package org.apache.dubbo.registry.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.FILE_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.ACCEPTS_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.DEFAULT_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.registry.Constants.REGISTRY_FILESAVE_SYNC_KEY;

/**
 * AbstractRegistry. (SPI, Prototype, ThreadSafe)
 * 抽象的注册中心
 * 做的工作如下：
 * 1、记录了已订阅的URL集合
 * 2、记录了已注册的URL集合
 * 3、维持了订阅URL和他订阅的urls,已经分组了，方便写入properties和本地缓存文件
 * 4、恢复的方法，主要是恢复注册和订阅，防止因为网络抖动啥的导致订阅失败
 * 5、基本的销毁方法，取消订阅、取消注册
 */
public abstract class AbstractRegistry implements Registry {

    // URL address separator, used in file cache, service provider URL separation
    // URL地址分隔符，使用场景：文件缓存，服务提供者url分割
    private static final char URL_SEPARATOR = ' ';

    // URL address separated regular expression for parsing the service provider URL list in the file cache
    // URL地址分割表达式， 用来解析文件缓存中服务器提供着Url列表
    private static final String URL_SPLIT = "\\s+";

    // Max times to retry to save properties to local cache file
    // 保存配置到本地缓存的最大重试次数
    private static final int MAX_RETRY_TIMES_SAVE_PROPERTIES = 3;

    // Log output
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // Local disk cache, where the special key value.registries records the list of registry centers,
    // and the others are the list of notified service providers
    // 本地磁盘的缓存， TODO 后来再看有啥用
    // 1、其中特殊的key值 .registries 记录注册中心的列表
    // 2、其他的的为服务提供者列表
    // 其实这个一个键值对为一个消费者url的serviceKey 对应一个他订阅的所有url的完整字符串拼接
    private final Properties properties = new Properties();

    // File cache timing writing
    // 文件缓存定时写入执行器
    private final ExecutorService registryCacheExecutor = Executors.newFixedThreadPool(1, new NamedThreadFactory("DubboSaveRegistryCache", true));

    // Is it synchronized to save the file
    /// 保存到文件时是否是同步的
    private final boolean syncSaveFile;

    //  最后的数据版本号
    private final AtomicLong lastCacheChanged = new AtomicLong();

    // 保存配置重试的次数
    private final AtomicInteger savePropertiesRetryTimes = new AtomicInteger();

    // 已注册的url集合，注册的url不仅仅可以是服务提供者，也可能是服务消费者  TODO 什么是已注册的url呢？提供者和消费者？？
   private final Set<URL> registered = new ConcurrentHashSet<>();

    // 订阅URL的监听器集合
    private final ConcurrentMap<URL, Set<NotifyListener>> subscribed = new ConcurrentHashMap<>();

    // URL和他订阅的url分组，方便写入 properties中，也算是一个本地缓存
    // ConcurrentMap<消费者URL, Map<分类, 分类下的url集合>>
    // 分类 providers、consumers、routes、configurators TODO 实际无消费者，因为没有人订阅消费者
    private final ConcurrentMap<URL, Map<String, List<URL>>> notified = new ConcurrentHashMap<>();

    // 注册中心url
    private URL registryUrl;

    // Local disk cache file
    // 本地磁盘缓存文件
    private File file;

    public AbstractRegistry(URL url) {
        // 保存注册中心url
        setUrl(url);

        // Start file save timer
        // 启动文件保存定时器
        syncSaveFile = url.getParameter(REGISTRY_FILESAVE_SYNC_KEY, false);

        // 默认的缓存文件地址  %user.home%/.dubbo/dubbo-registry-${application}-${ip-port}.cache
        String defaultFilename = System.getProperty("user.home") + "/.dubbo/dubbo-registry-" + url.getParameter(APPLICATION_KEY) + "-" + url.getAddress().replaceAll(":", "-") + ".cache";

        // 获取缓存文件地址
        String filename = url.getParameter(FILE_KEY, defaultFilename);

        // 创建文件父级目录
        // 我有一个疑问，为什么只创建父级目录，其实如果是为了恢复缓存中的数据，那么也是恢复上一次写入的缓存啊！
        // 所以如果没有这个文件就代表没有缓存啊！
        File file = null;
        if (ConfigUtils.isNotEmpty(filename)) {
            file = new File(filename);
            if (!file.exists() && file.getParentFile() != null && !file.getParentFile().exists()) {
                if (!file.getParentFile().mkdirs()) {
                    throw new IllegalArgumentException("Invalid registry cache file " + file + ", cause: Failed to create directory " + file.getParentFile() + "!");
                }
            }
        }

        //保存文件引用
        this.file = file;

        // When starting the subscription center,
        // we need to read the local cache file for future Registry fault tolerance processing.
        // 启动注册中心时，我们需要读取本地缓存文件，以便将来进行注册表容错
        loadProperties();

        // TODO 为什么要监听， subscribed 都是空的吧
        notify(url.getBackupUrls());
    }

    /**
     * 如果urls为空，则默认添加一个空协议的url放入到集合中
     * TODO 这有啥用呢？为啥要用个空协议url呢？
     * @param url
     * @param urls
     * @return
     */
    protected static List<URL> filterEmpty(URL url, List<URL> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            List<URL> result = new ArrayList<>(1);
            result.add(url.setProtocol(EMPTY_PROTOCOL));
            return result;
        }
        return urls;
    }

    @Override
    public URL getUrl() {
        return registryUrl;
    }

    protected void setUrl(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("registry url == null");
        }
        this.registryUrl = url;
    }

    public Set<URL> getRegistered() {
        return Collections.unmodifiableSet(registered);
    }

    public Map<URL, Set<NotifyListener>> getSubscribed() {
        return Collections.unmodifiableMap(subscribed);
    }

    public Map<URL, Map<String, List<URL>>> getNotified() {
        return Collections.unmodifiableMap(notified);
    }

    public File getCacheFile() {
        return file;
    }

    public Properties getCacheProperties() {
        return properties;
    }

    public AtomicLong getLastCacheChanged() {
        return lastCacheChanged;
    }

    /**
     * 将缓存url保存至缓存文件
     * @param version
     */
    public void doSaveProperties(long version) {
        // 当前版本小于最后版本，则丢弃这次更新
        if (version < lastCacheChanged.get()) {
            return;
        }
        // 没有缓存文件不更新
        if (file == null) {
            return;
        }

        try {
            // 以文件当锁
            File lockfile = new File(file.getAbsolutePath() + ".lock");

            // 文件不存在时创建
            if (!lockfile.exists()) {
                lockfile.createNewFile();
            }

            // 获取文件锁
            try (RandomAccessFile raf = new RandomAccessFile(lockfile, "rw");
                 FileChannel channel = raf.getChannel()) {
                FileLock lock = channel.tryLock();
                // 没有获取到锁，抛出异常
                if (lock == null) {
                    throw new IOException("Can not lock the registry cache file " + file.getAbsolutePath() + ", ignore and retry later, maybe multi java process use the file, please config: dubbo.registry.file=xxx.properties");
                }

                try {
                    // 缓存文件不存在时创建
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    // 将配置写入到缓存文件
                    try (FileOutputStream outputFile = new FileOutputStream(file)) {
                        properties.store(outputFile, "Dubbo Registry Cache");
                    }
                } finally {
                    // 释放文件锁
                    lock.release();
                }
            }
        } catch (Throwable e) {
            // 保存配置失败重试次数+1
            savePropertiesRetryTimes.incrementAndGet();

            // 当失败次数过多时会打印警告信息，并且重置失败次数
            if (savePropertiesRetryTimes.get() >= MAX_RETRY_TIMES_SAVE_PROPERTIES) {
                logger.warn("Failed to save registry cache file after retrying " + MAX_RETRY_TIMES_SAVE_PROPERTIES + " times, cause: " + e.getMessage(), e);
                savePropertiesRetryTimes.set(0);
                return;
            }

            // 如果版本已经更新，那么重置失败次数，否则继续重试
            if (version < lastCacheChanged.get()) {
                savePropertiesRetryTimes.set(0);
                return;
            } else {
                registryCacheExecutor.execute(new SaveProperties(lastCacheChanged.incrementAndGet()));
            }
            logger.warn("Failed to save registry cache file, will retry, cause: " + e.getMessage(), e);
        }
    }

    private void loadProperties() {

        // 如果文件存在，则去读取
        if (file != null && file.exists()) {
            InputStream in = null;
            try {
                in = new FileInputStream(file);

                // 将文件的内容读取到 properties中
                properties.load(in);
                if (logger.isInfoEnabled()) {
                    logger.info("Load registry cache file " + file + ", data: " + properties);
                }
            } catch (Throwable e) {
                logger.warn("Failed to load registry cache file " + file, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     *  获取缓存的url
     * @param url
     * @return
     */
    public List<URL> getCacheUrls(URL url) {
        // 从配置中获取
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();

            // key必须是url的serviceKey，并且value有值
            if (key != null && key.length() > 0 && key.equals(url.getServiceKey())
                    && (Character.isLetter(key.charAt(0)) || key.charAt(0) == '_')
                    && value != null && value.length() > 0) {

                // 值以空格分隔
                String[] arr = value.trim().split(URL_SPLIT);
                List<URL> urls = new ArrayList<>();

                // 将每一个字符串转换为url对象
                for (String u : arr) {
                    urls.add(URL.valueOf(u));
                }
                return urls;
            }
        }
        return null;
    }

    @Override
    public List<URL> lookup(URL url) {
        List<URL> result = new ArrayList<>();
        // 获取url下的所有类型所有url  Map<类型, url集合>
        Map<String, List<URL>> notifiedUrls = getNotified().get(url);

        // 如果url存在订阅的url集合
        if (notifiedUrls != null && notifiedUrls.size() > 0) {
            for (List<URL> urls : notifiedUrls.values()) {
                for (URL u : urls) {
                    // 如果不是空协议，啊么就保存起来
                    if (!EMPTY_PROTOCOL.equals(u.getProtocol())) {
                        result.add(u);
                    }
                }
            }
        } else {
            // 否则使用订阅器去订阅一次
            final AtomicReference<List<URL>> reference = new AtomicReference<>();

            // 这个订阅器收到通知时会直接将值设置给 reference TODO 其实我觉得这个监听器用完之后应该需要删除
            NotifyListener listener = reference::set;

            // 开始订阅
            subscribe(url, listener); // Subscribe logic guarantees the first notify to return

            // 获取订阅的值
            List<URL> urls = reference.get();
            if (CollectionUtils.isNotEmpty(urls)) {
                for (URL u : urls) {
                    // 过滤空协议
                    if (!EMPTY_PROTOCOL.equals(u.getProtocol())) {
                        result.add(u);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void register(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("register url == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Register: " + url);
        }
        // 将url放入到已注册url中
        registered.add(url);
    }

    @Override
    public void unregister(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("unregister url == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Unregister: " + url);
        }

        // 将url从已注册的url中删除
        registered.remove(url);
    }

    @Override
    public void subscribe(URL url, NotifyListener listener) {
        if (url == null) {
            throw new IllegalArgumentException("subscribe url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("subscribe listener == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Subscribe: " + url);
        }

        // 防止并发订阅，导致监听器集合被覆盖
        Set<NotifyListener> listeners = subscribed.computeIfAbsent(url, n -> new ConcurrentHashSet<>());

        // 将监听器放到集合中
        listeners.add(listener);
    }

    @Override
    public void unsubscribe(URL url, NotifyListener listener) {
        if (url == null) {
            throw new IllegalArgumentException("unsubscribe url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("unsubscribe listener == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Unsubscribe: " + url);
        }

        // 从url对应的监听器集合中移除监听器
        Set<NotifyListener> listeners = subscribed.get(url);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    protected void recover() throws Exception {

        // 获得所有已注册的url
        Set<URL> recoverRegistered = new HashSet<>(getRegistered());

        if (!recoverRegistered.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover register url " + recoverRegistered);
            }

            // 开始恢复注册
            for (URL url : recoverRegistered) {
                register(url);
            }
        }

        // 所有已订阅的url,开始恢复监听
        Map<URL, Set<NotifyListener>> recoverSubscribed = new HashMap<>(getSubscribed());
        if (!recoverSubscribed.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover subscribe url " + recoverSubscribed.keySet());
            }

            for (Map.Entry<URL, Set<NotifyListener>> entry : recoverSubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    // 恢复监听
                    subscribe(url, listener);
                }
            }
        }
    }

    /**
     * 当url发生改变时，通知对应的订阅者
     * @param urls 发生改变的urls
     */
    protected void notify(List<URL> urls) {

        // 如果没有发生改变，则返回
        if (CollectionUtils.isEmpty(urls)) {
            return;
        }

        // 循环所有已订阅的url和监听器
        for (Map.Entry<URL, Set<NotifyListener>> entry : getSubscribed().entrySet()) {
            // 获取订阅的url
            URL url = entry.getKey();

            // 只要一个匹配，那么就是他想要的，TODO 已知一次通知只会是一种类型的
            if (!UrlUtils.isMatch(url, urls.get(0))) {
                continue;
            }

            // 如果配置，那么则去通知他所有的监听器
            Set<NotifyListener> listeners = entry.getValue();
            if (listeners != null) {
                for (NotifyListener listener : listeners) {
                    try {
                        // 通知对应的监听器，当urls为空时，某人给他一个集合，里面只有一个元素，就是一个空协议的url
                        notify(url, listener, filterEmpty(url, urls));
                    } catch (Throwable t) {
                        logger.error("Failed to notify registry event, urls: " + urls + ", cause: " + t.getMessage(), t);
                    }
                }
            }
        }
    }

    /**
     * Notify changes from the Provider side.
     *
     * @param url      consumer side url
     * @param listener listener
     * @param urls     provider latest urls
     */
    protected void notify(URL url, NotifyListener listener, List<URL> urls) {
        if (url == null) {
            throw new IllegalArgumentException("notify url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("notify listener == null");
        }

        // 如果urls为空，并且url的serviceInterface不为 * ，则返回
        if ((CollectionUtils.isEmpty(urls)) && !ANY_VALUE.equals(url.getServiceInterface())) {
            logger.warn("Ignore empty notify urls for subscribe url " + url);
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info("Notify urls for subscribe url " + url + ", urls: " + urls);
        }

        // keep every provider's category.
        // 以 category 分组
        Map<String, List<URL>> result = new HashMap<>();
        for (URL u : urls) {
            if (UrlUtils.isMatch(url, u)) {
                String category = u.getParameter(CATEGORY_KEY, DEFAULT_CATEGORY);
                List<URL> categoryList = result.computeIfAbsent(category, k -> new ArrayList<>());
                categoryList.add(u);
            }
        }

        if (result.size() == 0) {
            return;
        }


        Map<String, List<URL>> categoryNotified = notified.computeIfAbsent(url, u -> new ConcurrentHashMap<>());
        for (Map.Entry<String, List<URL>> entry : result.entrySet()) {

            // 当前的分类 provider、consumer、configuration、router
            String category = entry.getKey();

            // 当前分类的 url集合
            List<URL> categoryList = entry.getValue();

            categoryNotified.put(category, categoryList);

            // 像监听器通知一个分类的所有url集合
            listener.notify(categoryList);

            // We will update our cache file after each notification.
            // When our Registry has a subscribe failure due to network jitter,
            // we can return at least the existing cache URL.
            // 我们在通知之后总是会去更新缓存文件，当我们的注册表由于网络抖动导致订阅失败时，我们可以返回最后存在的缓存URL
            saveProperties(url);
        }
    }

    /**
     *  将订阅的urls保存到配置中
     * @param url 消费者url
     */
    private void saveProperties(URL url) {

        // 文件没有，不保存
        if (file == null) {
            return;
        }

        try {
            StringBuilder buf = new StringBuilder();
            // 获取当前消费者url订阅的所有类型的所有url
            // Map<URL类型,URL集合>
            Map<String, List<URL>> categoryNotified = notified.get(url);

            if (categoryNotified != null) {
                // 将所有的url拼接起来，不分类型
                for (List<URL> us : categoryNotified.values()) {
                    for (URL u : us) {
                        if (buf.length() > 0) {
                            buf.append(URL_SEPARATOR);
                        }
                        buf.append(u.toFullString());
                    }
                }
            }

            // 以服务key来保存所有订阅的url
            properties.setProperty(url.getServiceKey(), buf.toString());

            // 获取最后的版本号
            long version = lastCacheChanged.incrementAndGet();

            // 异步还是同步保存版本号
            if (syncSaveFile) {
                doSaveProperties(version);
            } else {
                registryCacheExecutor.execute(new SaveProperties(version));
            }
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }

    /**
     *  销毁注册中心的时候做的事
     *  1、取消已注册并且dynamic参数为true的url，TODO 这里是不是就是提供者服务关闭时取消注册，在zookeeper上下线吧
     *  2、取消进行订阅的监听器
     */
    @Override
    public void destroy() {
        if (logger.isInfoEnabled()) {
            logger.info("Destroy registry:" + getUrl());
        }
        Set<URL> destroyRegistered = new HashSet<>(getRegistered());
        if (!destroyRegistered.isEmpty()) {
            for (URL url : new HashSet<>(getRegistered())) {
                if (url.getParameter(DYNAMIC_KEY, true)) {
                    try {
                        unregister(url);
                        if (logger.isInfoEnabled()) {
                            logger.info("Destroy unregister url " + url);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to unregister url " + url + " to registry " + getUrl() + " on destroy, cause: " + t.getMessage(), t);
                    }
                }
            }
        }
        Map<URL, Set<NotifyListener>> destroySubscribed = new HashMap<>(getSubscribed());
        if (!destroySubscribed.isEmpty()) {
            for (Map.Entry<URL, Set<NotifyListener>> entry : destroySubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    try {
                        unsubscribe(url, listener);
                        if (logger.isInfoEnabled()) {
                            logger.info("Destroy unsubscribe url " + url);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to unsubscribe url " + url + " to registry " + getUrl() + " on destroy, cause: " + t.getMessage(), t);
                    }
                }
            }
        }
    }

    /**
     * TODO 注册中心能够接受的协议？
     * 1、如果注册中心的 accepts参数为空，则返回true
     * 2、如果 accepts不为true,则以逗号拆分，进行匹配 urlToRegistry的 protocol
     * 例如：
     *   urlToRegistry = xxx://localhost:8080    registryUrl = zookeeper://localhost:2181?accepts=          匹配
     *   urlToRegistry = xxx://localhost:8080    registryUrl = zookeeper://localhost:2181?accepts=xxx       匹配
     *   urlToRegistry = xxx://localhost:8080    registryUrl = zookeeper://localhost:2181?accepts=zzz      不匹配
     *
     * @param urlToRegistry
     * @return
     */
    protected boolean acceptable(URL urlToRegistry) {
        String pattern = registryUrl.getParameter(ACCEPTS_KEY);
        if (StringUtils.isEmpty(pattern)) {
            return true;
        }

        return Arrays.stream(COMMA_SPLIT_PATTERN.split(pattern))
                .anyMatch(p -> p.equalsIgnoreCase(urlToRegistry.getProtocol()));
    }

    @Override
    public String toString() {
        return getUrl().toString();
    }


    /**
     *  保存配置任务
     */
    private class SaveProperties implements Runnable {
        private long version;

        private SaveProperties(long version) {
            this.version = version;
        }

        @Override
        public void run() {
            doSaveProperties(version);
        }
    }

}
