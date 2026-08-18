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

package com.ecat.core.Bus;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.Bus.consumer.AbstractBusConsumer;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.BusPayload;
import com.ecat.core.Bus.event.EventContext;
import com.ecat.core.Task.TaskManager;
import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * 因果链三跳端到端测试（14 号架构 §5.4-3）：poll 任务（调度引擎周期执行）→ bus publish →
 * 消费者线程，三跳共享同一个 ULID——
 *
 * <pre>
 * 第一跳  引擎 wrapPeriodicRunnable 为本次执行生成 ULID traceId（poll 线程 MDC）
 * 第二跳  任务体内 BusEvent.of(...) 把该 ULID 捕获进事件信封 causationId
 * 第三跳  订阅 lambda → onEvent（同线程同步扇出）→ 消费线程 MDC restore 同一 ULID
 * </pre>
 *
 * <p>断言三者相等：grep 这个 ULID 即串起一次 poll 的全部日志。latch 同步，不做 sleep 等待。
 */
public class CausalChainThreeHopTest {

    private static final String TOPIC = "test.causal.chain";

    private static final class PollPayload implements BusPayload {
    }

    /** 记录消费线程 MDC traceId 的探针消费者（第三跳）。 */
    private static final class ChainConsumer extends AbstractBusConsumer<BusPayload> {
        final CountDownLatch consumed = new CountDownLatch(1);
        volatile String consumerTraceId;

        ChainConsumer() {
            super("causal-chain", 8);
        }

        @Override
        protected void consume(BusPayload event) {
            consumerTraceId = TraceContext.getTraceId();
            consumed.countDown();
        }
    }

    private TaskManager taskManager;
    private BusRegistry registry;
    private ChainConsumer consumer;

    @Before
    public void setUp() {
        MDC.clear();
        taskManager = new TaskManager();
        registry = new BusRegistry();
        consumer = new ChainConsumer();
    }

    @After
    public void tearDown() {
        consumer.shutdown();
        taskManager.shutdownAll();
        MDC.clear();
    }

    @Test
    public void pollBusPublishAndConsumerShareOneCausationUlid() throws Exception {
        AtomicReference<String> pollTraceId = new AtomicReference<>();
        AtomicReference<BusEvent<?>> publishedEvent = new AtomicReference<>();

        // 订阅：同步扇出保证 lambda 与 onEvent 运行在发布线程（= 执行 poll 的引擎 worker 线程）
        registry.subscribe(TOPIC, event -> {
            publishedEvent.set(event);
            consumer.onEvent(event.getPayload());
        });

        // poll 任务（周期执行的第一轮即完整链路；周期语义贴近真实轮询）
        taskManager.getMdcScheduledExecutorService().scheduleWithFixedDelay(() -> {
            pollTraceId.set(TraceContext.getTraceId());
            registry.publish(BusEvent.of(TOPIC, new PollPayload(),
                    EventContext.root(EventContext.Source.DEVICE_POLL, null)));
        }, 0, 50, TimeUnit.MILLISECONDS);

        assertTrue("消费者应收到事件（latch 同步）", consumer.consumed.await(10, TimeUnit.SECONDS));

        assertThat("第一跳：poll 线程应有引擎生成的 ULID", pollTraceId.get(), notNullValue());
        assertThat("第一跳 traceId 是 26 字符 ULID", pollTraceId.get().length(), is(26));
        assertThat("第二跳：事件信封 causationId 须等于 poll 线程 traceId",
                publishedEvent.get().getCausationId(), is(pollTraceId.get()));
        assertThat("第三跳：消费线程 MDC traceId 须等于同一 ULID",
                consumer.consumerTraceId, is(pollTraceId.get()));
    }
}
