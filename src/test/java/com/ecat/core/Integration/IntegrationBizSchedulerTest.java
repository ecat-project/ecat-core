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

package com.ecat.core.Integration;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.EcatCore;
import com.ecat.core.Task.TaskManager;
import com.ecat.core.Utils.Mdc.TraceContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 集成层业务计时器（S2 摆脱引擎）回归。
 *
 * <p>契约：{@link IntegrationBase#getBizScheduler()} 返回集成自持的普通业务
 * ScheduledExecutorService（ecat-biz-sched 具名守护线程、MDC 经 TraceContext 传播）。
 * W7 集中调度引擎退役后，这是 core 侧唯一的共享调度器——IO 轮询定时归各传输域 SDK
 * 自持（SerialSdkTimers/ModbusSdkTimers 等），不经本池；停机随 TaskManager.shutdownAll
 * 收编。
 *
 * <p>同步方式遵守测试纪律：全程 CountDownLatch 等待事件发生，不用 sleep。
 *
 * @author coffee
 */
public class IntegrationBizSchedulerTest {

    private TaskManager taskManager;

    @After
    public void tearDown() {
        if (taskManager != null) {
            taskManager.shutdownAll();
        }
    }

    /** 最小 IntegrationBase 子类：测试内直接注 core（protected 字段，包内可见）。 */
    private static final class TestIntegration extends IntegrationBase {
        void wire(EcatCore core) {
            this.core = core;
        }

        @Override
        public void onInit() {
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onPause() {
        }
    }

    private TestIntegration newIntegration(TaskManager tm) {
        EcatCore core = mock(EcatCore.class);
        when(core.getTaskManager()).thenReturn(tm);
        TestIntegration integration = new TestIntegration();
        integration.wire(core);
        return integration;
    }

    /**
     * 业务计时器单源（W7 终态）：getBizScheduler() 返回 TaskManager 的业务池本体
     * ——集中调度引擎退役后，集成侧计时入口与 core 侧 biz 池是同一实例（单源），
     * 设备/传输域的周期轮询不经此池（域 SDK 自持定时器）。
     */
    @Test
    public void 业务计时器单源() {
        taskManager = new TaskManager();
        TestIntegration integration = newIntegration(taskManager);

        ScheduledExecutorService biz = integration.getBizScheduler();

        assertNotNull(biz);
        assertSame("getBizScheduler 须返回 TaskManager 业务池（W7 后单源）",
            taskManager.getBizScheduler(), biz);
    }

    /** 业务线程具名（ecat-biz-sched-N）且为守护线程——线程普查可归因、不阻塞 JVM 退出。 */
    @Test
    public void 业务线程具名且守护() throws Exception {
        taskManager = new TaskManager();
        TestIntegration integration = newIntegration(taskManager);

        AtomicReference<Thread> worker = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        integration.getBizScheduler().execute(() -> {
            worker.set(Thread.currentThread());
            done.countDown();
        });
        assertTrue("业务任务未在时限内执行", done.await(10, TimeUnit.SECONDS));

        Thread t = worker.get();
        assertNotNull(t);
        assertTrue("业务线程名须以 ecat-biz-sched- 开头，实际=" + t.getName(),
            t.getName().startsWith("ecat-biz-sched-"));
        assertTrue("业务线程须为守护线程", t.isDaemon());
    }

    /**
     * MDC 语义与引擎一致：提交时上下文传播到任务线程；周期任务每次执行换新 traceId
     * （TraceContext.wrapPeriodicRunnable 契约）。
     */
    @Test
    public void MDC上下文传播且周期任务每次换traceId() throws Exception {
        taskManager = new TaskManager();
        TestIntegration integration = newIntegration(taskManager);

        MDC.put("bizTestCtx", "coord:test");
        try {
            int executions = 2;
            CountDownLatch done = new CountDownLatch(executions);
            Queue<String> traceIds = new ConcurrentLinkedQueue<>();
            AtomicReference<String> ctxSeen = new AtomicReference<>();
            ScheduledFuture<?> future = integration.getBizScheduler()
                .scheduleAtFixedRate(() -> {
                    ctxSeen.compareAndSet(null, MDC.get("bizTestCtx"));
                    traceIds.add(TraceContext.getTraceId());
                    done.countDown();
                }, 0, 10, TimeUnit.MILLISECONDS);
            try {
                assertTrue("周期任务未在时限内执行 " + executions + " 次",
                    done.await(10, TimeUnit.SECONDS));
            } finally {
                future.cancel(false);
            }

            assertEquals("提交时 MDC 上下文须传播到任务线程", "coord:test", ctxSeen.get());
            assertEquals(executions, traceIds.size());
            String first = traceIds.poll();
            String second = traceIds.poll();
            assertNotNull("任务线程须有 traceId（TraceContext 包装）", first);
            assertNotNull(second);
            assertEquals("traceId 须为 26 字符 ULID", 26, first.length());
            assertNotEquals("周期任务每次执行须换新 traceId", first, second);
        } finally {
            MDC.remove("bizTestCtx");
        }
    }

    /** 停机收编：TaskManager.shutdownAll 后业务池拒绝新任务并终止。 */
    @Test
    public void shutdownAll收编业务池() throws Exception {
        taskManager = new TaskManager();
        TestIntegration integration = newIntegration(taskManager);
        ScheduledExecutorService biz = integration.getBizScheduler();

        taskManager.shutdownAll();

        assertTrue("业务池须在 shutdownAll 后终止",
            biz.awaitTermination(10, TimeUnit.SECONDS));
        try {
            biz.execute(() -> { });
            fail("shutdownAll 后提交须被拒绝（严格模式，不静默丢）");
        } catch (RejectedExecutionException expected) {
            // 预期：池已关闭
        }
    }
}
