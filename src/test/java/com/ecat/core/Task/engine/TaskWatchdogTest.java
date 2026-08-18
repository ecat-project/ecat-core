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

package com.ecat.core.Task.engine;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

/**
 * 看门狗单测：注入时钟 + scanOnce 手动驱动；假任务的阻塞用 latch 控制（无 sleep 同步）。
 *
 * <p>覆盖：慢任务阈值只点名不打断（且每次执行至多一行）；硬超时中断执行线程 + 标记失败；
 * 阈值之下无动作。
 */
public class TaskWatchdogTest {

    private static final long SLOW_MS = 10_000L;
    private static final long HARD_MS = 30_000L;

    private MutableClock clock;
    private SchedulerMetrics metrics;
    private TaskWatchdog watchdog;

    @Before
    public void setUp() {
        clock = new MutableClock();
        metrics = new SchedulerMetrics();
        SchedulerConfig config = new SchedulerConfig(1, 100L, 64, SLOW_MS, HARD_MS, 1_000L, 16, 5, 300_000L);
        watchdog = new TaskWatchdog(config, clock, metrics, false);
    }

    private EngineTask<Void> registeredTask(Thread runner, long ageMillis) {
        EngineTask<Void> task = new EngineTask<>(new EngineTask.Instrumented<>(() -> null),
                clock.nanoTime(), 0L, "lane-w", "WatchdogTestTask", clock);
        task.runner = runner;
        task.startNanos = clock.nanoTime() - TimeUnit.MILLISECONDS.toNanos(ageMillis);
        watchdog.register(task);
        return task;
    }

    @Test
    public void slowTaskIsReportedOnceWithoutInterrupt() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch taskRunning = new CountDownLatch(1);
        CountDownLatch taskExited = new CountDownLatch(1);
        // 假任务挂住（模拟慢 IO），自身监测是否被中断
        Thread runner = new Thread(() -> {
            taskRunning.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.set(true);
            } finally {
                taskExited.countDown();
            }
        }, "fake-slow-runner");
        runner.start();
        assertTrue(taskRunning.await(5, TimeUnit.SECONDS));

        EngineTask<Void> task = registeredTask(runner, SLOW_MS);  // 恰达慢阈值
        watchdog.scanOnce();

        assertThat("慢任务应点名一次", metrics.getSlowTaskReports().sum(), is(1L));
        assertThat("慢任务不打断", task.timedOut, is(false));
        assertThat(metrics.getHardTimeouts().sum(), is(0L));

        watchdog.scanOnce();  // 第二轮扫描不得重复点名
        assertThat("每次执行至多一行点名", metrics.getSlowTaskReports().sum(), is(1L));
        assertThat(interrupted.get(), is(false));

        runner.interrupt();
        assertTrue(taskExited.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void hardTimeoutInterruptsRunnerAndMarksTask() throws Exception {
        CountDownLatch taskRunning = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Thread runner = new Thread(() -> {
            taskRunning.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        }, "fake-stuck-runner");
        runner.start();
        assertTrue(taskRunning.await(5, TimeUnit.SECONDS));

        EngineTask<Void> task = registeredTask(runner, HARD_MS);  // 恰达硬超时
        watchdog.scanOnce();

        assertThat("硬超时应中断执行线程", interrupted.await(5, TimeUnit.SECONDS), is(true));
        assertThat("任务被标记超时（结果按失败计）", task.timedOut, is(true));
        assertThat(metrics.getHardTimeouts().sum(), is(1L));
    }

    @Test
    public void belowThresholdNoAction() throws Exception {
        CountDownLatch taskRunning = new CountDownLatch(1);
        Thread runner = new Thread(() -> {
            taskRunning.countDown();
            awaitForever();
        }, "fake-fresh-runner");
        runner.setDaemon(true);
        runner.start();
        assertTrue(taskRunning.await(5, TimeUnit.SECONDS));

        registeredTask(runner, SLOW_MS - 1);  // 未达慢阈值
        watchdog.scanOnce();

        assertThat(metrics.getSlowTaskReports().sum(), is(0L));
        assertThat(metrics.getHardTimeouts().sum(), is(0L));
    }

    @Test
    public void unregisteredTaskIsNotScanned() throws Exception {
        CountDownLatch taskRunning = new CountDownLatch(1);
        Thread runner = new Thread(() -> {
            taskRunning.countDown();
            awaitForever();
        }, "fake-ghost-runner");
        runner.setDaemon(true);
        runner.start();
        assertTrue(taskRunning.await(5, TimeUnit.SECONDS));

        EngineTask<Void> task = registeredTask(runner, HARD_MS);
        watchdog.unregister(task);
        watchdog.scanOnce();

        assertThat("注销后不再扫描", metrics.getHardTimeouts().sum(), is(0L));
        assertThat(watchdog.runningCount(), is(0));
    }

    // ==================== blocking 探针（103000 观测补盲：1s 早段，slow 10s 之前） ====================

    /**
     * 运行超 blocking 阈值（1s）未完的任务：计数恰一次 + 出现在当前清单
     * （lane + 任务类 + 已跑 ms），且未达 slow 阈值不重复点名慢任务。
     */
    @Test
    public void blockingTaskCountedOnceAndListedBeforeSlowThreshold() throws Exception {
        CountDownLatch taskRunning = new CountDownLatch(1);
        Thread runner = new Thread(() -> {
            taskRunning.countDown();
            awaitForever();
        }, "fake-blocking-runner");
        runner.setDaemon(true);
        runner.start();
        assertTrue(taskRunning.await(5, TimeUnit.SECONDS));

        registeredTask(runner, 2_000);  // 已跑 2s：过 blocking(1s)、未达 slow(10s)
        watchdog.scanOnce();

        assertThat("blocking 计数恰一次", metrics.getBlockingTasks().sum(), is(1L));
        assertThat("未达 slow 阈值不计慢任务", metrics.getSlowTaskReports().sum(), is(0L));

        java.util.List<BlockingTaskStatus> list = watchdog.blockingSnapshot();
        assertThat("当前清单恰一条", list.size(), is(1));
        assertThat(list.get(0).getLaneKey(), is("lane-w"));
        assertThat(list.get(0).getTaskLabel(), is("WatchdogTestTask"));
        assertThat("已跑时长如实 ≥2000ms", list.get(0).getElapsedMillis() >= 2_000L, is(true));

        watchdog.scanOnce();  // 第二轮扫描不重复计数
        assertThat("每次执行至多计一次", metrics.getBlockingTasks().sum(), is(1L));
    }

    /** 未达 blocking 阈值（<1s）：不计数、清单空。 */
    @Test
    public void belowBlockingThresholdNotCountedNorListed() throws Exception {
        CountDownLatch taskRunning = new CountDownLatch(1);
        Thread runner = new Thread(() -> {
            taskRunning.countDown();
            awaitForever();
        }, "fake-fresh-blocking-runner");
        runner.setDaemon(true);
        runner.start();
        assertTrue(taskRunning.await(5, TimeUnit.SECONDS));

        registeredTask(runner, 999);  // 差 1ms 到阈值
        watchdog.scanOnce();

        assertThat(metrics.getBlockingTasks().sum(), is(0L));
        assertThat("清单如实为空", watchdog.blockingSnapshot().isEmpty(), is(true));
    }

    private static void awaitForever() {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
