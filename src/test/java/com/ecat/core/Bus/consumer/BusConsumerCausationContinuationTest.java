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

package com.ecat.core.Bus.consumer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * 消费侧因果续传测试（14 号架构 §5.4-3）：onEvent 在发布线程捕获 traceId（ULID）随载荷入队，
 * 消费线程取出时 restore 进自身 MDC——consume 内日志与再发布事件指向同一因果锚点；
 * 发布线程无 traceId 时消费线程 MDC 的 traceId 被清空（如实缺失，不残留上一条的旧 id）。
 *
 * <p>latch 同步消费完成，不做 sleep 等待。
 */
public class BusConsumerCausationContinuationTest {

    /** 记录 consume 时 MDC traceId 的探针消费者；每次消费 countDown 对应序号的 latch。 */
    private static final class ProbeConsumer extends AbstractBusConsumer<String> {
        private final CountDownLatch[] latches;
        private final AtomicInteger round = new AtomicInteger();
        volatile String lastSeenTraceId;

        ProbeConsumer(CountDownLatch... latches) {
            super("causation-probe", 8);
            this.latches = latches;
        }

        @Override
        protected void consume(String event) {
            lastSeenTraceId = TraceContext.getTraceId();
            int index = round.getAndIncrement();
            if (index < latches.length) {
                latches[index].countDown();
            }
        }
    }

    @Test
    public void consumerThreadSeesPublishersTraceId() throws Exception {
        CountDownLatch consumed = new CountDownLatch(1);
        // 哨兵轮 latch：evt-2 的 consume 完成蕴含 evt-1 的 noteProcessed 已跑（worker 串行：
        // consume(evt-1) → noteProcessed(evt-1) → consume(evt-2)），使 processed 计数断言
        // 确定性成立——countDown 在 consume 内、计数在 consume 返回后，单轮 latch 与计数
        // 断言间存在固有竞态（bug-record-20260828-004500，负载下可复现）
        CountDownLatch sentinelConsumed = new CountDownLatch(1);
        ProbeConsumer consumer = new ProbeConsumer(consumed, sentinelConsumed);
        MDC.clear();
        Thread publisher = new Thread(() -> {
            TraceContext.setTraceId(TraceContext.generateTraceId());
            consumer.onEvent("evt-1");
            consumer.onEvent("evt-2");
        }, "publish-thread");
        publisher.start();
        publisher.join(5_000L);

        assertTrue("哨兵轮 consume 应完成（latch 同步，不 sleep）", sentinelConsumed.await(5, TimeUnit.SECONDS));
        assertThat("消费线程 MDC traceId 应为发布线程的 26 字符 ULID",
                consumer.lastSeenTraceId == null ? 0 : consumer.lastSeenTraceId.length(), is(26));
        assertThat("处理计数须计入", consumer.getProcessedCount() >= 1, is(true));
        consumer.shutdown();
    }

    @Test
    public void consumerThreadClearsTraceIdWhenPublisherHasNone() throws Exception {
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        ProbeConsumer consumer = new ProbeConsumer(first, second);

        // 第一条：带 traceId（制造消费线程上的 traceId 残留）
        TraceContext.setTraceId(TraceContext.generateTraceId());
        consumer.onEvent("evt-with-trace");
        assertTrue(first.await(5, TimeUnit.SECONDS));

        // 第二条：发布线程无 traceId——消费线程必须清掉残留，不得把上一条的 id 算到这一条头上
        Thread publisher = new Thread(() -> {
            MDC.clear();
            consumer.onEvent("evt-no-trace");
        }, "publish-thread-2");
        publisher.start();
        publisher.join(5_000L);

        assertTrue(second.await(5, TimeUnit.SECONDS));
        assertThat("发布线程无 traceId 时消费线程 traceId 须为 null（不残留上一条）",
                consumer.lastSeenTraceId, nullValue());
        consumer.shutdown();
    }

    @Test
    public void queueCapacityExposedForHealthEndpoint() {
        CountDownLatch unused = new CountDownLatch(1);
        ProbeConsumer consumer = new ProbeConsumer(unused);
        assertThat("容量 = size + remainingCapacity", consumer.getQueueCapacity(), is(8));
        consumer.shutdown();
    }
}
