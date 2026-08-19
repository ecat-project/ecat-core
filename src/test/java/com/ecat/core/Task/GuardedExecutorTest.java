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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * GuardedExecutor（统一硬超时看门狗）契约测试：
 * 超时执法（TimeoutException 完成+interrupt）、gate 串行/异 gate 并行、
 * 池满 REJECTED 点名、超时回调、MDC 传播、默认超时配置解析。
 *
 * <p>同步纪律：全部用 latch 证「事件已发生」；超时执法本身是等待 watchdog 触发的事件，
 * 用 latch.await(边界) 而非固定 sleep。
 *
 * @author coffee
 */
public class GuardedExecutorTest {

    private GuardedExecutor executor;

    @Before
    public void setUp() {
        executor = new GuardedExecutor(4, "guarded-test");
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    // ==================== 超时执法 ====================

    /** 红测试复现的缺陷形态：无护栏的烂任务（不可中断挂死）让调用方永远阻塞——护栏后必须在超时时刻解除。 */
    @Test(timeout = 10000)
    public void hungTask_completesWithTimeoutExceptionAndCallerUnblocks() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        GuardedExecutor.GuardedFuture<String> future = executor.doSubmit("g1", "hung",
                () -> {
                    taskStarted.countDown();
                    // 不可中断挂死：忽略 interrupt，直到测试显式放行（占槽至自然结束的诚实边界）
                    releaseTask.await();
                    return "late";
                }, 300);

        assertTrue(taskStarted.await(5, TimeUnit.SECONDS));

        long start = System.nanoTime();
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("挂死任务应超时完成");
        } catch (ExecutionException e) {
            assertTrue("应以 TimeoutException 完成, 实际: " + e.getCause(),
                    e.getCause() instanceof TimeoutException);
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("调用方应在超时时刻附近解除阻塞, 实际 " + elapsedMs + "ms", elapsedMs < 4000);

        // 账目点名：超时计数
        assertTrue("stats 应含 timedOut>=1: " + executor.stats(), executor.stats().contains("timedOut=1"));
        // 释放挂死任务，槽位归还（late-end 路径）
        releaseTask.countDown();
        waitForStatAtLeast("completed", 1, 5000);
    }

    /** 超时后提交的异 gate 任务不被烂任务拖死（隔离第一原则）。 */
    @Test(timeout = 10000)
    public void afterTimeout_otherGateTaskStillRuns() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        executor.doSubmit("hung-gate", "hung", (Callable<Void>) () -> {
            taskStarted.countDown();
            releaseTask.await();
            return null;
        }, 200);
        assertTrue(taskStarted.await(5, TimeUnit.SECONDS));

        // 等 hung 任务被执法（否则它只占 1/4 槽，证明力不足）
        waitForStatAtLeast("timedOut", 1, 5000);

