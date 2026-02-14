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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.ecat.core.Utils.Mdc.MdcScheduledExecutorService;

/**
 * TaskManager class is responsible for managing scheduled tasks using a thread pool.
 * It provides methods to pause, stop, and access the executor service.
 * Uses MDC-wrapped executor to preserve context across async tasks.
 *
 * @author coffee
 */
public class TaskManager {
    // Raw executor service (for shutdown)
    private final ScheduledExecutorService rawExecutorService = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);
    // MDC-wrapped executor service (for task execution with context propagation)
    private final ScheduledExecutorService executorService = MdcScheduledExecutorService.wrap(this.rawExecutorService);

    private static final int THREAD_POOL_SIZE = 2;

    public ScheduledExecutorService getExecutorService() {
        return this.executorService;
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return this.executorService.scheduleWithFixedDelay(command, initialDelay, period, unit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return this.executorService.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return this.executorService.schedule(command, delay, unit);
    }

    public void pauseAllTasks() {
        // 空实现，可扩展
    }

    public void stopAllTasks() {
        this.rawExecutorService.shutdownNow();
    }
}
