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
package org.apache.dubbo.rpc.protocol.dubbo;

import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.io.Bytes;
import org.apache.dubbo.common.io.UnsafeByteArrayInputStream;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.codec.ExchangeCodec;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

import java.io.IOException;
import java.io.InputStream;

import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.CallbackServiceCodec.encodeInvocationArgument;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DECODE_IN_IO_THREAD_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.DEFAULT_DECODE_IN_IO_THREAD;

/**
 * Dubbo codec.
 * Dubbo编解码器
 */
public class DubboCodec extends ExchangeCodec {

    public static final String NAME = "dubbo";
    public static final String DUBBO_VERSION = Version.getProtocolVersion();
    // 响应是异常
    public static final byte RESPONSE_WITH_EXCEPTION = 0;
    // 响应是值
    public static final byte RESPONSE_VALUE = 1;
    // 响应是空值
    public static final byte RESPONSE_NULL_VALUE = 2;
    // TODO 上面的这些操作在附件中？
    public static final byte RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS = 3;
    public static final byte RESPONSE_VALUE_WITH_ATTACHMENTS = 4;
    public static final byte RESPONSE_NULL_VALUE_WITH_ATTACHMENTS = 5;

    // 方法参数空数组
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    // 方法参数类型空数组
    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    private static final Logger log = LoggerFactory.getLogger(DubboCodec.class);

    @Override
    protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        // 标记位，标记当前是请求还是响应
        byte flag = header[2];

        // 获得序列化对象
        byte proto = (byte) (flag & SERIALIZATION_MASK);

        // get request id.
        long id = Bytes.bytes2long(header, 4);

        // TODO 这里是请求，为什么要去解析响应呢？？？？？？？？
        if ((flag & FLAG_REQUEST) == 0) {
            // decode response.
            Response res = new Response(id);
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(true);
            }
            // get status.
            byte status = header[3];
            res.setStatus(status);
            try {
                if (status == Response.OK) {
                    Object data;
                    if (res.isEvent()) {
                        ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                        data = decodeEventData(channel, in);
                    } else {

                        DecodeableRpcResult result;
                        // 根据配置，解码的线程池不同，decode.in.io =true,在dubbo线程池中解码，就是现在这个位置
                        if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
                            result = new DecodeableRpcResult(channel, res, is,
                                    (Invocation) getRequestData(id), proto);
                            result.decode();
                        } else {
                            // 这个放到netty中去解码 TODO 后面再看看
                            result = new DecodeableRpcResult(channel, res,
                                    new UnsafeByteArrayInputStream(readMessageData(is)),
                                    (Invocation) getRequestData(id), proto);
                        }
                        data = result;
                    }
                    res.setResult(data);
                } else {
                    // 如果是异常的话
                    ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                    res.setErrorMessage(in.readUTF());
                }
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode response failed: " + t.getMessage(), t);
                }
                // 写入错误信息
                res.setStatus(Response.CLIENT_ERROR);
                res.setErrorMessage(StringUtils.toString(t));
            }
            return res;
        } else {
            // decode request.
            Request req = new Request(id);
            req.setVersion(Version.getProtocolVersion());
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(true);
            }
            try {
                Object data;
                if (req.isEvent()) {
                    ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                    data = decodeEventData(channel, in);
                } else {
                    DecodeableRpcInvocation inv;
                    if (channel.getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD)) {
                        inv = new DecodeableRpcInvocation(channel, req, is, proto);
                        inv.decode();
                    } else {
                        inv = new DecodeableRpcInvocation(channel, req,
                                new UnsafeByteArrayInputStream(readMessageData(is)), proto);
                    }
                    data = inv;
                }
                req.setData(data);
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode request failed: " + t.getMessage(), t);
                }

                // bad request
                req.setBroken(true);
                req.setData(t);
            }

            return req;
        }
    }

    private byte[] readMessageData(InputStream is) throws IOException {
        if (is.available() > 0) {
            byte[] result = new byte[is.available()];
            is.read(result);
            return result;
        }
        return new byte[]{};
    }

    @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeRequestData(channel, out, data, DUBBO_VERSION);
    }

    @Override
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeResponseData(channel, out, data, DUBBO_VERSION);
    }

    @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data, String version) throws IOException {
        // 将请求数据编码
        RpcInvocation inv = (RpcInvocation) data;

        // dubbo 版本号
        out.writeUTF(version);

        // 路径 TODO 这个路径是啥来着
        out.writeUTF(inv.getAttachment(PATH_KEY));
        // 获取接口的版本号？
        out.writeUTF(inv.getAttachment(VERSION_KEY));

        // 请求的方法
        out.writeUTF(inv.getMethodName());

        // 请求的参数类型描述 TODO 不知道是啥，到时候看看
        out.writeUTF(inv.getParameterTypesDesc());

        // 请求方法的实参
        Object[] args = inv.getArguments();
        if (args != null) {
            // 编码每个参数，并写入到输出流中
            for (int i = 0; i < args.length; i++) {
                out.writeObject(encodeInvocationArgument(channel, inv, i));
            }
        }

        // 写出附件信息
        out.writeAttachments(inv.getAttachments());
    }

    @Override
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data, String version) throws IOException {
        Result result = (Result) data;
        // currently, the version value in Response records the version of Request
        // 是否支持响应中携带附件
        boolean attach = Version.isSupportResponseAttachment(version);

        Throwable th = result.getException();

        if (th == null) {
            // 如果响应中没有异常
            Object ret = result.getValue();
            if (ret == null) {
                // 如果响应中没有数据，附件不同空，则可能数据在附件中，否则标记空值
                out.writeByte(attach ? RESPONSE_NULL_VALUE_WITH_ATTACHMENTS : RESPONSE_NULL_VALUE);
            } else {
                // 有数据，如果附件不为空，则标记值在附件中，否则标记值在响应中， TODO 为什么值在附件中啊？？？
                out.writeByte(attach ? RESPONSE_VALUE_WITH_ATTACHMENTS : RESPONSE_VALUE);
                // 写入数据到输出流 TODO 写到那里去了呢？
                out.writeObject(ret);
            }
        } else {
            // 有异常，附件不为空，标记异常在附件中，否则标记异常在响应中
            out.writeByte(attach ? RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS : RESPONSE_WITH_EXCEPTION);

            // 写入异常数据
            out.writeThrowable(th);
        }

        // 是否存在附件
        if (attach) {
            // returns current version of Response to consumer side.
            // 在响应中设置dubbo的版本
            result.getAttachments().put(DUBBO_VERSION_KEY, Version.getProtocolVersion());

            // 写出附件信息
            out.writeAttachments(result.getAttachments());
        }
    }
}
