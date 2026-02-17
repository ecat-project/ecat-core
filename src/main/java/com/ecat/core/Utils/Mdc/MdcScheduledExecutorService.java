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

package com.ecat.core.Utils.Mdc;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MDC 包装 ScheduledExecutorService
 *
 * <p>自动将提交任务时的 MDC 上下文传递到任务执行线程。
 *
 * <p>对于周期性任务（scheduleAtFixedRate、scheduleWithFixedDelay），
 * 每次执行时生成新的 Trace ID，以便追踪单次执行的完整链路。
 * coordinate 等其他上下文保持不变（来自提交时）。
 *
 * <p>使用示例：
 * <pre>
 * ScheduledExecutorService scheduler = MdcScheduledExecutorService.wrap(Executors.newScheduledThreadPool(2));
 * </pre>
 *
 * @author coffee
 */
public class MdcScheduledExecutorService extends MdcExecutorService implements ScheduledExecutorService {
    private final ScheduledExecutorService scheduledDelegate;

    protected MdcScheduledExecutorService(ScheduledExecutorService delegate) {
        super(delegate);
        this.scheduledDelegate = delegate;
    }

    /**
     * 包装 ScheduledExecutorService
     *
     * @param executor 原始 ScheduledExecutorService
     * @return 包装后的 ScheduledExecutorService
     */
    public static ScheduledExecutorService wrap(ScheduledExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }
        if (executor instanceof MdcScheduledExecutorService) {
            return executor;
        }
        return new MdcScheduledExecutorService(executor);
    }

    /**
     * 捕获当前 MDC 上下文（包括 Trace ID）
     *
     * @return MDC 上下文
     */
    private Map<String, String> captureContext() {
        return TraceContext.capture();
    }

    /**
     * 包装 Runnable，自动传播 MDC 上下文和 Trace ID
     *
     * @param task 原始任务
     * @param context MDC 上下文
     * @return 包装后的任务
     */
    private Runnable wrapRunnable(Runnable task, Map<String, String> context) {
        return TraceContext.wrapRunnable(task, context);
    }

    /**
     * 包装 Callable，自动传播 MDC 上下文和 Trace ID
     *
     * @param task 原始任务
     * @param context MDC 上下文
     * @return 包装后的任务
     */
    private <V> Callable<V> wrapCallable(Callable<V> task, Map<String, String> context) {
        return TraceContext.wrapCallable(task, context);
    }

    /**
     * 包装周期性任务，每次执行时生成新的 Trace ID
     *
     * <p>周期性任务（scheduleAtFixedRate、scheduleWithFixedDelay）每次执行是独立的业务操作，
     * 应该有独立的 Trace ID 以便追踪单次执行链路。
     * coordinate 等其他上下文保持不变（来自提交时）。
     *
     * @param task 原始任务
     * @param context MDC 上下文（提交时捕获，不含 traceId）
     * @return 包装后的任务
     */
    private Runnable wrapPeriodicRunnable(Runnable task, Map<String, String> context) {
        return () -> {
            Map<String, String> previousContext = TraceContext.capture();
            try {
                // 恢复上下文（coordinate 等）
                TraceContext.restore(context);
                // 为每次执行生成新的 Trace ID
                TraceContext.setTraceId(TraceContext.generateTraceId());
                task.run();
            } finally {
                TraceContext.restore(previousContext);
            }
        };
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return scheduledDelegate.schedule(wrapRunnable(command, captureContext()), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return scheduledDelegate.schedule(wrapCallable(callable, captureContext()), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        Map<String, String> context = captureContext();
        return scheduledDelegate.scheduleAtFixedRate(wrapPeriodicRunnable(command, context), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        Map<String, String> context = captureContext();
        return scheduledDelegate.scheduleWithFixedDelay(wrapPeriodicRunnable(command, context), initialDelay, delay, unit);
    }
}
