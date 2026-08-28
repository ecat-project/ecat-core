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

package com.ecat.core.Task.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.Device.RemovalHost;
import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * HostedExecutors 自测：满拒 REE / MDC 提交时捕获执行时恢复 /
 * 线程名派生 / 逐任务异常续跑（execute warn 不上抛 + submit 异常进 future）/
 * threads&lt;1 与 null host 拒绝 / 宿主 sweep=shutdownNow 丢排队+中断在飞。
 *
 * <p>同步纪律：latch 证「事件已发生」，负向断言（任务被丢弃）在 awaitTermination
 * （池已终止=再无执行可能）之后做，非定时猜测；全程零 Thread.sleep。
 */
public class HostedExecutorsTest {

    /** 记录型假宿主：收集 onRemove 注册动作，sweep 由测试线程显式驱动（确定性）。 */
    static final class RecordingHost implements RemovalHost {
        final List<Runnable> actions = new CopyOnWriteArrayList<>();
        volatile boolean swept;

        @Override
        public void onRemove(Runnable action) {
            if (action == null) {
                throw new IllegalArgumentException("onRemove(null) 不允许——无动作可注册");
            }
            if (swept) {
                throw new RejectedExecutionException("host already swept (test)");
            }
            actions.add(action);
        }

        /** 模拟宿主拆卸：执行全部注册动作（真实宿主里由生命周期 chokepoint 驱动）。 */
        void sweep() {
            swept = true;
            for (Runnable action : actions) {
                action.run();
            }
        }
    }

    private final List<ExecutorService> pools = new CopyOnWriteArrayList<>();

    @After
    public void tearDown() {
        for (ExecutorService pool : pools) {
            pool.shutdownNow();
        }
        MDC.clear();
    }

    private ExecutorService newBounded(int threads, RemovalHost host) {
        ExecutorService pool = HostedExecutors.bounded(threads, host);
        pools.add(pool);
        return pool;
    }

