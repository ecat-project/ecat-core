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

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Test;

import com.ecat.core.Bus.consumer.AbstractBatchBusConsumer;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Utils.SchedulerClock;

/**
 * 集成生命周期阶段测试：
 * ①drain 真把队列尾批 flush——真实 {@link AbstractBatchBusConsumer} + 假事件断言 flush 内容
 * （重启零丢尾的核心原语）；②onPause 卡死时有界放弃（WARN + 继续）；③onPause 抛异常不阻断后续集成。
 *
 * <p>阶段内 future.get(timeout) 的有界等待是被测对象本身（停机编排既有口径），
 * 非测试同步；测试同步全部用 latch/报告返回值（禁 sleep）。
 */
public class IntegrationLifecycleStageTest {

    /** 记录 flush 内容的假批量消费者（真骨架，只换 flush 目标）。 */
    private static final class RecordingBatchConsumer extends AbstractBatchBusConsumer<String> {
        final List<String> flushed = new CopyOnWriteArrayList<>();

        RecordingBatchConsumer() {
            // batchSize=100 / flushInterval=60s：不触发改批/定时 flush，事件只能靠 shutdown drain——
            // 正是要验证的「尾批」形态
            super("recording", 100, 100, 60_000L);
        }

        @Override
        protected void flush(List<String> batch) {
            flushed.addAll(batch);
        }
    }

    /** onPause 关闭消费者的假服务集成（模拟 env-data-handle 等服务型集成的 onPause 契约）。 */
    private static final class ConsumerHostIntegration extends IntegrationBase {
        final RecordingBatchConsumer consumer;
        final List<String> journal;

        ConsumerHostIntegration(RecordingBatchConsumer consumer, List<String> journal) {
            this.consumer = consumer;
            this.journal = journal;
        }

        @Override
        public void onInit() { }

        @Override
        public void onStart() { }

        @Override
        public void onPause() {
            journal.add("pause:consumer-host");
            consumer.shutdown();
        }
    }

    @Test
    public void drainFlushesQueuedTailBatchViaConsumerShutdown() {
        RecordingBatchConsumer consumer = new RecordingBatchConsumer();
        List<String> journal = new ArrayList<>();
        ConsumerHostIntegration host = new ConsumerHostIntegration(consumer, journal);

        // 7 条事件全部低于 batchSize、远离 flushInterval：停机前它们只能滞留 队列/buffer（尾批）
        for (int i = 1; i <= 7; i++) {
            consumer.onEvent("e" + i);
        }

        IntegrationLifecycleStage stage = new IntegrationLifecycleStage(
                "bus-drain", 5_000L, 5_000L,
                IntegrationLifecycleStage.LifecycleAction.PAUSE, () -> Arrays.asList(host));

        boolean completed = stage.execute(
                System.nanoTime() + 5_000_000_000L, SchedulerClock.SYSTEM);

        assertThat(completed, is(true));
        assertThat(journal, is(Arrays.asList("pause:consumer-host")));
        assertThat("队列+buffer 残留应被 drain 并按序 flush（零丢尾）",
                consumer.flushed, is(Arrays.asList("e1", "e2", "e3", "e4", "e5", "e6", "e7")));
    }

    /** onPause 永不返回（模拟对失联对端的优雅断开）：有界放弃并继续下一个集成。 */
    @Test
    public void stuckOnPauseIsAbandonedAfterBudgetAndNextIntegrationStillRuns() {
        List<String> journal = new CopyOnWriteArrayList<>();
        CountDownLatch neverReleased = new CountDownLatch(1);

        IntegrationBase stuck = new IntegrationBase() {
            @Override
            public void onInit() { }

            @Override
            public void onStart() { }

            @Override
            public void onPause() {
                journal.add("pause:stuck");
                try {
                    neverReleased.await(); // 卡死直到被中断
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 响应中断退出，模拟可中断的阻塞 IO
                }
            }
        };
        IntegrationBase healthy = new IntegrationBase() {
            @Override
            public void onInit() { }

            @Override
            public void onStart() { }

            @Override
            public void onPause() {
                journal.add("pause:healthy");
            }
        };

        IntegrationLifecycleStage stage = new IntegrationLifecycleStage(
                "bus-drain", 2_000L, 300L,
                IntegrationLifecycleStage.LifecycleAction.PAUSE, () -> Arrays.asList(stuck, healthy));

        long start = System.nanoTime();
        boolean completed = stage.execute(start + 2_000_000_000L, SchedulerClock.SYSTEM);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertThat(completed, is(false));
        assertThat("stuck 被放弃后 healthy 仍执行", journal, hasItems("pause:stuck", "pause:healthy"));
        // 不硬等的硬证据：stuck 只吃掉自己 300ms 份额，整体远小于「stuck 无限等」
        assertTrue("stage 应在 stuck 单集成份额后返回而非硬等（elapsedMs=" + elapsedMillis + "）",
                elapsedMillis < 1_500L);
    }

    /** onPause 抛异常：记 ERROR 后继续下一个集成（停机不因单集成失败而中断）。 */
    @Test
    public void failingOnPauseDoesNotBlockLaterIntegrations() {
        List<String> journal = new CopyOnWriteArrayList<>();

        IntegrationBase failing = new IntegrationBase() {
            @Override
            public void onInit() { }

            @Override
            public void onStart() { }

            @Override
            public void onPause() {
                journal.add("pause:failing");
                throw new IllegalStateException("onPause-boom");
            }
        };
        IntegrationBase healthy = new IntegrationBase() {
            @Override
            public void onInit() { }

            @Override
            public void onStart() { }

            @Override
            public void onPause() {
                journal.add("pause:healthy");
            }
        };

        IntegrationLifecycleStage stage = new IntegrationLifecycleStage(
                "bus-drain", 5_000L, 5_000L,
                IntegrationLifecycleStage.LifecycleAction.PAUSE, () -> Arrays.asList(failing, healthy));

        boolean completed = stage.execute(
                System.nanoTime() + 5_000_000_000L, SchedulerClock.SYSTEM);

        assertThat(completed, is(false));
        assertThat(journal, is(Arrays.asList("pause:failing", "pause:healthy")));
    }
}