        GuardedExecutor.GuardedFuture<String> healthy = executor.doSubmit("other-gate", "healthy",
                () -> "ok", 5000);
        assertEquals("ok", healthy.get(5, TimeUnit.SECONDS));
        releaseTask.countDown();
    }

    /** 超时回调（tcp 首连带外清理依赖）在执法时刻被触发。 */
    @Test(timeout = 10000)
    public void onTimeoutCallback_firesAtEnforcement() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        CountDownLatch callbackFired = new CountDownLatch(1);
        GuardedExecutor.GuardedFuture<Void> future = executor.doSubmit("g-cb", "hung-cb", () -> {
            taskStarted.countDown();
            releaseTask.await();
            return null;
        }, 200);
        assertTrue(taskStarted.await(5, TimeUnit.SECONDS));
        future.onTimeout(callbackFired::countDown);

        assertTrue("超时回调应在执法时刻触发", callbackFired.await(5, TimeUnit.SECONDS));
        releaseTask.countDown();
    }

    // ==================== gate 语义 ====================

    /** 同 gate FIFO：第二个任务开始时第一个必已结束（开始时刻互斥）。 */
    @Test(timeout = 10000)
    public void sameGate_tasksAreSerialized() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch firstFinished = new CountDownLatch(1);
        AtomicInteger secondStartedWhileFirstRunning = new AtomicInteger();

        executor.doSubmit("serial", "first", () -> {
            firstStarted.countDown();
            firstFinished.await(5, TimeUnit.SECONDS);
            return null;
        }, 5000);
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

        executor.doSubmit("serial", "second", () -> {
            if (firstFinished.getCount() > 0) {
                secondStartedWhileFirstRunning.incrementAndGet();
            }
            return null;
        }, 5000);

        // 放行第一个；若 gate 未串行，second 会在 first 运行中就开始（计数>0）
        firstFinished.countDown();
        waitForStatAtLeast("completed", 2, 5000);
        assertEquals("同 gate 任务必须串行", 0, secondStartedWhileFirstRunning.get());
    }

    /** 异 gate 并行：两个 gate 的任务可同时运行（latch 互等证明同时在线）。 */
    @Test(timeout = 10000)
    public void differentGates_tasksRunInParallel() throws Exception {
        CountDownLatch bothRunning = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        executor.doSubmit("gate-a", "a", (Callable<Void>) () -> {
            bothRunning.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }, 5000);
        executor.doSubmit("gate-b", "b", (Callable<Void>) () -> {
            bothRunning.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }, 5000);

        assertTrue("异 gate 任务应并行运行（同 gate 会死等对方）", bothRunning.await(5, TimeUnit.SECONDS));
        release.countDown();
        waitForStatAtLeast("completed", 2, 5000);
    }

    // ==================== 池满 REJECTED ====================

    /** 4 槽被不可中断任务占满 → 新 gate 提交立即 REJECTED，异常点名 gate/label；同 gate 排队不 REJECTED。 */
    @Test(timeout = 10000)
    public void poolFull_newGateSubmitRejectedWithNaming() throws Exception {
        CountDownLatch allStarted = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            executor.doSubmit("occupy-" + idx, "occupier-" + idx, (Callable<Void>) () -> {
                allStarted.countDown();
                release.await(10, TimeUnit.SECONDS);
                return null;
            }, 60000);
        }
        assertTrue(allStarted.await(5, TimeUnit.SECONDS));

        try {
            executor.doSubmit("victim-gate", "victim-label", () -> "never", 1000);
            fail("池满应 REJECTED");
        } catch (RejectedExecutionException e) {
            assertTrue("异常应点名 gate: " + e.getMessage(), e.getMessage().contains("victim-gate"));
            assertTrue("异常应点名 label: " + e.getMessage(), e.getMessage().contains("victim-label"));
        }
        assertTrue(executor.stats().contains("rejected=1"));

        // 同 gate 排队语义不受池满影响：已占槽 gate 的新任务入 FIFO 队列而非 REJECTED
        executor.doSubmit("occupy-0", "queued-after", () -> "queued", 1000);
        release.countDown();
        waitForStatAtLeast("rejected", 1, 5000); // 无新增 REJECTED 即排队成功
    }

    /**
     * 池满 REJECTED 必须落 ERROR 日志（质量要求）：拒绝=真实工作被丢弃，
     * 可见性不能依赖调用方自觉——GuardedView.execute 路径下异常被 CompletableFuture
     * 吸收进 future，全链可能零日志。源头单点 ERROR 是唯一可靠出口。
     */
    @Test(timeout = 10000)
    public void poolFull_rejected_emitsErrorLog() throws Exception {
        Logger guardedLogger = (Logger) LoggerFactory.getLogger(GuardedExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        guardedLogger.addAppender(appender);
        CountDownLatch release = new CountDownLatch(1);
        try {
            CountDownLatch allStarted = new CountDownLatch(4);
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                executor.doSubmit("fill-" + idx, "filler-" + idx, (Callable<Void>) () -> {
                    allStarted.countDown();
                    release.await();
                    return null;
                }, 60000);
            }
            assertTrue(allStarted.await(5, TimeUnit.SECONDS));

            try {
                executor.doSubmit("log-victim-gate", "log-victim-label", () -> "never", 1000);
                fail("池满应 REJECTED");
            } catch (RejectedExecutionException expected) {
            }
            boolean hasError = appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.ERROR
                            && e.getFormattedMessage().contains("guarded-task-rejected")
                            && e.getFormattedMessage().contains("log-victim-gate"));
            assertTrue("池满 REJECTED 必须输出含 gate 点名的 ERROR 日志, 实际: " + appender.list, hasError);
            // 真凶点名：日志必须列出占槽者（filler 任务），被拒者无辜、占槽者才是排障目标
            boolean namesOccupier = appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.ERROR
                            && e.getFormattedMessage().contains("guarded-task-rejected")
                            && e.getFormattedMessage().contains("fill-0")
                            && e.getFormattedMessage().contains("已运行"));
            assertTrue("池满 REJECTED 日志必须点名占槽者及已运行时长, 实际: " + appender.list, namesOccupier);
        } finally {
            guardedLogger.detachAppender(appender);
            release.countDown();
        }
    }

    /**
     * 僵尸占槽必须显式标注：超时已执法但不可中断、槽位不可回收的任务是池满的真凶，
     * 日志须区分「合法慢任务仍在期限内」与「僵尸已破约仍占槽」。
     */
    @Test(timeout = 15000)
    public void poolFull_rejected_marksZombieOccupant() throws Exception {
        Logger guardedLogger = (Logger) LoggerFactory.getLogger(GuardedExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        guardedLogger.addAppender(appender);
        CountDownLatch release = new CountDownLatch(1);
        try {
            // 真凶：不可中断挂死任务（吞 interrupt，模拟 native 调用/不检查 interrupt 的场景），
            // 短超时让看门狗执法（future 已 TimeoutException 但槽仍被占）
            executor.doSubmit("zombie-gate", "zombie-label", (Callable<Void>) () -> {
                while (release.getCount() > 0) {
                    try {
                        release.await(10, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignored) {
                        // 吞 interrupt = 不可中断：占槽至自然结束的诚实边界
                    }
                }
                return null;
            }, 200);
            waitForStatAtLeast("timedOut", 1, 5000);

            // 占满其余 3 槽（合法慢任务，长超时不出僵尸标注）
            CountDownLatch allStarted = new CountDownLatch(3);
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                executor.doSubmit("slow-" + idx, "slow-label-" + idx, (Callable<Void>) () -> {
                    allStarted.countDown();
                    release.await();
                    return null;
                }, 60000);
            }
            assertTrue(allStarted.await(5, TimeUnit.SECONDS));

            try {
                executor.doSubmit("victim2", "victim2-label", () -> "never", 1000);
                fail("池满应 REJECTED");
            } catch (RejectedExecutionException expected) {
            }
            String rejectLog = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("guarded-task-rejected"))
                    .reduce((first, second) -> second).orElse("");
            assertTrue("僵尸占槽者必须被点名标注, 实际: " + rejectLog,
                    rejectLog.contains("zombie-gate") && rejectLog.contains("僵尸"));
            assertTrue("合法慢任务占槽者照常点名但不标僵尸, 实际: " + rejectLog,
                    rejectLog.contains("slow-0")
                            && rejectLog.indexOf("slow-0") >= 0
                            && !rejectLog.contains("slow-label-0(超时已执法"));
        } finally {
            guardedLogger.detachAppender(appender);
            release.countDown();
        }
    }

    // ==================== MDC 传播 ====================

    @Test(timeout = 10000)
    public void mdcTraceIdPropagatedToWorker() throws Exception {
        TraceContext.setTraceId("trace-guarded-test");
        try {
            AtomicReference<String> seen = new AtomicReference<>();
            GuardedExecutor.GuardedFuture<Void> future = executor.doSubmit("mdc-gate", "mdc-task", () -> {
                seen.set(TraceContext.getTraceId());
                return null;
            }, 5000);
            future.get(5, TimeUnit.SECONDS);
            assertEquals("trace-guarded-test", seen.get());
        } finally {
            TraceContext.clearTraceId();
        }
    }

    // ==================== 默认超时配置解析 ====================

    @Test
    public void defaultTimeoutMs_unconfiguredIs60s() {
        String previous = System.clearProperty("ecat.guarded.timeout-ms");
        try {
            assertEquals(60000L, GuardedExecutor.defaultTimeoutMs());
        } finally {
            restore(previous);
        }
    }

    @Test
    public void defaultTimeoutMs_configuredValueHonored() {
        String previous = System.setProperty("ecat.guarded.timeout-ms", "12345");
        try {
            assertEquals(12345L, GuardedExecutor.defaultTimeoutMs());
        } finally {
            restore(previous);
        }
    }

    @Test
    public void defaultTimeoutMs_malformedValueThrows() {
        String previous = System.setProperty("ecat.guarded.timeout-ms", "abc");
        try {
            GuardedExecutor.defaultTimeoutMs();
            fail("非法配置应抛 IllegalArgumentException（严格模式，不静默回退）");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        } finally {
            restore(previous);
        }
    }

    // ==================== 辅助：轮询等 stats 事件（等事件非等时间） ====================

    private void waitForStatAtLeast(String key, long minValue, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(key + "=(\\d+)")
                    .matcher(executor.stats());
            if (m.find() && Long.parseLong(m.group(1)) >= minValue) {
                return;
            }
            Thread.sleep(20);
        }
        fail("等待 stats 事件超时: 期望 " + key + ">=" + minValue + ", 实际: " + executor.stats());
    }

    private static void restore(String previous) {
        if (previous == null) {
            System.clearProperty("ecat.guarded.timeout-ms");
        } else {
            System.setProperty("ecat.guarded.timeout-ms", previous);
        }
    }

    // 静态共享实例 API 冒烟（submit/视图/账目）
    @Test(timeout = 10000)
    public void sharedInstance_apiSmoke() throws Exception {
        GuardedExecutor.GuardedFuture<String> future = GuardedExecutor.submit("shared-gate", "smoke",
                () -> "shared-ok", 5000);
        assertEquals("shared-ok", future.get(5, TimeUnit.SECONDS));
        assertTrue(GuardedExecutor.getStats().contains("completed="));

        java.util.concurrent.ExecutorService view = GuardedExecutor.guardedExecutorFor("view-gate", 5000);
        java.util.concurrent.Future<Integer> viewed = view.submit(() -> 42);
        assertEquals(Integer.valueOf(42), viewed.get(5, TimeUnit.SECONDS));
        try {
            view.shutdown();
            fail("视图 shutdown 应不支持");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