    /** 有界等待 latch（统一超时预算，超时即失败——不静默放过未发生的事件）。 */
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("latch 未在预算内到达（事件未发生）", latch.await(10, TimeUnit.SECONDS));
    }

    /** 排队界 64：在飞占满 worker 后，前 64 个入队成功、第 65 个同步抛 REE。 */
    @Test(timeout = 15000)
    public void queueFull_rejectedWithRee() throws Exception {
        RecordingHost host = new RecordingHost();
        ExecutorService pool = newBounded(1, host);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(65);
        pool.execute(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        });
        await(started);                            // worker 已被在飞任务占住，队列空
        for (int i = 0; i < 64; i++) {
            pool.execute(finished::countDown);     // 填满排队界 64
        }
        try {
            pool.execute(finished::countDown);
            fail("排队满 64 后再提交应同步抛 RejectedExecutionException");
        } catch (RejectedExecutionException expected) {
            // 满拒=显式信号（镜像 KSE per-key 满拒语义）
        }
        release.countDown();
        await(finished);                           // 已受理的 65 个（1 在飞+64 排队）全部执行完
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }

    /** MDC：提交线程的上下文在 worker 执行时恢复（execute 与 submit 两路径同权）。 */
    @Test(timeout = 15000)
    public void mdcCapturedAtSubmit_restoredAtExecution() throws Exception {
        MDC.clear();                               // 隔离其他用例残留，断言才是全量等值
        Map<String, String> submitted = new HashMap<>();
        submitted.put(TraceContext.TRACE_ID_KEY, "01TESTTRACETESTTESTTE");
        submitted.put("coordinate", "com.ecat:integration-test");
        for (Map.Entry<String, String> e : submitted.entrySet()) {
            MDC.put(e.getKey(), e.getValue());
        }
        try {
            RecordingHost host = new RecordingHost();
            ExecutorService pool = newBounded(1, host);

            AtomicReference<Map<String, String>> seenByExecute = new AtomicReference<>();
            CountDownLatch executeDone = new CountDownLatch(1);
            pool.execute(() -> {
                seenByExecute.set(MDC.getCopyOfContextMap());
                executeDone.countDown();
            });
            await(executeDone);
            assertEquals("execute 路径应恢复提交时 MDC", submitted, seenByExecute.get());

            AtomicReference<Map<String, String>> seenBySubmit = new AtomicReference<>();
            Future<String> future = pool.submit(() -> {
                seenBySubmit.set(MDC.getCopyOfContextMap());
                return "done";
            });
            assertEquals("done", future.get(10, TimeUnit.SECONDS));
            assertEquals("submit 路径应恢复提交时 MDC", submitted, seenBySubmit.get());
        } finally {
            MDC.clear();
        }
    }

    /** 线程名=宿主类名@实例哈希-序号（谁的池/哪个实例零输入可辨）。 */
    @Test(timeout = 15000)
    public void threadNameDerivedFromHostClassAndIdentity() throws Exception {
        RecordingHost host = new RecordingHost();
        ExecutorService pool = newBounded(2, host);
        String expectedPrefix = RecordingHost.class.getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(host)) + "-";
        AtomicReference<String> firstSeen = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        pool.execute(() -> {
            firstSeen.set(Thread.currentThread().getName());
            done.countDown();
        });
        await(done);
        String name = firstSeen.get();
        assertNotNull(name);
        assertTrue("线程名应以派生前缀开头: " + name, name.startsWith(expectedPrefix));
    }

    /** ⑧a execute 路径：单任务异常记 warn 不上抛，后续任务继续跑（不杀 worker）。 */
    @Test(timeout = 15000)
    public void executeTaskThrows_workerSurvivesNextTaskRuns() throws Exception {
        RecordingHost host = new RecordingHost();
        ExecutorService pool = newBounded(1, host);
        pool.execute(() -> {
            throw new IllegalStateException("boom（应记 warn 续跑）");
        });
        CountDownLatch next = new CountDownLatch(1);
        pool.execute(next::countDown);
        await(next);   // 守卫镜像 KSE drain：异常不钉死后续任务
    }

    /** ⑧b submit 路径：Callable 异常原样进 future（不双重处理），正常值透传。 */
    @Test(timeout = 15000)
    public void submitCallableException_propagatesThroughFutureUntouched() throws Exception {
        RecordingHost host = new RecordingHost();
        ExecutorService pool = newBounded(1, host);

        Future<Integer> ok = pool.submit(() -> 42);
        assertEquals((Integer) 42, ok.get(10, TimeUnit.SECONDS));

        IllegalStateException boom = new IllegalStateException("bad-callable");
        Callable<Object> throwing = () -> {
            throw boom;                             // 显式 Callable（裸 throw lambda 在 submit 两重载间二义）
        };
        Future<Object> failed = pool.submit(throwing);
        try {
            failed.get(10, TimeUnit.SECONDS);
            fail("Callable 异常应经 future 透传");
        } catch (ExecutionException e) {
            assertTrue("future 应携带原异常（同引用，未被包装改写）", e.getCause() == boom);
        }
        // 异常任务之后池仍可用（与 execute 路径同一守卫，不杀 worker）
        CountDownLatch next = new CountDownLatch(1);
        pool.execute(next::countDown);
        await(next);
    }

    /** ⑨ threads&lt;1 / host=null 是调用错误：IllegalArgumentException，无默认值兜底。 */
    @Test(timeout = 15000)
    public void invalidArguments_rejected() {
        RecordingHost host = new RecordingHost();
        try {
            HostedExecutors.bounded(0, host);
            fail("threads=0 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue("threads=0 应在建池前拒绝（无注册副作用）", host.actions.isEmpty());
        }
        try {
            HostedExecutors.bounded(-1, host);
            fail("threads=-1 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 同上：调用错误非边界
        }
        try {
            HostedExecutors.bounded(1, null);
            fail("host=null 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 池必须有宿主可挂
        }
    }

    /** ⑩ 宿主 sweep=shutdownNow：在飞任务被中断（正向 latch 证事件发生），排队任务被丢弃
     *  （awaitTermination 后池已终态=再无执行可能，此时断言未跑才是确定性负向断言）。 */
    @Test(timeout = 15000)
    public void hostSweep_shutdownNow_interruptsInFlight_dropsQueued() throws Exception {
        RecordingHost host = new RecordingHost();
        ThreadPoolExecutor pool = (ThreadPoolExecutor) newBounded(1, host);

        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch queuedRan = new CountDownLatch(2);
        pool.execute(() -> {
            inFlight.countDown();
            try {
                new CountDownLatch(1).await();     // 阻塞占住唯一 worker，直到被中断
            } catch (InterruptedException e) {
                interrupted.countDown();           // shutdownNow 的中断到达在飞任务
            }
        });
        await(inFlight);
        pool.execute(queuedRan::countDown);        // 排队任务 2 个：sweep 后应被丢弃
        pool.execute(queuedRan::countDown);
        assertEquals("占位中：排队应有 2 个", 2, pool.getQueue().size());

        host.sweep();                              // 执行注册的 shutdownNow

        await(interrupted);                        // 事件已发生：在飞被中断
        assertTrue("sweep 后池应处于 shutdown 态", pool.isShutdown());
        assertEquals("shutdownNow 应已清空排队", 0, pool.getQueue().size());
        assertTrue("worker 终止超预算", pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals("池已终止后排队任务仍未跑（丢弃语义，非猜测）", 2, queuedRan.getCount());
    }

    /** 宿主已 sweep 后建池：注册被拒 REE 原样上抛（病态调用不静默收下）。 */
    @Test(timeout = 15000)
    public void alreadySweptHost_registrationRejectedWithRee() {
        RecordingHost host = new RecordingHost();
        host.swept = true;
        try {
            HostedExecutors.bounded(1, host);
            fail("宿主已 sweep 后建池应抛 RejectedExecutionException");
        } catch (RejectedExecutionException expected) {
            // 终态宿主上建池=病态调用，严格模式 reject
        }
    }
}
