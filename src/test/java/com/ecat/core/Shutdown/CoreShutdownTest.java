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

package com.ecat.core.Shutdown;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.ecat.core.Bus.consumer.AbstractBatchBusConsumer;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationDeviceBase;
import com.ecat.core.Integration.IntegrationRegistry;
import com.ecat.core.State.StateManager;
import com.ecat.core.Task.TaskManager;

/**
 * core 停机序列端到端语义测试（真实 TaskManager/StateManager/IntegrationRegistry + 假集成）：
 * ①设备 onPause（源）先于服务 onPause（drain）——设备终态事件被消费者存活期间吸收并在 drain 落库
 * （零丢尾顺序依据的行为级锁定）；②业务计时器优雅收敛（在跑一次性任务跑完、池 TERMINATED；
 * W7 引擎退役后 quiesce 的等待对象）；③onRelease/线程池收尾执行；④init 未完成（部件为
 * null）时安全降级。
 */
public class CoreShutdownTest {

    /** 记录 flush 内容的假批量消费者。 */
    private static final class RecordingBatchConsumer extends AbstractBatchBusConsumer<String> {
        final List<String> flushed = new CopyOnWriteArrayList<>();

        RecordingBatchConsumer() {
            super("core-shutdown-test", 100, 100, 60_000L);
        }

        @Override
        protected void flush(List<String> batch) {
            flushed.addAll(batch);
        }
    }

    /** 设备型假集成：onPause 记序 + 发布终态事件（模拟设备停轮询时最后一批属性上报）。
     *  非 final：排序用例以匿名子类覆写 onPause。 */
    private static class FakeDeviceIntegration extends IntegrationDeviceBase {
        private final RecordingBatchConsumer downstream;
        private final List<String> journal;

        FakeDeviceIntegration(RecordingBatchConsumer downstream, List<String> journal) {
            this.downstream = downstream;
            this.journal = journal;
        }

        @Override
        public void onPause() {
            journal.add("device-pause");
            // 设备收尾自身发布终态事件——必须发生在消费者 drain 之前（顺序依据的核心场景）
            downstream.onEvent("terminal-1");
            downstream.onEvent("terminal-2");
            super.onPause();
        }
    }

    /** 服务型假集成：onPause 即停订阅 + drain flush（env-data-handle 等服务集成的 onPause 契约）。
     *  非 final：排序用例以匿名子类覆写 onPause。 */
    private static class FakeServiceIntegration extends IntegrationBase {
        private final RecordingBatchConsumer consumer;
        private final List<String> journal;

        FakeServiceIntegration(RecordingBatchConsumer consumer, List<String> journal) {
            this.consumer = consumer;
            this.journal = journal;
        }

        @Override
        public void onInit() { }

        @Override
        public void onStart() { }

        @Override
        public void onPause() {
            journal.add("service-drain");
            consumer.shutdown();
        }

        @Override
        public void onRelease() {
            journal.add("service-release");
        }
    }

    @Test
    public void devicePausedBeforeConsumerDrainAndTerminalEventsAreFlushed() {
        TaskManager taskManager = new TaskManager();
        StateManager stateManager = new StateManager();   // 无持久化（baseDir null）：state-flush 阶段空跑
        RecordingBatchConsumer consumer = new RecordingBatchConsumer();
        List<String> journal = new CopyOnWriteArrayList<>();

        // 停机前已有的尾批（低流量场景：不足以触发批量/定时 flush）
        consumer.onEvent("tail-before-shutdown");

        // biz 池上挂真实任务：验证 scheduler-quiesce 优雅收敛（在跑任务不被中断地跑完、周期停排）
        CountDownLatch inFlightRan = new CountDownLatch(1);
        taskManager.getBizScheduler()
                .scheduleWithFixedDelay(() -> { }, 10L, 10L, TimeUnit.MILLISECONDS);
        // execute()=零延迟直通：任务进入在跑状态后再停机——quiesce 必须等它跑完
        taskManager.getBizScheduler().execute(() -> {
            sleepQuietly(150L); // 模拟在跑 IO（被测对象本身，非测试同步）
            inFlightRan.countDown();
        });

        IntegrationRegistry registry = new IntegrationRegistry();
        registry.register("com.ecat:z-service", new FakeServiceIntegration(consumer, journal));
        registry.register("com.ecat:a-device", new FakeDeviceIntegration(consumer, journal));

        ShutdownOrchestrator.ShutdownReport report =
                CoreShutdown.forRegistries(taskManager, stateManager, registry).run();

        // ①顺序：设备 onPause（源先停）→ 服务 onPause（drain）→ release
        assertThat(journal, is(Arrays.asList("device-pause", "service-drain", "service-release")));

        // ②零丢尾：停机前尾批 + 设备终态事件全部被 flush（drain 在设备收尾之后的直接证据）
        assertThat(consumer.flushed, is(Arrays.asList(
                "tail-before-shutdown", "terminal-1", "terminal-2")));

        // ③业务计时器优雅收敛 + 线程池收尾：在跑任务（150ms）在 quiesce 预算内跑完、未被中断
        assertThat("SHUTDOWN 应优雅收敛 biz 池（在跑任务跑完、周期停排）",
                taskManager.getBizScheduler().isTerminated(), is(true));
        assertThat("停机序列应全部完成: " + report.getStages(), report.allCompleted(), is(true));
        assertThat("scheduler-quiesce 阶段应 COMPLETED",
                report.find("scheduler-quiesce").getOutcome(),
                is(ShutdownOrchestrator.StageOutcome.COMPLETED));
        // 在跑任务未被中断的证据：引擎 TERMINATED 前它必然已 countDown（确定性，无等待）
        assertThat(inFlightRan.getCount(), is(0L));
    }

