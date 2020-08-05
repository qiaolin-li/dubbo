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
package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.support.AccessLogData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.Constants.ACCESS_LOG_KEY;

/**
 * Record access log for the service.
 * 记录服务的访问日志
 * 详见：http://dubbo.apache.org/zh-cn/docs/user/demos/accesslog.html
 * <p>
 * Logger key is <code><b>dubbo.accesslog</b></code>.
 * In order to configure access log appear in the specified appender only, additivity need to be configured in log4j's
 * config file, for example:
 * <code>
 * <pre>
 * &lt;logger name="<b>dubbo.accesslog</b>" <font color="red">additivity="false"</font>&gt;
 *    &lt;level value="info" /&gt;
 *    &lt;appender-ref ref="foo" /&gt;
 * &lt;/logger&gt;
 * </pre></code>
 */
@Activate(group = PROVIDER, value = ACCESS_LOG_KEY)
public class AccessLogFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(AccessLogFilter.class);

    /**
     *  日志key
     */
    private static final String LOG_KEY = "dubbo.accesslog";

    /**
     * 日志缓冲区大小
     */
    private static final int LOG_MAX_BUFFER = 5000;

    /**
     *  日志输出间隔
     */
    private static final long LOG_OUTPUT_INTERVAL = 5000;

    /**
     *  文件名日期格式
     */
    private static final String FILE_DATE_FORMAT = "yyyyMMdd";

    // It's safe to declare it as singleton since it runs on single thread only
    // 因为他只在单线程中使用，所以它被声明为单实例的
    private static final DateFormat FILE_NAME_FORMATTER = new SimpleDateFormat(FILE_DATE_FORMAT);

    private static final Map<String, Set<AccessLogData>> LOG_ENTRIES = new ConcurrentHashMap<>();

    /**
     *  写日志的线程
     */
    private static final ScheduledExecutorService LOG_SCHEDULED = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Dubbo-Access-Log", true));

    /**
     * Default constructor initialize demon thread for writing into access log file with names with access log key
     * defined in url <b>accesslog</b>
     */
    public AccessLogFilter() {
        // 开启一个写日志到文件的任务
        LOG_SCHEDULED.scheduleWithFixedDelay(this::writeLogToFile, LOG_OUTPUT_INTERVAL, LOG_OUTPUT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * This method logs the access log for service method invocation call.
     *
     * @param invoker service
     * @param inv     Invocation service method.
     * @return Result from service method.
     * @throws RpcException
     */
    @Override
    public Result invoke(Invoker<?> invoker, Invocation inv) throws RpcException {
        try {
            // 如果开启访问日志
            String accessLogKey = invoker.getUrl().getParameter(ACCESS_LOG_KEY);
            if (ConfigUtils.isNotEmpty(accessLogKey)) {
                // 构建访问日志对象
                AccessLogData logData = buildAccessLogData(invoker, inv);

                // 记录日志
                log(accessLogKey, logData);
            }
        } catch (Throwable t) {
            logger.warn("Exception in AccessLogFilter of service(" + invoker + " -> " + inv + ")", t);
        }
        return invoker.invoke(inv);
    }

    /**
     *  将访问日志放入到缓冲集合中
     * @param accessLog 日志文件名，默认值为true或default
     * @param accessLogData 访问日志数据对象
     */
    private void log(String accessLog, AccessLogData accessLogData) {
        // 获得日志文件对应的集合，集合是为了缓冲日志
        Set<AccessLogData> logSet = LOG_ENTRIES.computeIfAbsent(accessLog, k -> new ConcurrentHashSet<>());

        // 如果集合中的日志数量小于最大缓冲数据，放入到集合中，否则打印警告日志，丢弃日志数据对象，
        if (logSet.size() < LOG_MAX_BUFFER) {
            logSet.add(accessLogData);
        } else {
            //TODO we needs use force writing to file so that buffer gets clear and new log can be written.
            logger.warn("AccessLog buffer is full skipping buffer ");
        }
    }

    /**
     *  写入日志到文件
     */
    private void writeLogToFile() {
        // 如果开启了日志，只要开启了日志，这个集合不会为空的
        if (!LOG_ENTRIES.isEmpty()) {

            // 循环所有的日志文件
            for (Map.Entry<String, Set<AccessLogData>> entry : LOG_ENTRIES.entrySet()) {
                try {
                    // 日志文件，可能是true/default/具体的文件名
                    String accessLog = entry.getKey();

                    // 访问日志数据对象
                    Set<AccessLogData> logSet = entry.getValue();

                    // 如果是默认的 true/default
                    if (ConfigUtils.isDefault(accessLog)) {
                        processWithServiceLogger(logSet);
                    } else {
                        // 不是默认的，那么就是具体的文件路径了
                        File file = new File(accessLog);

                        // 保证日志文件的父目录存在
                        createIfLogDirAbsent(file);
                        if (logger.isDebugEnabled()) {
                            logger.debug("Append log to " + accessLog);
                        }

                        // 日志归档
                        renameFile(file);

                        // 将日志数据对象写入到日志文件
                        processWithAccessKeyLogger(logSet, file);
                    }

                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     *
     * @param logSet
     * @param file
     * @throws IOException
     */
    private void processWithAccessKeyLogger(Set<AccessLogData> logSet, File file) throws IOException {
        // 将日志写入日志文件
        try (FileWriter writer = new FileWriter(file, true)) {
            for (Iterator<AccessLogData> iterator = logSet.iterator();
                 iterator.hasNext();
                 iterator.remove()) {
                writer.write(iterator.next().getLogMessage());
                writer.write(System.getProperty("line.separator"));
            }
            // 刷盘
            writer.flush();
        }
    }


    private AccessLogData buildAccessLogData(Invoker<?> invoker, Invocation inv) {
        AccessLogData logData = AccessLogData.newLogData();
        // 构建日志信息
        logData.setServiceName(invoker.getInterface().getName());
        logData.setMethodName(inv.getMethodName());
        logData.setVersion(invoker.getUrl().getParameter(VERSION_KEY));
        logData.setGroup(invoker.getUrl().getParameter(GROUP_KEY));
        logData.setInvocationTime(new Date());
        logData.setTypes(inv.getParameterTypes());
        logData.setArguments(inv.getArguments());
        return logData;
    }

    /**
     *  处理默认的日志
     * @param logSet
     */
    private void processWithServiceLogger(Set<AccessLogData> logSet) {
        Iterator<AccessLogData> iterator = logSet.iterator();
        while (iterator.hasNext()) {
            AccessLogData logData = iterator.next();

            // 以 dubbo.access.服务名接口名 写日志
            LoggerFactory.getLogger(LOG_KEY + "." + logData.getServiceName()).info(logData.getLogMessage());

            // 删除这个日志数据对象，防止多次写入
            iterator.remove();
        }
    }

    /**
     * 如果日志文件父目录不存在时，创建父目录
     * @param file
     */
    private void createIfLogDirAbsent(File file) {
        File dir = file.getParentFile();
        if (null != dir && !dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 文件存在时，如果文件最后的更新时间不是今日，那么重命名日志文件名称，加入日期
     */
    private void renameFile(File file) {
        if (file.exists()) {
            String now = FILE_NAME_FORMATTER.format(new Date());
            String last = FILE_NAME_FORMATTER.format(new Date(file.lastModified()));
            if (!now.equals(last)) {
                File archive = new File(file.getAbsolutePath() + "." + last);
                file.renameTo(archive);
            }
        }
    }
}
