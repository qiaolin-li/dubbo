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
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;

import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;
import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.Constants.ASYNC_KEY;
import static org.apache.dubbo.rpc.Constants.FORCE_USE_TAG;
import static org.apache.dubbo.rpc.Constants.TOKEN_KEY;


/**
 * ContextFilter set the provider RpcContext with invoker, invocation, local port it is using and host for
 * current execution thread.
 *
 * @see RpcContext
 */
@Activate(group = PROVIDER, order = -10000)
public class ContextFilter implements Filter, Filter.Listener {
    private static final String TAG_KEY = "dubbo.tag";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        Map<String, String> attachments = invocation.getAttachments();
        if (attachments != null) {
            attachments = new HashMap<>(attachments);
            attachments.remove(PATH_KEY);
            attachments.remove(INTERFACE_KEY);
            attachments.remove(GROUP_KEY);
            attachments.remove(VERSION_KEY);
            attachments.remove(DUBBO_VERSION_KEY);
            attachments.remove(TOKEN_KEY);
            attachments.remove(TIMEOUT_KEY);
            // Remove async property to avoid being passed to the following invoke chain.
            // 删除异步属性，避免传递到下面的调用链 TODO 这里不太了解
            attachments.remove(ASYNC_KEY);
            attachments.remove(TAG_KEY);
            attachments.remove(FORCE_USE_TAG);
        }
        RpcContext context = RpcContext.getContext();

        context.setInvoker(invoker)
                .setInvocation(invocation)
//              .setAttachments(attachments)  // merged from dubbox
                .setLocalAddress(invoker.getUrl().getHost(), invoker.getUrl().getPort());

        // 获取消费者应用名
        String remoteApplication = invocation.getAttachment(REMOTE_APPLICATION_KEY);
        if (StringUtils.isNotEmpty(remoteApplication)) {
            context.setRemoteApplicationName(remoteApplication);
        } else {

            // 如果消费者应用名为空，则从附件中取
            context.setRemoteApplicationName(RpcContext.getContext().getAttachment(REMOTE_APPLICATION_KEY));
        }


        // merged from dubbox
        // we may already added some attachments into RpcContext before this filter (e.g. in rest protocol)
        // 可能在当前Filter之前已经在上下文中放置了附件，我们需要合并过来
        if (attachments != null) {
            if (RpcContext.getContext().getAttachments() != null) {
                RpcContext.getContext().getAttachments().putAll(attachments);
            } else {
                RpcContext.getContext().setAttachments(attachments);
            }
        }

        if (invocation instanceof RpcInvocation) {
            ((RpcInvocation) invocation).setInvoker(invoker);
        }
        try {
            // 标记不可清除上下文
            RpcContext.getContext().clearAfterEachInvoke(false);
            return invoker.invoke(invocation);
        } finally {
            // 标记可以清除上下文
            RpcContext.getContext().clearAfterEachInvoke(true);
            // IMPORTANT! For async scenario, we must remove context from current thread, so we always create a new RpcContext for the next invoke for the same thread.
            // 对于异步场景，我们必须清理掉当前线程的上下文，我们针对相同的线程进行下一次调用时，总是会创建一个新的上下文
            // TODO 异步场景清理掉上下文之后，会不会导致拿不到上下文中的信息？？
            // 突然想了一下，都是异步请求了，就已经不是一个线程了，所以也拿不到上下文里面的信息了
            RpcContext.removeContext();
            RpcContext.removeServerContext();
        }
    }

    @Override
    public void onMessage(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        // pass attachments to result
        // 将附件传递给结果，这里是服务端的附件哈 TODO 给消费端干啥呢？？？
        appResponse.addAttachments(RpcContext.getServerContext().getAttachments());
    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {

    }
}
