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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 车道调度单测：同键串行 / 异键并行 / 公平轮转 / 有界拒绝。
 *
 * <p>同步纪律：全部 CountDownLatch 确定性同步；任务体的 latch 等待是「假 IO」不是测试同步。
 * 公平性用「A1 阻塞期间入队 A2/A3/B1」钉死时序——A 车道被 A1 占用时不在就绪环，B1 必然
 * 插到 A2 前，顺序唯一确定。
 */
public class LaneDispatcherTest {

    private static final long AWAIT = 5_000L;

    private MutableClock clock;
    private SchedulerEngine engine;
    private SchedulerMetrics metrics;
    private LaneDispatcher dispatcher;

    @Before
    public void setUp() {
        clock = new MutableClock();
        // 引擎不启线程（本测试直接驱动 dispatcher），dispatcher 启真实 worker
        engine = new SchedulerEngine(testConfig(2, 4096), clock, false);
        metrics = new SchedulerMetrics();
        dispatcher = new LaneDispatcher(engine, testConfig(2, 4096), clock,
                new TaskWatchdog(testConfig(2, 4096), clock, metrics, false), metrics, true);
    }

    @After
    public void tearDown() {
        dispatcher.shutdownNow();
    }

    private static SchedulerConfig testConfig(int workers, int maxQueued) {
        return new SchedulerConfig(workers, 100L, 64, 60_000L, 120_000L, 1_000L,
                maxQueued, 5, 300_000L);
    }

    private EngineTask<Void> task(String laneKey, Runnable body) {
        return new EngineTask<>(new EngineTask.Instrumented<>(() -> {
            body.run();
            return null;
        }), clock.nanoTime(), 0L, laneKey, "LaneTestTask", clock);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("latch 未在期限内放开（5s）", latch.await(AWAIT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void sameLaneTasksAreStrictlySerial() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();

        Runnable body = () -> {
            int current = inside.incrementAndGet();
            maxInside.accumulateAndGet(current, Math::max);
            inside.decrementAndGet();
        };

        dispatcher.enqueue(task("lane-A", () -> {
            firstStarted.countDown();
            body.run();
            awaitQuietly(releaseFirst);
        }));
        await(firstStarted);
        dispatcher.enqueue(task("lane-A", () -> {
            body.run();
            secondDone.countDown();
        }));
        // 此时 lane-A 的第二个任务必然还在排队（第一个占着车道），先放行再等完成
        releaseFirst.countDown();
        await(secondDone);

        assertThat("同车道任务不得并发（并发则峰值 > 1）", maxInside.get(), is(1));
    }

    @Test
    public void differentLanesRunInParallel() throws Exception {
        CountDownLatch bothRunning = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        dispatcher.enqueue(task("lane-A", () -> {
            bothRunning.countDown();
            awaitQuietly(release);
        }));
        dispatcher.enqueue(task("lane-B", () -> {
            bothRunning.countDown();
            awaitQuietly(release);
        }));

        // 2 worker 下两个车道任务同时挂住：latch(2) 放开即证明并行（串行则永远凑不齐 2）
        await(bothRunning);
        release.countDown();
    }

    @Test
    public void singleWorkerRoundRobinIsFairAcrossLanes() throws Exception {
        LaneDispatcher singleWorker = new LaneDispatcher(engine, testConfig(1, 4096), clock,
                new TaskWatchdog(testConfig(1, 4096), clock, metrics, false), metrics, true);
        try {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch allDone = new CountDownLatch(4);
            List<String> order = new ArrayList<>();

            singleWorker.enqueue(task("lane-A", () -> {
                firstStarted.countDown();
                awaitQuietly(releaseFirst);
                synchronized (order) { order.add("A1"); }
                allDone.countDown();
            }));
            await(firstStarted);
            // A 车道被 A1 占用（不在就绪环）：此刻入队的三件任务排布确定——
            // A2/A3 进 lane-A 队列，B1 进就绪环；A1 完成后 A 车道重新挂环，排在 B 之后
            singleWorker.enqueue(task("lane-A", () -> { synchronized (order) { order.add("A2"); } allDone.countDown(); }));
            singleWorker.enqueue(task("lane-A", () -> { synchronized (order) { order.add("A3"); } allDone.countDown(); }));
            singleWorker.enqueue(task("lane-B", () -> { synchronized (order) { order.add("B1"); } allDone.countDown(); }));

            releaseFirst.countDown();
            await(allDone);

            assertThat("B1 不得饿在 A 车道积压后面", order.toArray(), is(new String[]{"A1", "B1", "A2", "A3"}));
        } finally {
            singleWorker.shutdownNow();
        }
    }

    @Test
    public void queueBoundRejectsObservable() {
        // 无 worker 模式直接测 enqueue 的总量上限：maxQueued=4，第 5 件必须被拒
        LaneDispatcher bounded = new LaneDispatcher(engine, testConfig(1, 4), clock,
                new TaskWatchdog(testConfig(1, 4), clock, metrics, false), metrics, false);
        for (int i = 0; i < 4; i++) {
            assertThat("前 4 件应入队", bounded.enqueue(task("lane-A", () -> { })), is(true));
        }
        assertThat("第 5 件应被拒（防 OOM 有界拒绝）", bounded.enqueue(task("lane-A", () -> { })), is(false));
        assertThat("被拒不减少已排队计数", bounded.queuedTotal(), is(4));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
