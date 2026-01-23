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

/**
 * TaskManager class is responsible for managing scheduled tasks using a thread pool.
 * It provides methods to pause, stop, and access the executor service.
 * 
 * @author coffee
 */
public class TaskManager {
    // Executor service for scheduling and executing tasks
    private final ScheduledExecutorService executorService;
    private static final int THREAD_POOL_SIZE = 2; // 可根据实际情况调整线程池大小

    public TaskManager() {
        this.executorService = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);
    }

    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    public void pauseAllTasks() {
        // 这里可以实现更复杂的暂停逻辑，例如标记任务状态等
        // 简单情况下可以直接调用 shutdownNow，但会直接停止所有任务
        // executorService.shutdownNow();
    }

    public void stopAllTasks() {
        executorService.shutdownNow();
    }
}
