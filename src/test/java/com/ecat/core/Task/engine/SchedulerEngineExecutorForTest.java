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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.Task.TaskManager;
import com.ecat.core.Utils.Mdc.MdcContext;

/**
 * {@link SchedulerEngine#executorFor(String)} 显式车道视图契约测试（IO 收敛 P0）：
 * 调用方按「资源坐标」（如 modbus source 连接）而非提交方 MDC 坐标分道——
 * 同 key 严格串行、异 key 并行、与 MDC 推导任务共享同一套车道执法且互不拖停。
 *
 * <p>同步纪律：全程 latch/barrier 确定性同步，无 Thread.sleep；任务体内的
 * latch 等待是「假 IO」被测对象本身，不是测试同步。
 */
public class SchedulerEngineExecutorForTest {

    private SchedulerEngine engine;

    @Before
    public void setUp() {
        // 3 worker：共存用例同时按住两个阻塞任务（MDC 车道 + 显式车道各占一个 worker），
        // 仍须有第三个 worker 服务健康车道；慢/硬阈值拉满避免噪声
        SchedulerConfig config = new SchedulerConfig(3, 20L, 64, 600_000L, 600_000L, 100L, 256, 5, 300_000L);
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
    public void sameKeyTasksExecuteStrictlyInSequence() throws Exception {
        ExecutorService lane = engine.executorFor("modbus-source:serial-probe");
        CountDownLatch aStarted = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        CountDownLatch aDone = new CountDownLatch(1);
        CountDownLatch bDone = new CountDownLatch(1);
        CountDownLatch otherLaneDone = new CountDownLatch(1);
        // B 只能观察到 A 已完成（车道串行）；若同 key 被并行执行，B 会在 A 阻塞期启动观察到 false
        AtomicBoolean bObservedADone = new AtomicBoolean(false);
        List<String> events = Collections.synchronizedList(new ArrayList<>());

        lane.execute(() -> {
            aStarted.countDown();
            awaitQuietly(releaseA);          // 假 IO：按住本车道
            events.add("A-end");
            aDone.countDown();
        });
        await(aStarted);                     // A 已占住 worker1，才开始排 B
        lane.execute(() -> {
            events.add("B-start");
            bObservedADone.set(aDone.getCount() == 0L);
            bDone.countDown();
        });
        // 异 key 探针：worker2 空闲可用（探针立刻执行），而同 key 的 B 仍被按住——串行性的确定性反证
        engine.executorFor("modbus-source:serial-other").execute(otherLaneDone::countDown);
        await(otherLaneDone);
        assertThat("有空闲 worker 服务了异 key 探针，同 key 的 B 不得先于 A 启动",
                bDone.getCount(), is(1L));

        releaseA.countDown();
        await(aDone);
        await(bDone);
        assertThat("同 key 两任务必须按 A→B 顺序执行", events, is(listOf("A-end", "B-start")));
        assertThat("B 启动时 A 必须已完成（同 key 串行）", bObservedADone.get(), is(true));
    }

    @Test
    public void distinctKeysRunConcurrently() throws Exception {
        ExecutorService laneA = engine.executorFor("modbus-source:par-a");
        ExecutorService laneB = engine.executorFor("modbus-source:par-b");
        CyclicBarrier bothRunning = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicBoolean serialized = new AtomicBoolean(false);
        Runnable body = () -> {
            try {
                // 真 IO 形态：双方都要等对方在场才放行；被串行化则 barrier 超时
                bothRunning.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                serialized.set(true);
            } finally {
                done.countDown();
            }
        };
        laneA.execute(body);
        laneB.execute(body);
        // 串行化情形下 barrier 要先耗满 5s 超时，窗口放宽到 10s
        assertTrue("latch 未在期限内放开", done.await(10, TimeUnit.SECONDS));
        assertThat("异 key 两任务必须并行（barrier 汇合失败=被串行化）", serialized.get(), is(false));
    }

    @Test
    public void explicitLaneCoexistsWithMdcDerivedLanes() throws Exception {
        // 方向 1：MDC 推导车道阻塞不拖停显式车道
        CountDownLatch mdcBlockerStarted = new CountDownLatch(1);
        CountDownLatch releaseMdcBlocker = new CountDownLatch(1);
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, "com.ecat:integration-mdc-blocked");
        engine.execute(() -> {
            mdcBlockerStarted.countDown();
            awaitQuietly(releaseMdcBlocker);
        });
        await(mdcBlockerStarted);

        CountDownLatch explicitDone = new CountDownLatch(1);
        engine.executorFor("modbus-source:coex-free").execute(explicitDone::countDown);
        assertTrue("MDC 车道阻塞期间，显式车道任务必须照常执行", explicitDone.await(5, TimeUnit.SECONDS));

        // 方向 2：显式车道阻塞也不拖停 MDC 推导车道（换一个 MDC 坐标=另一个集成，
        // 方向 1 的坐标仍被 blocker 按住，同坐标排队是车道语义而非缺陷）
        CountDownLatch explicitBlockerStarted = new CountDownLatch(1);
        CountDownLatch releaseExplicitBlocker = new CountDownLatch(1);
        CountDownLatch mdcDone = new CountDownLatch(1);
        engine.executorFor("modbus-source:coex-blocked").execute(() -> {
            explicitBlockerStarted.countDown();
            awaitQuietly(releaseExplicitBlocker);
        });
        await(explicitBlockerStarted);
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, "com.ecat:integration-mdc-healthy");
        engine.execute(mdcDone::countDown);
        assertTrue("显式车道阻塞期间，MDC 推导车道任务必须照常执行", mdcDone.await(5, TimeUnit.SECONDS));

