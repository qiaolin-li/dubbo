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

import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Codec2;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.support.MultiMessage;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.RpcInvocation;

import java.io.IOException;

import static org.apache.dubbo.rpc.Constants.INPUT_KEY;
import static org.apache.dubbo.rpc.Constants.OUTPUT_KEY;

/**
 *  支持多消息的解码器，可一次性解码多条消息
 */

public final class DubboCountCodec implements Codec2 {

    // Dubbo编解码器，真正的编码、解码逻辑委托给它去做
    private DubboCodec codec = new DubboCodec();

    @Override
    public void encode(Channel channel, ChannelBuffer buffer, Object msg) throws IOException {
        codec.encode(channel, buffer, msg);
    }

    @Override
    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        // 记录当前消息的读的起始位置
        int save = buffer.readerIndex();

        // 创建多消息类
        MultiMessage result = MultiMessage.create();

        do {
            // 开始解码
            Object obj = codec.decode(channel, buffer);

            // 如果数据不够，那么恢复消息的读的索引位置，当下次消息来到时，重新从该位置开始读
            if (Codec2.DecodeResult.NEED_MORE_INPUT == obj) {
                buffer.readerIndex(save);
                break;
            } else {

                // 消息足够，保存消息
                result.addMessage(obj);

                // 在消息对象obj中设置数据大小    buffer.readerIndex()为当前读取到的位置，减去开始保存的位置，则为消息体长度
                logMessageLength(obj, buffer.readerIndex() - save);

                // 保存当前位置，下一个消息从这里开始读
                save = buffer.readerIndex();
            }
        } while (true);

        // 如果没读取到消息，则返回一个标记
        if (result.isEmpty()) {
            return Codec2.DecodeResult.NEED_MORE_INPUT;
        }

        // 如果只有一个，返回消息本身
        if (result.size() == 1) {
            return result.get(0);
        }

        // 存在多个，返回多消息对象
        return result;
    }

    /**
     *  在请求或响应中记录数据的长度
     * @param result
     * @param bytes
     */
    private void logMessageLength(Object result, int bytes) {
        if (bytes <= 0) {
            return;
        }
        if (result instanceof Request) {
            try {
                ((RpcInvocation) ((Request) result).getData()).setAttachment(INPUT_KEY, String.valueOf(bytes));
            } catch (Throwable e) {
                /* ignore */
            }
        } else if (result instanceof Response) {
            try {
                ((AppResponse) ((Response) result).getResult()).setAttachment(OUTPUT_KEY, String.valueOf(bytes));
            } catch (Throwable e) {
                /* ignore */
            }
        }
    }

}
