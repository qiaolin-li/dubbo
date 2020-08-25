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
package org.apache.dubbo.remoting;

import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;

import java.io.IOException;

/**
 *  编解码器
 *  TODO 为什么是2呢？ 2.0？
 *  是的，就是第2版
 */

@SPI
public interface Codec2 {

    /**
     *  编码
     * @param channel 连接通道
     * @param buffer  将编码后的字节写入的buffer
     * @param message 待编码的数据
     * @throws IOException
     */
    @Adaptive({Constants.CODEC_KEY})
    void encode(Channel channel, ChannelBuffer buffer, Object message) throws IOException;

    /**
     *  解码
     * @param channel 连接通道
     * @param buffer 待解码的字节
     * @return 解码完的数据
     * @throws IOException
     */
    @Adaptive({Constants.CODEC_KEY})
    Object decode(Channel channel, ChannelBuffer buffer) throws IOException;


    /**
     *  解码解雇
     */
    enum DecodeResult {

        // 需要更多的输入， TODO 解决粘包？ 解决拆包的
        NEED_MORE_INPUT,

        // 跳过少量的输入 TODO ？？
        SKIP_SOME_INPUT
    }

}

