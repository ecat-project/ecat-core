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
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.Utils.Mdc.MdcContext;
import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * 门面契约测试：对 getScheduledExecutor() 返回对象逐项断言语义（对照
 * ScheduledThreadPoolExecutor 行为）——106 文件/141 调用点零改动的安全网。
 *
 * <p>真实线程 + 真实时钟（行为级断言，窗口放宽到秒级容错）；任务体内的 sleep/latch 等待是
 * 「假 IO」被测对象本身，不是测试同步。
 */
public class SchedulerEngineContractTest {

    private SchedulerEngine engine;

    @Before
    public void setUp() {
        // tick=20ms 提高周期精度；慢/硬阈值拉满避免噪声；watchdog 100ms（最小值）供硬超时用例。
        // 真实线程 + 系统时钟（表轮/lane 均由真实时间驱动）；虚拟时钟确定性断言在 TimingWheelTest。
        SchedulerConfig config = new SchedulerConfig(2, 20L, 64, 600_000L, 600_000L, 100L, 256, 5, 300_000L);
        engine = new SchedulerEngine(config, SchedulerClock.SYSTEM, true);
    }

    @After
    public void tearDown() {
        MDC.remove(MdcContext.INTEGRATION_COORDINATE_KEY);
        engine.shutdownNow();
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("latch 未在期限内放开", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void scheduleCallableReturnsValue() throws Exception {
        ScheduledFuture<Integer> future = engine.schedule(() -> 42, 0, TimeUnit.MILLISECONDS);
        assertThat(future.get(5, TimeUnit.SECONDS), is(42));
    }

    @Test
    public void scheduleRunnableCompletesWithNull() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        ScheduledFuture<?> future = engine.schedule(ran::countDown, 0, TimeUnit.MILLISECONDS);
        await(ran);
        assertThat(future.get(5, TimeUnit.SECONDS), is((Object) null));
        assertThat(future.isDone(), is(true));
    }

    @Test
    public void executeServesCompletableFutureImmediately() throws Exception {
        // gassensor PM3006SDevice 的用法：CompletableFuture.supplyAsync(..., getScheduledExecutor())
        // 走 execute() 通道，不得经表轮多等一个 tick
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "calibration", engine);
        assertThat(future.get(5, TimeUnit.SECONDS), is("calibration"));
    }

    @Test
    public void submitReturnsValue() throws Exception {
        assertThat(engine.submit(() -> 7).get(5, TimeUnit.SECONDS), is(7));
    }

