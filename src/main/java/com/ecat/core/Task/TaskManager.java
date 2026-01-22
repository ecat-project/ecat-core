package com.ecat.core.Task;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class TaskManager {
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