    /** EcatCore.init 未完成即停机（部件 null）：跳过对应阶段，不抛 NPE，停机仍完整退出。 */
    @Test
    public void nullComponentsDegradeGracefully() {
        ShutdownOrchestrator.ShutdownReport report = CoreShutdown.forRegistries(null, null, null).run();

        assertThat(report.allCompleted(), is(true));
        // 仅剩三个生命周期阶段（无引擎/状态/线程池可收）
        assertThat(report.getStages().size(), is(3));
        assertTrue(report.find("device-wind-down") != null);
        assertTrue(report.find("bus-drain") != null);
        assertTrue(report.find("release-integrations") != null);
    }

    /** 分区快照：设备型/服务型各归其位且按坐标排序（注册顺序与执行顺序解耦）。 */
    @Test
    public void registryPartitionSortsByCoordinate() {
        List<String> journal = new CopyOnWriteArrayList<>();
        RecordingBatchConsumer consumer = new RecordingBatchConsumer();

        FakeServiceIntegration serviceB = new FakeServiceIntegration(consumer, journal) {
            @Override
            public void onPause() {
                journal.add("pause:service-b");
            }
        };
        FakeDeviceIntegration deviceY = new FakeDeviceIntegration(consumer, journal) {
            @Override
            public void onPause() {
                journal.add("pause:device-y");
            }
        };
        FakeDeviceIntegration deviceX = new FakeDeviceIntegration(consumer, journal) {
            @Override
            public void onPause() {
                journal.add("pause:device-x");
            }
        };

        IntegrationRegistry registry = new IntegrationRegistry();
        registry.register("com.ecat:m-service-b", serviceB);
        registry.register("com.ecat:z-device-y", deviceY);
        registry.register("com.ecat:a-device-x", deviceX);

        // 只跑到两个 pause 阶段：给一个消费完即返回的假 stateManager/taskManager 不必要，
        // 直接跑全序列，断言 pause 顺序即可
        CoreShutdown.forRegistries(null, null, registry).run();

        // 设备组内按坐标 a < z，且整组先于服务组（源先停、汇后停）；release 阶段条目在其后，不参与本断言
        assertThat("前三个动作应体现分区与组内排序",
                journal.subList(0, 3), is(Arrays.asList("pause:device-x", "pause:device-y", "pause:service-b")));
    }

    /** biz 池真实优雅停机语义（对 scheduler-quiesce 阶段的直接验证）：在跑任务不被中断地跑完、周期停排。
     *
     * <p>注：W7 引擎退役后 quiesce 的等待对象是业务计时器（原引擎 SHUTDOWN 语义随引擎消亡）；
     * 此处锁 C2 需要的契约——在跑任务跑完不被中断，一次性排队任务由 JDK shutdown 语义跑完。 */
    @Test
    public void schedulerQuiesceLetsInflightTaskFinishWithoutInterrupt() {
        TaskManager taskManager = new TaskManager();
        try {
            java.util.concurrent.ScheduledExecutorService biz = taskManager.getBizScheduler();
            CountDownLatch inFlightRan = new CountDownLatch(1);
            biz.execute(() -> {
                sleepQuietly(150L); // 模拟在跑 IO（被测对象本身，非测试同步）
                inFlightRan.countDown();
            });
            biz.scheduleWithFixedDelay(() -> { }, 10L, 10L, TimeUnit.MILLISECONDS);

            CoreShutdown.forRegistries(taskManager, null, null).run();

            assertThat("在跑任务应跑完而非被中断", inFlightRan.getCount(), is(0L));
            assertThat(biz.isTerminated(), is(true));
        } finally {
            taskManager.shutdownAll();
        }
    }

    /** 预算组不变量（2026-08-16 终验 20.8s 停机的实测驱动，依据见各常量注释）：
     *  ①阶段预算之和 == 总预算——最坏情况（各阶段全部吃满卡死上限）每个阶段仍拿全额份额，
     *    数据关键阶段 bus-drain/state-flush 不会被前段耗尽总额而级联 SKIPPED；
     *  （②原「quiesce ≥ 引擎在跑任务硬超时」随 W7 引擎退役删——硬超时语义随引擎消亡，
     *    quiesce 预算依据见 CoreShutdown.SCHEDULER_QUIESCE_MS 注释。）
     *  ③wind-down/bus-drain 预算容得下 2 个 stuck 份额 + 健康路径（单集成份额防饿死的配套）。 */
    @Test
    public void budgetCompositionCoversWorstCaseWithoutStageStarvation() {
        long stageSum = CoreShutdown.SCHEDULER_QUIESCE_MS + CoreShutdown.DEVICE_WIND_DOWN_MS
                + CoreShutdown.BUS_DRAIN_MS + CoreShutdown.STATE_FLUSH_MS
                + CoreShutdown.RELEASE_INTEGRATIONS_MS + CoreShutdown.POOLS_SHUTDOWN_MS;
        assertEquals("阶段预算之和应等于总预算（最坏情况无级联 SKIPPED）",
                CoreShutdown.TOTAL_BUDGET_MS, stageSum);
        assertTrue("device-wind-down 预算应容下 2 个 stuck 设备集成份额 + 健康收尾",
                CoreShutdown.DEVICE_WIND_DOWN_MS >= 2 * CoreShutdown.PER_INTEGRATION_DEVICE_PAUSE_MS);
        assertTrue("bus-drain 预算应容下 2 个 stuck 服务消费者份额 + 健康排空",
                CoreShutdown.BUS_DRAIN_MS >= 2 * CoreShutdown.PER_INTEGRATION_SERVICE_PAUSE_MS);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