        releaseMdcBlocker.countDown();
        releaseExplicitBlocker.countDown();
    }

    @Test
    public void explicitKeyRoutesToExplicitLaneAndMdcStillPropagates() throws Exception {
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, "com.ecat:integration-mdc-submitter");
        AtomicReference<String> coordinateSeenInTask = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        ExecutorService lane = engine.executorFor("modbus-source:explicit-key-probe");
        lane.execute(() -> {
            coordinateSeenInTask.set(MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY));
            done.countDown();
        });
        await(done);
        assertThat("提交时 MDC 上下文必须照常传播到任务体（日志链路不丢）",
                coordinateSeenInTask.get(), is("com.ecat:integration-mdc-submitter"));
        assertThat("显式键应作为独立车道存在",
                engine.getBreakers().containsKey("modbus-source:explicit-key-probe"), is(true));
        assertThat("提交方 MDC 坐标不得被当作车道键（显式键优先）",
                engine.getBreakers().containsKey("com.ecat:integration-mdc-submitter"), is(false));
    }

    @Test
    public void submitAndCompletableFutureCompleteWithResult() throws Exception {
        ExecutorService lane = engine.executorFor("modbus-source:future-probe");
        Future<String> submitted = lane.submit(() -> "transaction-result");
        assertThat("submit(Callable) 的 Future 必须正常携带结果", submitted.get(5, TimeUnit.SECONDS), is("transaction-result"));

        // P2 modbus source 的实际用法：supplyAsync(事务体, 车道视图)
        CompletableFuture<Integer> async = CompletableFuture.supplyAsync(() -> 21, lane);
        assertThat("CompletableFuture 必须在车道上完成", async.get(5, TimeUnit.SECONDS), is(21));

        Future<?> runnable = lane.submit(() -> { }, "preset");
        assertThat("submit(Runnable, T) 的 Future 必须返回预设值", runnable.get(5, TimeUnit.SECONDS), is("preset"));
    }

    @Test
    public void viewIsCachedPerKeyAndDistinctAcrossKeys() {
        assertThat("同 key 视图应复用同一薄对象（禁每次建池）",
                engine.executorFor("modbus-source:cache-probe") == engine.executorFor("modbus-source:cache-probe"),
                is(true));
        assertThat("异 key 视图必须彼此独立",
                engine.executorFor("modbus-source:cache-a") == engine.executorFor("modbus-source:cache-b"),
                is(false));
    }

    @Test
    public void engineShutdownRejectsViewSubmissions() throws Exception {
        ExecutorService lane = engine.executorFor("modbus-source:shutdown-probe");
        CountDownLatch ran = new CountDownLatch(1);
        lane.execute(ran::countDown);
        await(ran);

        engine.shutdown();
        assertThat("引擎停机后视图 isShutdown 须为 true", lane.isShutdown(), is(true));
        try {
            lane.execute(() -> { });
            fail("引擎停机后视图 execute 应抛 RejectedExecutionException");
        } catch (RejectedExecutionException expected) {
            // 对齐 ScheduledExecutorService 契约
        }
        try {
            lane.submit(() -> { });
            fail("引擎停机后视图 submit 应抛 RejectedExecutionException");
        } catch (RejectedExecutionException expected) {
            // 同上
        }
        try {
            CompletableFuture.supplyAsync(() -> 1, lane);
            fail("引擎停机后视图上的 supplyAsync 应同步抛 RejectedExecutionException");
        } catch (RejectedExecutionException expected) {
            // CompletableFuture 直接调 execute，拒绝同步可见
        }
        try {
            lane.shutdown();
            fail("视图 shutdown 应显式拒绝（车道是共享引擎的窗口，无独立生命周期）");
        } catch (UnsupportedOperationException expected) {
            // 调用方资源回收语义须由自身标志位表达，不得停掉共享引擎
        }
    }

    @Test
    public void blankLaneKeyIsRejected() {
        assertLaneKeyRejected(null);
        assertLaneKeyRejected("");
        assertLaneKeyRejected("   \t ");
    }

    @Test
    public void taskManagerDelegatesExecutorFor() throws Exception {
        TaskManager taskManager = new TaskManager();
        try {
            ExecutorService lane = taskManager.executorFor("modbus-source:tm-probe");
            Future<Integer> future = lane.submit(() -> 7);
            assertThat(future.get(5, TimeUnit.SECONDS), is(7));
            try {
                taskManager.executorFor("");
                fail("TaskManager 通道同样必须拒绝 blank 车道键");
            } catch (IllegalArgumentException expected) {
                // 严格模式透传
            }
        } finally {
            taskManager.shutdownAll();
        }
    }

    private void assertLaneKeyRejected(String laneKey) {
        try {
            engine.executorFor(laneKey);
            fail("blank/null 车道键必须拒绝: [" + laneKey + "]");
        } catch (IllegalArgumentException expected) {
            // 严格模式：显式拒绝，不静默落默认道
        }
    }

    private static List<String> listOf(String... items) {
        List<String> out = new ArrayList<>();
        Collections.addAll(out, items);
        return out;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
