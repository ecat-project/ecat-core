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

import org.slf4j.MDC;

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
     * 捕获当前 MDC 上下文
     *
     * @return MDC 上下文
     */
    private Map<String, String> captureContext() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * 包装 Runnable
     *
     * @param task 原始任务
     * @param context MDC 上下文
     * @return 包装后的任务
     */
    private Runnable wrapRunnable(Runnable task, Map<String, String> context) {
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            try {
                if (context != null) {
                    for (Map.Entry<String, String> entry : context.entrySet()) {
                        MDC.put(entry.getKey(), entry.getValue());
                    }
                }
                task.run();
            } finally {
                MDC.clear();
                if (previousContext != null) {
                    for (Map.Entry<String, String> entry : previousContext.entrySet()) {
                        MDC.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        };
    }

    /**
     * 包装 Callable
     *
     * @param task 原始任务
     * @param context MDC 上下文
     * @return 包装后的任务
     */
    private <V> Callable<V> wrapCallable(Callable<V> task, Map<String, String> context) {
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            try {
                if (context != null) {
                    for (Map.Entry<String, String> entry : context.entrySet()) {
                        MDC.put(entry.getKey(), entry.getValue());
                    }
                }
                return task.call();
            } finally {
                MDC.clear();
                if (previousContext != null) {
                    for (Map.Entry<String, String> entry : previousContext.entrySet()) {
                        MDC.put(entry.getKey(), entry.getValue());
                    }
                }
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
        return scheduledDelegate.scheduleAtFixedRate(wrapRunnable(command, context), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        Map<String, String> context = captureContext();
        return scheduledDelegate.scheduleWithFixedDelay(wrapRunnable(command, context), initialDelay, delay, unit);
    }
}
