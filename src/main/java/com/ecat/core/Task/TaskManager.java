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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Utils.Mdc.MdcExecutorService;
import com.ecat.core.Utils.Mdc.MdcScheduledExecutorService;

/**
 * TaskManager class is responsible for managing scheduled tasks and executor services.
 * It provides methods to create MDC-wrapped executors that preserve context across async tasks.
 *
 * <p>Usage examples:
 * <pre>
 * // For scheduled tasks (timers, periodic execution)
 * ScheduledExecutorService scheduler = taskManager.getMdcScheduledExecutorService();
 * scheduler.scheduleWithFixedDelay(() -> {...}, 0, 5, TimeUnit.SECONDS);
 *
 * // For CompletableFuture async operations
 * ExecutorService executor = taskManager.createMdcExecutorService(2);
 * CompletableFuture.supplyAsync(() -> {...}, executor)
 *     .thenApplyAsync(result -> {...}, executor);
 * </pre>
 *
 * @author coffee
 */
public class TaskManager {
    // Raw executor service (for shutdown) - 使用 NamedThreadFactory 命名
    private final ThreadFactory scheduledThreadFactory = new NamedThreadFactory("ecat-scheduled", true);
    private final ScheduledExecutorService rawExecutorService = Executors.newScheduledThreadPool(THREAD_POOL_SIZE, scheduledThreadFactory);
    // MDC-wrapped executor service (for task execution with context propagation)
    private final ScheduledExecutorService scheduledExecutorService = MdcScheduledExecutorService.wrap(this.rawExecutorService);

    // Managed executor services created by this manager (for unified shutdown)
    private final List<ExecutorService> managedExecutors = new CopyOnWriteArrayList<>();

    private static final int THREAD_POOL_SIZE = 2;

    /**
     * @deprecated Use {@link #getMdcScheduledExecutorService()} instead.
     * @return the MDC-wrapped ScheduledExecutorService
     */
    @Deprecated
    public ScheduledExecutorService getExecutorService() {
        return this.scheduledExecutorService;
    }

    /**
     * Get the shared MDC-wrapped ScheduledExecutorService.
     * Use this for scheduled tasks (scheduleWithFixedDelay, scheduleAtFixedRate, schedule).
     *
     * @return MDC-wrapped ScheduledExecutorService
     */
    public ScheduledExecutorService getMdcScheduledExecutorService() {
        return this.scheduledExecutorService;
    }

    /**
     * Create an MDC-wrapped fixed-size thread pool ExecutorService.
     * The thread name prefix is automatically inferred from the caller's coordinate.
     *
     * @param poolSize the number of threads in the pool
     * @return MDC-wrapped ExecutorService
     */
    public ExecutorService createMdcExecutorService(int poolSize) {
        String prefix = inferCallerCoordinate();
        return createMdcExecutorService(poolSize, prefix);
    }

    /**
     * Create an MDC-wrapped fixed-size thread pool ExecutorService with custom prefix.
     *
     * @param poolSize the number of threads in the pool
     * @param threadNamePrefix prefix for thread names
     * @return MDC-wrapped ExecutorService
     */
    public ExecutorService createMdcExecutorService(int poolSize, String threadNamePrefix) {
        ThreadFactory factory = new NamedThreadFactory(threadNamePrefix);
        ExecutorService raw = Executors.newFixedThreadPool(poolSize, factory);
        ExecutorService wrapped = MdcExecutorService.wrap(raw);
        managedExecutors.add(wrapped);
        return wrapped;
    }

    /**
     * Create an MDC-wrapped single-thread ExecutorService.
     * The thread name prefix is automatically inferred from the caller's coordinate.
     *
     * @return MDC-wrapped single-thread ExecutorService
     */
    public ExecutorService createMdcSingleThreadExecutor() {
        String prefix = inferCallerCoordinate();
        return createMdcSingleThreadExecutor(prefix);
    }

    /**
     * Create an MDC-wrapped single-thread ExecutorService with custom prefix.
     *
     * @param threadNamePrefix prefix for thread name
     * @return MDC-wrapped single-thread ExecutorService
     */
    public ExecutorService createMdcSingleThreadExecutor(String threadNamePrefix) {
        ThreadFactory factory = new NamedThreadFactory(threadNamePrefix);
        ExecutorService raw = Executors.newSingleThreadExecutor(factory);
        ExecutorService wrapped = MdcExecutorService.wrap(raw);
        managedExecutors.add(wrapped);
        return wrapped;
    }

    /**
     * Create an MDC-wrapped cached thread pool ExecutorService.
     * The thread name prefix is automatically inferred from the caller's coordinate.
     *
     * @return MDC-wrapped cached thread pool ExecutorService
     */
    public ExecutorService createMdcCachedExecutorService() {
        String prefix = inferCallerCoordinate();
        return createMdcCachedExecutorService(prefix);
    }

    /**
     * Create an MDC-wrapped cached thread pool ExecutorService with custom prefix.
     *
     * @param threadNamePrefix prefix for thread names
     * @return MDC-wrapped cached thread pool ExecutorService
     */
    public ExecutorService createMdcCachedExecutorService(String threadNamePrefix) {
        ThreadFactory factory = new NamedThreadFactory(threadNamePrefix);
        ExecutorService raw = Executors.newCachedThreadPool(factory);
        ExecutorService wrapped = MdcExecutorService.wrap(raw);
        managedExecutors.add(wrapped);
        return wrapped;
    }

    /**
     * Infer the caller's coordinate by inspecting the call stack.
     * Looks for IntegrationBase subclasses and extracts their coordinate.
     *
     * @return inferred coordinate prefix, or "ecat" as default
     */
    private String inferCallerCoordinate() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            // Look for IntegrationBase subclasses
            if (className.contains("integration.") || className.contains("Integration")) {
                try {
                    Class<?> clazz = Class.forName(className);
                    if (IntegrationBase.class.isAssignableFrom(clazz) && clazz != IntegrationBase.class) {
                        // Return simplified coordinate using class name
                        String simpleName = clazz.getSimpleName();
                        return simpleName.replace("Integration", "").toLowerCase();
                    }
                } catch (ClassNotFoundException ignored) {
                    // Class not found, continue searching
                }
            }
        }
        return "ecat";  // Default prefix
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return this.scheduledExecutorService.scheduleWithFixedDelay(command, initialDelay, period, unit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return this.scheduledExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return this.scheduledExecutorService.schedule(command, delay, unit);
    }

    public void pauseAllTasks() {
        // 空实现，可扩展
    }

    /**
     * Shutdown all managed executor services.
     * This includes the shared scheduled executor and all executors created via createMdc* methods.
     */
    public void shutdownAll() {
        // Shutdown scheduled executor
        this.rawExecutorService.shutdownNow();

        // Shutdown all managed executors
        for (ExecutorService executor : managedExecutors) {
            executor.shutdownNow();
        }
        managedExecutors.clear();
    }

    /**
     * @deprecated Use {@link #shutdownAll()} instead.
     */
    @Deprecated
    public void stopAllTasks() {
        shutdownAll();
    }
}