    @Test
    public void scheduleHonorsDelayLowerBound() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<Long> firedAt = new AtomicReference<>();
        long submittedAt = System.nanoTime();
        engine.schedule(() -> {
            firedAt.set(System.nanoTime());
            ran.countDown();
        }, 150, TimeUnit.MILLISECONDS);
        await(ran);
        long deltaMs = TimeUnit.NANOSECONDS.toMillis(firedAt.get() - submittedAt);
        assertTrue("延迟下界：不得早于 150ms（实测 " + deltaMs + "ms）", deltaMs >= 140L);
        assertTrue("延迟上界：不得晚得离谱（实测 " + deltaMs + "ms）", deltaMs <= 2_000L);
    }

    @Test
    public void scheduleWithFixedDelayExecutesRepeatedly() throws Exception {
        CountDownLatch thirdRun = new CountDownLatch(3);
        engine.scheduleWithFixedDelay(thirdRun::countDown, 0, 50, TimeUnit.MILLISECONDS);
        await(thirdRun);
    }

    @Test
    public void fixedRateReschedulesFromNominalStartNotCompletion() throws Exception {
        // 任务体 300ms 假 IO、周期 100ms：fixedRate 下一轮按名义网格（已错过）→ 立即补跑，
        // 两轮起点间隔 ≈ 单次执行时长（~300ms）；fixedDelay 按完成时刻 +100ms（~400ms）。
        // 两种语义的判别窗口取 [350, 380) 分隔。
        long gap = measureSecondStartGapMillis(true);
        assertTrue("fixedRate 第二轮起点应在完成即补跑（" + gap + "ms < 380ms）", gap < 380L);
    }

    @Test
    public void fixedDelayReschedulesFromCompletionTime() throws Exception {
        long gap = measureSecondStartGapMillis(false);
        assertTrue("fixedDelay 第二轮起点应 ≥ 完成+100ms（" + gap + "ms ≥ 380ms）", gap >= 380L);
    }

    private long measureSecondStartGapMillis(boolean fixedRate) throws Exception {
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY,
                fixedRate ? "com.ecat:integration-rate-probe" : "com.ecat:integration-delay-probe");
        CountDownLatch secondStarted = new CountDownLatch(2);
        AtomicReference<Long> firstStart = new AtomicReference<>();
        AtomicReference<Long> secondStart = new AtomicReference<>();
        AtomicInteger ordinal = new AtomicInteger();
        Runnable body = () -> {
            int round = ordinal.getAndIncrement();
            if (round == 0) {
                firstStart.set(System.nanoTime());
            } else if (round == 1) {
                secondStart.set(System.nanoTime());
            }
            secondStarted.countDown();
            sleepQuietly(300L);  // 假 IO：被测量的对象本身
        };
        if (fixedRate) {
            engine.scheduleAtFixedRate(body, 0, 100, TimeUnit.MILLISECONDS);
        } else {
            engine.scheduleWithFixedDelay(body, 0, 100, TimeUnit.MILLISECONDS);
        }
        await(secondStarted);
        return TimeUnit.NANOSECONDS.toMillis(secondStart.get() - firstStart.get());
    }

    @Test
    public void periodicExceptionStopsSchedulingWithOneDeathLine() throws Exception {
        CountDownLatch firstRan = new CountDownLatch(1);
        CountDownLatch markerRan = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();

        engine.scheduleAtFixedRate(() -> {
            executions.incrementAndGet();
            firstRan.countDown();
            throw new IllegalStateException("poll boom");
        }, 0, 50, TimeUnit.MILLISECONDS);
        await(firstRan);

        // 标记任务与被测周期任务同落 "core" 车道（同一测试类定义）→ 车道串行保证：
        // 标记任务执行时，周期任务的 runTask 终态处理（死亡计数）已完成
        engine.execute(markerRan::countDown);
        await(markerRan);

        assertThat("抛异常后不再调度下次", executions.get(), is(1));
        assertThat("死亡点名计数恰好一次", engine.getMetrics().getPeriodicDiedOnException().sum(), is(1L));
    }

    @Test
    public void mdcCoordinatePropagatesAndPeriodicGetsFreshTraceId() throws Exception {
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, "com.ecat:integration-mdc-probe");
        CountDownLatch twoExecutions = new CountDownLatch(2);
        AtomicReference<String> firstTraceId = new AtomicReference<>();
        AtomicReference<String> secondTraceId = new AtomicReference<>();
        AtomicReference<String> coordinateInTask = new AtomicReference<>();
        AtomicInteger round = new AtomicInteger();

        engine.scheduleAtFixedRate(() -> {
            coordinateInTask.set(MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY));
            if (round.getAndIncrement() == 0) {
                firstTraceId.set(TraceContext.getTraceId());
            } else {
                secondTraceId.set(TraceContext.getTraceId());
            }
            twoExecutions.countDown();
        }, 0, 20, TimeUnit.MILLISECONDS);
        await(twoExecutions);

        assertThat("提交时的 coordinate 必须传播到执行线程",
                coordinateInTask.get(), is("com.ecat:integration-mdc-probe"));
        assertTrue("周期任务每次执行应换新 traceId",
                firstTraceId.get() != null && secondTraceId.get() != null
                        && !firstTraceId.get().equals(secondTraceId.get()));
    }

    @Test
    public void hardTimeoutInterruptsBlockedTaskAndPeriodicRetries() throws Exception {
        // 独立小阈值引擎：硬超时 1s（配置下限）→ 阻塞的周期任务被中断 → 失败计入熔断 →
        // 周期继续重排 → 第二轮正常执行（连续失败才 OPEN，单次超时不杀轮询）
        SchedulerConfig config = new SchedulerConfig(2, 20L, 64, 500_000L, 1_000L, 100L, 256, 5, 300_000L);
        SchedulerEngine local = new SchedulerEngine(config, SchedulerClock.SYSTEM, true);
        try {
            CountDownLatch secondRun = new CountDownLatch(2);
            AtomicInteger interruptedCount = new AtomicInteger();
            AtomicInteger round = new AtomicInteger();
            engine = local;  // tearDown 统一收口

            local.scheduleWithFixedDelay(() -> {
                secondRun.countDown();
                if (round.getAndIncrement() == 0) {
                    try {
                        // 第一轮假装卡死在无超时 IO 上，硬超时中断将其打断；第二轮立即返回
                        new CountDownLatch(1).await();
                    } catch (InterruptedException e) {
                        interruptedCount.incrementAndGet();
                    }
                }
            }, 0, 50, TimeUnit.MILLISECONDS);

            await(secondRun);
            assertThat("硬超时必须真正中断阻塞执行", interruptedCount.get(), is(1));
            assertThat(local.getMetrics().getHardTimeouts().sum(), is(1L));
            assertThat("超时按失败计入熔断", local.getMetrics().getFailed().sum(), is(1L));
        } finally {
            local.shutdownNow();
        }
    }

    @Test
    public void shutdownRejectsNewSubmissions() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        engine.execute(ran::countDown);
        await(ran);
        engine.shutdown();
        assertThat(engine.isShutdown(), is(true));
        try {
            engine.schedule(() -> { }, 1, TimeUnit.MILLISECONDS);
            fail("停机后提交应抛 RejectedExecutionException");
        } catch (RejectedExecutionException expected) {
            // STPE 同款契约
        }
        assertTrue("优雅停机应可终止", engine.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    public void shutdownRunsWheelPendingOneshotToCompletion() throws Exception {
        // bug-record-20260817-021700：停机时仍在表轮内等待的一次性任务，到期必须入车道
        // 跑完并正常完成 future（STPE executeExistingDelayedTasksAfterShutdown=true 语义）；
        // 旧行为静默丢弃→等待方永远挂起。周期任务停机取消的语义不变（下条断言）。
        CountDownLatch oneshotRan = new CountDownLatch(1);
        ScheduledFuture<?> oneshot = engine.schedule(oneshotRan::countDown, 120, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> periodic = engine.scheduleAtFixedRate(() -> { }, 0, 20, TimeUnit.MILLISECONDS);

        engine.shutdown(); // oneshot 尚未到期（120ms > 停机时刻）

        await(oneshotRan);
        assertThat("一次性任务 future 应正常完成（非取消）", oneshot.isCancelled(), is(false));
        assertThat(oneshot.get(5, TimeUnit.SECONDS), is((Object) null));
        assertTrue("引擎应在任务排空后终止", engine.awaitTermination(5, TimeUnit.SECONDS));
        assertThat("周期任务停机后应被取消不重排", periodic.isCancelled() || periodic.isDone(), is(true));
    }

    @Test
    public void shutdownNowReturnsPendingAndCancelsThem() {
        ScheduledFuture<?> pending = engine.schedule(() -> { }, 60, TimeUnit.SECONDS);
        List<Runnable> returned = engine.shutdownNow();
        assertThat("未到期任务应被返回（STPE shutdownNow 契约）", returned.size(), is(1));
        assertThat("返回任务应已被取消", pending.isCancelled(), is(true));
        assertThat(engine.isShutdown(), is(true));
    }

    @Test
    public void cancelPreventsExecutionAndFutureReportsCancelled() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        ScheduledFuture<?> future = engine.schedule(ran::countDown, 200, TimeUnit.MILLISECONDS);
        assertThat(future.cancel(false), is(true));
        assertThat(future.isCancelled(), is(true));
        // 取消后停机回收，再断言计数为零（避免负等待：任务被取消后永不 countDown）
        engine.shutdownNow();
        assertThat("被取消任务不得执行", ran.getCount(), is(1L));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
