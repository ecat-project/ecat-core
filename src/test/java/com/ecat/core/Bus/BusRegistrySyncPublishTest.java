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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

import com.ecat.core.Bus.consumer.AbstractBusConsumer;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.BusPayload;
import com.ecat.core.Bus.event.EventContext;

/**
 * Tests for BusRegistry 同步扇出分发（重构后契约）。
 * 重构后 publish 是唯一发布方法，做同步扇出（调用线程内执行订阅者），不再持有共享线程池；
 * 异步性下放到消费者自身（AbstractBusConsumer 自带独占线程）。本类验证：
 * 同步扇出在调用线程执行、通配符匹配、订阅者从 BusEvent 信封取 type/payload、以及消费者自有线程承担异步处理。
 *
 * <p>载荷用本测试自带的 {@link TestPayload}（实现 {@link BusPayload}）——这些用例只验证总线信封投递
 * 契约（同步扇出/通配符/线程归属），不关心领域载荷语义，故用最小占位载荷而非真实 DeviceDataChangedEvent。
 */
public class BusRegistrySyncPublishTest {

    /**
     * 测试占位载荷——只验证总线信封投递契约（同步扇出/通配符/线程归属）的最小 BusPayload，
     * 不承载领域语义。携带一个 Object 值供订阅者取回校验透传正确性。
     */
    private static final class TestPayload implements BusPayload {
        private final Object value;
        TestPayload(Object value) { this.value = value; }
        Object getValue() { return value; }
    }

    private BusRegistry registry;

    @Before
    public void setUp() {
        registry = new BusRegistry();
    }

    @After
    public void tearDown() {
        registry.shutdown();
    }

    /**
     * publish 必须在调用 publish 的同一线程上同步执行订阅者（不在任何执行器线程上）。
     */
    @Test
    public void testPublishCallsSubscriberInSameThread() {
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();

        registry.subscribe("device.status", event -> callbackThread.set(Thread.currentThread()));

        registry.publish(BusEvent.of("device.status", new TestPayload("online"),
                EventContext.root(EventContext.Source.SYSTEM, null)));

        assertNotNull("Subscriber should have been called", callbackThread.get());
        assertSame("publish 应在调用线程同步扇出（重构后无共享线程池）",
                callerThread, callbackThread.get());
    }

    /**
     * 重构后的流控契约：publish() 同步扇出——订阅者在调用线程内执行，不再经共享线程池。
     * 这正是把"是否异步"下放到消费者的前提：发布线程同步扇出，消费者自行决定是否离线处理。
     */
    @Test
    public void testPublishIsSyncFanoutOnCallerThread() {
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();

        registry.subscribe("device.data", event -> callbackThread.set(Thread.currentThread()));

        registry.publish(BusEvent.of("device.data", new TestPayload("some-payload"),
                EventContext.root(EventContext.Source.DEVICE_POLL, null)));

        assertNotNull("Subscriber should have been called", callbackThread.get());
        assertSame("publish 应在调用线程同步扇出（重构后无共享线程池）",
                callerThread, callbackThread.get());
    }

    /**
     * 异步处理由消费者自有线程承担：订阅者把事件非阻塞转交 AbstractBusConsumer（自带独占线程），
     * 实际消费跑在 consumer 的 worker 线程（bus-consumer-*），而非发布线程。
     * 这是"流控下放到消费者"的核心——发布线程只入队纳秒级返回，绝不阻塞；慢消费者各自背压互不影响。
     */
    @Test
    public void testAsyncProcessingViaConsumerOwnedThread() throws InterruptedException {
        Thread callerThread = Thread.currentThread();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Thread> workerThread = new AtomicReference<>();

        AbstractBusConsumer<String> consumer = new AbstractBusConsumer<String>("sync-test", 4) {
            @Override
            protected void consume(String event) {
                workerThread.set(Thread.currentThread());
                latch.countDown();
            }
        };
        // 订阅者只做非阻塞入队（onEvent 纳秒级返回），重活交给消费者独占线程；
        // 信封迁移后订阅者收到 BusEvent，从 getPayload() 取 TestPayload 再读出其中的 String 值
        // （getPayload() 返回通配类型 capture#1 of ?，须先按 TestPayload 取值再交消费者）
        registry.subscribe("device.data",
                event -> consumer.onEvent((String) ((TestPayload) event.getPayload()).getValue()));

        registry.publish(BusEvent.of("device.data", new TestPayload("heavy-payload"),
                EventContext.root(EventContext.Source.DEVICE_POLL, null)));

        assertTrue("消费者应在 5s 内处理完", latch.await(5, TimeUnit.SECONDS));
        assertNotNull("消费者 worker 应已执行", workerThread.get());
        assertNotSame("实际消费应跑在消费者独占线程，而非发布线程",
                callerThread, workerThread.get());
        consumer.shutdown();
    }

    /**
     * publish 必须支持通配符 topic 匹配：订阅 "device.*" 应匹配 "device.status"。
     * 信封迁移后订阅者从 BusEvent 取 type/payload。
     */
    @Test
    public void testPublishMatchesWildcardTopics() {
        AtomicReference<String> receivedTopic = new AtomicReference<>();
        AtomicReference<Object> receivedData = new AtomicReference<>();

        registry.subscribe("device.*", event -> {
            receivedTopic.set(event.getType());
            receivedData.set(((TestPayload) event.getPayload()).getValue());
        });

        registry.publish(BusEvent.of("device.status", new TestPayload(42),
                EventContext.root(EventContext.Source.SYSTEM, null)));

        assertEquals("Wildcard should match 'device.status'",
                "device.status", receivedTopic.get());
        assertEquals("Event data should be passed through",
                42, receivedData.get());
    }
}
