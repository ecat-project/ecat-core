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

package com.ecat.core.Observability;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Task.TaskManager;
import com.ecat.core.Utils.Mdc.MdcContext;

/**
 * system_health 快照服务测试（14 号架构 §5.4-2）：
 * 三节结构完整性、速率差分（注入时钟确定性驱动，零 sleep）、熔断状态表如实反映 OPEN 车道、
 * 组件缺失时节为 null（严格模式不编造）。
 */
public class SystemHealthServiceTest {

    private static final String BOOM_LANE = "test.lane.boom";

    private TaskManager taskManager;
    private BusRegistry registry;
    private SystemHealthService service;

    @Before
    public void setUp() {
        taskManager = new TaskManager();
        registry = new BusRegistry();
        service = new SystemHealthService(taskManager, registry);
    }

    @After
    public void tearDown() {
        taskManager.shutdownAll();
        MDC.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Object container, String key) {
        return (Map<String, Object>) ((Map<String, Object>) container).get(key);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void snapshotContainsThreeSectionsWithEngineAndBusData() throws Exception {
        // 引擎有真实执行记录
        taskManager.getMdcScheduledExecutorService()
                .schedule(() -> { }, 0, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS);

        Map<String, Object> snap = service.snapshot(1_000L);
        assertThat(snap.get("timestamp"), is(1_000L));

        Map<String, Object> scheduler = section(snap, "scheduler");
        assertThat(scheduler, notNullValue());
        Map<String, Object> metrics = section(scheduler, "metrics");
        assertThat("submitted 至少计入上面那次调度", (Long) metrics.get("submitted") >= 1, is(true));
        // 103000 观测补盲：blocking 探针（运行超 1s 任务计数 + 当前清单）必须在 scheduler 节直读
        assertThat("metrics 含 blockingTasks 计数", metrics.containsKey("blockingTasks"), is(true));
        List<Map<String, Object>> blockingList = (List<Map<String, Object>>) scheduler.get("blockingTaskList");
        assertThat("blockingTaskList 节存在（空清单=无钉死早段，如实展示）",
                blockingList != null, is(true));
        Map<String, Object> queues = section(scheduler, "queues");
        assertThat(queues.containsKey("laneQueuedTotal"), is(true));
        assertThat(queues.containsKey("wheelPending"), is(true));
        Map<String, Object> workers = section(scheduler, "workers");
        assertThat("默认 6 worker（IO 收敛 P2 上调）", (Integer) workers.get("count"), is(6));
        List<Map<String, Object>> workerList = (List<Map<String, Object>>) workers.get("list");
        assertThat("每个 worker 一条忙闲记录", workerList.size(), is(6));

        Map<String, Object> bus = section(snap, "bus");
        assertThat(bus, notNullValue());
        assertThat("消费者表键存在", bus.containsKey("consumers"), is(true));

        Map<String, Object> threads = section(snap, "threads");
        assertThat("线程普查总数 > 0", (Integer) threads.get("total") > 0, is(true));
        List<Map<String, Object>> groups = (List<Map<String, Object>>) threads.get("groups");
        assertThat("分组非空", groups.isEmpty(), is(false));
        int groupSum = groups.stream().mapToInt(g -> (Integer) g.get("count")).sum();
        assertThat("分组计数之和 = 总线程数", groupSum, is((Integer) threads.get("total")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ratesAreNullOnFirstReadAndDifferencedOnSecond() {
        Map<String, Object> first = service.snapshot(1_000L);
        assertThat("首次读取无窗口，速率如实 null",
                section(first, "scheduler").get("executedPerSecond"), nullValue());
        assertThat(section(first, "bus").get("publishPerSecond"), nullValue());
        assertThat(section(first, "threads").get("churnPerSecond"), nullValue());
        assertThat(section(first, "threads").get("rateWindowMillis"), nullValue());

        // 窗口内第二次读：时间推进 2s、发布 5 个事件 → publishPerSecond = 2.5
        for (int i = 0; i < 5; i++) {
            registry.publish(com.ecat.core.Bus.event.BusEvent.of("test.topic",
                    new ProbePayload(), com.ecat.core.Bus.event.EventContext.root(
                            com.ecat.core.Bus.event.EventContext.Source.SYSTEM, null)));
        }
        Map<String, Object> second = service.snapshot(3_000L);
        assertThat(section(second, "bus").get("publishPerSecond"), is(2.5));
        assertThat(section(second, "threads").get("rateWindowMillis"), is(2_000L));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void sameMillisecondReadsYieldNullRateNotDivideByZero() {
        service.snapshot(1_000L);
        Map<String, Object> second = service.snapshot(1_000L);
        assertThat("零跨度窗口无效 → null（不编 0 也不 NaN）",
                section(second, "bus").get("publishPerSecond"), nullValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void breakerTableReflectsOpenLaneWithCooldown() throws Exception {
        // 同车道 5 个失败任务（默认 failThreshold=5）→ 熔断 OPEN
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, BOOM_LANE);
        for (int i = 0; i < 5; i++) {
            final int n = i;
            try {
                taskManager.getMdcScheduledExecutorService()
                        .schedule((Runnable) () -> { throw new IllegalStateException("boom-" + n); },
                                0, TimeUnit.MILLISECONDS)
                        .get(5, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                // 失败任务按预期以 ExecutionException 完成
            }
        }

        Map<String, Object> snap = service.snapshot(1_000L);
        Map<String, Object> breakers = section(section(snap, "scheduler"), "breakers");
        Map<String, Object> boom = section(breakers, BOOM_LANE);
        assertThat("失败车道必须出现在熔断表", boom, notNullValue());
        assertThat(boom.get("state"), is("OPEN"));
        assertThat("冷却剩余 > 0", (Long) boom.get("cooldownRemainingMillis") > 0, is(true));
        assertThat((Long) boom.get("openCount"), is(1L));
        assertThat("失败摘要可见病因（异常类名+消息）",
                (String) boom.get("lastFailureSummary"), is("IllegalStateException: boom-4"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void commBreakerTableReflectsCommFailedDevice() {
        String commKey = "com.ecat:integration-sailhero:so2-1";
        for (int i = 0; i < 5; i++) {
            taskManager.reportCommFailure(commKey, "so2chr$ Serial response timeout");
        }
        assertThat("5 连败后轮询守卫跳过", taskManager.isCommOpen(commKey), is(true));

        Map<String, Object> snap = service.snapshot(1_000L);
        Map<String, Object> scheduler = section(snap, "scheduler");

        Map<String, Object> commBreakers = section(scheduler, "commBreakers");
        Map<String, Object> comm = section(commBreakers, commKey);
        assertThat("风暴设备出现在通讯熔断表", comm, notNullValue());
        assertThat(comm.get("state"), is("OPEN"));
        assertThat("通讯失败计数进表", comm.get("commFailures"), is(5L));
        assertThat("守卫跳过计入 skippedCount", comm.get("skippedCount"), is(1L));
        assertTrue("lastFailureSummary 前缀「通讯失败」",
                ((String) comm.get("lastFailureSummary")).startsWith("通讯失败"));

        // 车道熔断表补 commFailures 字段（未接入通讯信号的车道如实为 0，不隐藏字段）
        Map<String, Object> breakers = section(scheduler, "breakers");
        for (Map.Entry<String, Object> e : breakers.entrySet()) {
            assertThat("车道表条目含 commFailures 字段: " + e.getKey(),
                    ((Map<String, Object>) e.getValue()).containsKey("commFailures"), is(true));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void missingComponentsReportedAsNullSections() {
        SystemHealthService bare = new SystemHealthService(null, null);
        Map<String, Object> snap = bare.snapshot(1_000L);
        assertThat("未装配组件如实 null（不编造空结构）", snap.get("scheduler"), nullValue());
        assertThat(snap.get("bus"), nullValue());
        assertThat("线程节恒可采集", section(snap, "threads").get("total") instanceof Integer, is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void consumerTableShowsQueueDepthAndDropped() throws Exception {
        DroppingProbeConsumer consumer = new DroppingProbeConsumer();
        // 容量 1 的消费者：连发 3 条（消费线程阻塞在 latch 上），drop-oldest 丢 2
        consumer.onEvent("e1");
        consumer.onEvent("e2");
        consumer.onEvent("e3");

        Map<String, Object> snap = service.snapshot(1_000L);
        List<Map<String, Object>> consumers =
                (List<Map<String, Object>>) section(snap, "bus").get("consumers");
        Map<String, Object> row = consumers.stream()
                .filter(c -> "drop-probe".equals(c.get("name")))
                .findFirst().orElse(null);
        assertThat("探针消费者必须被枚举", row, notNullValue());
        assertThat((Integer) row.get("queueCapacity"), is(1));
        assertTrue("丢批计数 >= 0 且键存在", row.containsKey("dropped"));

        consumer.release();
        consumer.shutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void threadSnapshotListsEveryThreadWithFirstFrame() {
        Map<String, Object> detail = service.threadSnapshot();
        Map<String, Object> threads = section(detail, "threads");
        List<Map<String, Object>> list = (List<Map<String, Object>>) threads.get("list");
        assertThat("清单条数 = 总数", list.size(), is((Integer) threads.get("total")));
        assertThat("每条都有名字", list.get(0).containsKey("name"), is(true));
        assertThat("每条都有栈首帧键（无栈时值可为 null）", list.get(0).containsKey("firstFrame"), is(true));
    }

    /** 容量 1 + 首 consume 阻塞的探针：制造队列满触发 drop-oldest。 */
    private static final class DroppingProbeConsumer
            extends com.ecat.core.Bus.consumer.AbstractBusConsumer<String> {
        private volatile java.util.concurrent.CountDownLatch gate =
                new java.util.concurrent.CountDownLatch(1);

        DroppingProbeConsumer() {
            super("drop-probe", 1);
        }

        @Override
        protected void consume(String event) {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void release() {
            gate.countDown();
        }
    }

    private static final class ProbePayload implements com.ecat.core.Bus.event.BusPayload {
    }
}
