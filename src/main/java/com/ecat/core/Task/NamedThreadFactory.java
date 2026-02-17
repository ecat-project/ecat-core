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

package com.ecat.core.Task;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 命名线程工厂，为创建的线程提供统一的前缀命名
 *
 * <p>线程命名格式: {prefix}-{index}，例如: sailhero-0, sailhero-1
 *
 * @author coffee
 */
public class NamedThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(0);
    private final boolean daemon;

    /**
     * 创建命名线程工厂
     *
     * @param prefix 线程名称前缀
     */
    public NamedThreadFactory(String prefix) {
        this(prefix, true);
    }

    /**
     * 创建命名线程工厂
     *
     * @param prefix 线程名称前缀
     * @param daemon 是否为守护线程
     */
    public NamedThreadFactory(String prefix, boolean daemon) {
        this.prefix = prefix;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setName(prefix + "-" + counter.getAndIncrement());
        t.setDaemon(daemon);
        return t;
    }
}
