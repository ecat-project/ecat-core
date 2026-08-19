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

import com.ecat.core.Utils.Mdc.TraceContext;

import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 命名线程工厂，为创建的线程提供统一的前缀命名
 *
 * <p>线程命名格式: {prefix}-{index}，例如: sailhero-0, sailhero-1
 *
 * <p>traceId 自动覆盖（arch-review 25 号杠杆③）：创建线程时包装 Runnable——线程起步时
 * 若无 traceId 则生成一个并入 MDC，长命循环线程（zeroconf 广播、调度 timer/watchdog 等）
 * 全程可按 ULID 追踪，日志不再出现空 {@code [-]} 槽。
 *
 * <p>与既有 MDC 包装的组合语义：本包装只在线程<strong>起步时</strong>补一个基线 traceId，
 * 不固定捕获创建时的上下文——池化执行器（{@code MdcExecutorService} 等）提交的任务
 * 仍按任务粒度捕获/恢复自己的 MDC，任务结束后回到本工厂的基线 id，两层互不双包、
 * 互不覆盖（回归见 NamedThreadFactoryTraceTest）。
 *
 * <p>明示边界：不经本工厂创建的库内部线程（JmDNS Timer、modbus4j 内部线程等）不覆盖。
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
        Thread t = new Thread(withBaselineTraceId(r));
        t.setName(prefix + "-" + counter.getAndIncrement());
        t.setDaemon(daemon);
        return t;
    }

    /**
     * 线程起步时补基线 traceId：已有则不动（外层提交方可能已注入），无则生成一个。
     * 结束时恢复线程起步前的 MDC（对池 worker 循环无实际影响，仅为对称收尾）。
     */
    private static Runnable withBaselineTraceId(Runnable task) {
        return () -> {
            Map<String, String> previous = TraceContext.capture();
            try {
                TraceContext.getOrCreateTraceId();
                task.run();
            } finally {
                TraceContext.restore(previous);
            }
        };
    }
}
