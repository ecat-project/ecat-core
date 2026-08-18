/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.CommTrace;

import java.io.Closeable;
import java.io.IOException;

/**
 * 通讯帧订阅者（SSE 桥接点）。携带自己的 {@link CommTraceFilter}，
 * 由 {@link CommTraceBuffer} 的单写者投递线程在匹配时回调 {@link #send}。
 *
 * <p>send 抛异常即被投递线程踢出（与 LogSubscriber 同语义）；投递线程 send 时
 * 不持任何 ecat 锁，慢/假死订阅者不阻塞捕获热路径。
 *
 * @author coffee
 */
public abstract class CommTraceSubscriber implements Closeable {

    private final CommTraceFilter filter;

    protected CommTraceSubscriber(CommTraceFilter filter) {
        this.filter = filter;
    }

    public final CommTraceFilter getFilter() {
        return filter;
    }

    /** 匹配过滤时投递一帧。实现不得阻塞过久（阻塞只会导致自身被环淘汰的帧丢弃）。 */
    public abstract void send(CommTraceEvent event) throws IOException;

    /** 订阅者已死（连接关闭/超时）则返回 true，投递线程据此踢出。 */
    public abstract boolean isClosed();

    @Override
    public abstract void close();
}
