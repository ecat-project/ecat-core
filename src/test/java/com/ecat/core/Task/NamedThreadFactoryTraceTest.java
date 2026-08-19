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

import com.ecat.core.Utils.Mdc.MdcExecutorService;
import com.ecat.core.Utils.Mdc.TraceContext;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * NamedThreadFactory 基线 traceId 覆盖（arch-review 25 号杠杆③）回归。
 *
 * <p>契约：
 * <ul>
 *   <li>工厂创建的线程起步即有 26 字符 ULID traceId（日志不再空槽）</li>
 *   <li>与 {@link MdcExecutorService} 的任务级 MDC 包装组合不双包：
 *       任务内自带 traceId 优先，任务结束后回到线程基线 id，且不同任务互不串</li>
 * </ul>
 *
 * @author coffee
 */
public class NamedThreadFactoryTraceTest {

    private static final long AWAIT_SECONDS = 10;

    @Test
    public void factoryThread_hasUlidTraceIdAtStart() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new NamedThreadFactory("trace-probe").newThread(() -> {
            seen.set(TraceContext.getTraceId());
            done.countDown();
        });
        t.start();
        assertTrue("线程未在时限内完成", done.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        t.join(TimeUnit.SECONDS.toMillis(AWAIT_SECONDS));

        String id = seen.get();
        assertNotNull("工厂线程起步应已有 traceId（原为 null=红）", id);
        assertEquals("traceId 应为 26 字符 ULID", 26, id.length());
    }

    @Test
    public void twoFactoryThreads_haveDistinctTraceIds() throws Exception {
        AtomicReference<String> a = new AtomicReference<>();
        AtomicReference<String> b = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(2);
        NamedThreadFactory factory = new NamedThreadFactory("trace-pair");
        factory.newThread(() -> { a.set(TraceContext.getTraceId()); done.countDown(); }).start();
        factory.newThread(() -> { b.set(TraceContext.getTraceId()); done.countDown(); }).start();
        assertTrue(done.await(AWAIT_SECONDS, TimeUnit.SECONDS));

        assertNotNull(a.get());
        assertNotNull(b.get());
        assertNotEquals("不同线程基线 id 应不同", a.get(), b.get());
    }

    /**
     * 组合语义（防双包/防串）：MdcExecutorService 的任务包装在工厂线程上仍按任务粒度生效——
     * 任务 A 的 traceId 不泄漏到任务 B；无包装任务回落到线程基线 id。
     */
    @Test
    public void mdcWrappedTask_keepsTaskLevelTraceAndFallsBackToBaseline() throws Exception {
        NamedThreadFactory factory = new NamedThreadFactory("trace-pool");
        ExecutorService pool = MdcExecutorService.wrap(Executors.newSingleThreadExecutor(factory));
        try {
            AtomicReference<String> taskA = new AtomicReference<>();
            AtomicReference<String> taskB = new AtomicReference<>();
            AtomicReference<String> bare = new AtomicReference<>();
            CountDownLatch phaseA = new CountDownLatch(1);
            CountDownLatch phaseB = new CountDownLatch(1);
            CountDownLatch phaseC = new CountDownLatch(1);

            // 任务 A：提交线程无 traceId，MdcExecutorService 包装会为其生成任务级 id
            TraceContext.clearTraceId();
            pool.execute(TraceContext.wrapRunnable(() -> {
                taskA.set(TraceContext.getTraceId());
                phaseA.countDown();
            }));
            assertTrue(phaseA.await(AWAIT_SECONDS, TimeUnit.SECONDS));

            // 任务 B：同款包装，独立任务级 id，不串 A 的
            TraceContext.clearTraceId();
            pool.execute(TraceContext.wrapRunnable(() -> {
                taskB.set(TraceContext.getTraceId());
                phaseB.countDown();
            }));
            assertTrue(phaseB.await(AWAIT_SECONDS, TimeUnit.SECONDS));

            // 裸任务：无任何包装，应看到工厂基线 id（任务级恢复不残留）
            pool.execute(() -> {
                bare.set(TraceContext.getTraceId());
                phaseC.countDown();
            });
            assertTrue(phaseC.await(AWAIT_SECONDS, TimeUnit.SECONDS));

            assertNotNull(taskA.get());
            assertNotNull(taskB.get());
            assertNotEquals("任务级 traceId 不应跨任务泄漏", taskA.get(), taskB.get());
            assertNotNull("裸任务应回落到工厂基线 id", bare.get());
            assertNotEquals("基线 id 不应等于任务 A 的 id（双包会让任务 id==基线）", taskA.get(), bare.get());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS));
        }
    }
}
