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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MDC 包装 ExecutorService
 *
 * <p>自动将提交任务时的 MDC 上下文传递到任务执行线程。
 *
 * <p>使用示例：
 * <pre>
 * ExecutorService executor = MdcExecutorService.wrap(Executors.newFixedThreadPool(4));
 * </pre>
 * 
 * @author coffee
 */
public class MdcExecutorService implements ExecutorService {
    private final ExecutorService delegate;

    protected MdcExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    /**
     * 包装 ExecutorService
     *
     * @param executor 原始 ExecutorService
     * @return 包装后的 ExecutorService
     */
    public static ExecutorService wrap(ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }
        if (executor instanceof MdcExecutorService) {
            return executor;
        }
        return new MdcExecutorService(executor);
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
    private <T> Callable<T> wrapCallable(Callable<T> task, Map<String, String> context) {
        return TraceContext.wrapCallable(task, context);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrapRunnable(command, captureContext()));
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrapCallable(task, captureContext()));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrapRunnable(task, captureContext()), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrapRunnable(task, captureContext()));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        Map<String, String> context = captureContext();
        List<Callable<T>> wrappedTasks = new ArrayList<>();
        for (Callable<T> task : tasks) {
            wrappedTasks.add(wrapCallable(task, context));
        }
        return delegate.invokeAll(wrappedTasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        Map<String, String> context = captureContext();
        List<Callable<T>> wrappedTasks = new ArrayList<>();
        for (Callable<T> task : tasks) {
            wrappedTasks.add(wrapCallable(task, context));
        }
        return delegate.invokeAll(wrappedTasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        Map<String, String> context = captureContext();
        List<Callable<T>> wrappedTasks = new ArrayList<>();
        for (Callable<T> task : tasks) {
            wrappedTasks.add(wrapCallable(task, context));
        }
        return delegate.invokeAny(wrappedTasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        Map<String, String> context = captureContext();
        List<Callable<T>> wrappedTasks = new ArrayList<>();
        for (Callable<T> task : tasks) {
            wrappedTasks.add(wrapCallable(task, context));
        }
        return delegate.invokeAny(wrappedTasks, timeout, unit);
    }
}
