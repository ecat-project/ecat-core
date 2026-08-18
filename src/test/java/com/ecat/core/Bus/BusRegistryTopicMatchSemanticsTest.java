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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.BusPayload;
import com.ecat.core.Bus.event.EventContext;

/**
 * BusRegistry topic 匹配与订阅表维护语义锁定测试（热路径快路径改造的等价性护栏）。
 *
 * <p>改造背景：dispatchToMatching 原先对每个事件重编译正则（实测 3,123ns/事件 vs equals 28ns，
 * ≈18KB/事件分配 churn）。改造后精确 topic（订阅 key 不含 {@code *}）走 Map equals 直配，
 * 通配订阅 key 在 subscribe 期预编译 Pattern。本类锁定两组语义：
 * <ol>
 *   <li><b>匹配语义</b>：精确 topic 仅字面相等命中（不再把 key 当正则、点号不再匹配任意字符）；
 *       通配 key（含 {@code *}）行为与改造前逐位一致。</li>
 *   <li><b>订阅表维护语义</b>：同 key 多订阅者的插入顺序、去重、异常隔离，以及
 *       「取消订阅一个订阅者不得影响同 key 其余订阅者」（原实现 unsubscribe 无条件移除整个
 *       key 映射，共享 topic 如 device.data.update 有 13 处生产订阅，一个集成下线会孤儿化
 *       其余全部订阅者——此处为该缺陷的 regression 锁）。</li>
 * </ol>
 */
public class BusRegistryTopicMatchSemanticsTest {

    /** 最小占位载荷——只验证总线投递契约，不承载领域语义（与 BusRegistrySyncPublishTest 同款）。 */
    private static final class TestPayload implements BusPayload {
        private final Object value;
        TestPayload(Object value) { this.value = value; }
        Object getValue() { return value; }
    }

    /** 按到达顺序记录订阅者标记的收集器，用于断言插入顺序与命中集合。 */
    private static final class RecordingSubscriber implements EventSubscriber {
        private final String tag;
        private final List<String> sink;
        RecordingSubscriber(String tag, List<String> sink) { this.tag = tag; this.sink = sink; }
        @Override
        public void handleEvent(BusEvent<?> event) { sink.add(tag); }
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

    private static BusEvent<TestPayload> event(String topic) {
        return BusEvent.of(topic, new TestPayload("v"), EventContext.root(EventContext.Source.SYSTEM, null));
    }

    // ==================== 匹配语义 ====================

    /** 精确 topic：订阅 key 与发布 topic 字面相等必须命中（生产 100% 流量形态）。 */
    @Test
    public void exactTopic_literalEquals_matches() {
        List<String> sink = new ArrayList<>();
        registry.subscribe("device.data.update", new RecordingSubscriber("a", sink));
        registry.publish(event("device.data.update"));
        assertEquals("精确 topic 字面相等应命中", 1, sink.size());
    }

    /**
     * 精确 topic 语义收紧锁定：不含 {@code *} 的订阅 key 现按字面字符串匹配。
     * 旧实现把 key 当正则（点号匹配任意字符），"deviceXdataYupdate" 会误命中 "device.data.update"；
     * 通配符是 {@code *} 而非点号，点号在本系统 topic 命名里是层级分隔符（BusTopic 全部 9 个名字
     * 均为 {@code a.b.c} 形态），故字面化是行为收敛而非语义丢失。
     */
    @Test
    public void exactTopic_nonDotCharInDotPosition_noLongerMatches() {
        List<String> sink = new ArrayList<>();
        registry.subscribe("device.data.update", new RecordingSubscriber("a", sink));
        registry.publish(event("deviceXdataYupdate"));
        assertTrue("不含 * 的 key 不得按正则点号通配命中", sink.isEmpty());
    }

    /** 通配语义保留：订阅 "device.*" 匹配 "device.status"（含预编译 Pattern 路径）。 */
    @Test
    public void wildcardTopic_matchesSuffixTopics() {
        List<String> sink = new ArrayList<>();
        registry.subscribe("device.*", new RecordingSubscriber("w", sink));
        registry.publish(event("device.status"));
        assertEquals("通配订阅应命中", 1, sink.size());
    }

    /** 通配语义保留：前缀不同的 topic 不得命中。 */
    @Test
    public void wildcardTopic_differentPrefix_noMatch() {
        List<String> sink = new ArrayList<>();
        registry.subscribe("device.*", new RecordingSubscriber("w", sink));
        registry.publish(event("sensor.status"));
        assertTrue("前缀不同不得命中", sink.isEmpty());
    }

    /** 同一发布同时命中精确订阅与通配订阅时两者都收到（exact 先于通配，顺序为改造后确定性约定）。 */
    @Test
    public void mixedExactAndWildcard_bothReceive() {
        List<String> sink = new ArrayList<>();
        registry.subscribe("device.status", new RecordingSubscriber("exact", sink));
        registry.subscribe("device.*", new RecordingSubscriber("wild", sink));
        registry.publish(event("device.status"));
        assertEquals(2, sink.size());
        assertTrue(sink.contains("exact"));
        assertTrue(sink.contains("wild"));
    }

    /** 无任何订阅时 publish 不抛异常（匹配零命中路径）。 */
    @Test
    public void publishWithNoSubscribers_doesNotThrow() {
        registry.publish(event("device.data.update"));
    }

    // ==================== 订阅表维护语义 ====================

    /** 同 key 多订阅者按插入顺序依次同步调用（CopyOnWriteArrayList 顺序，改造前后一致）。 */
    @Test
    public void sameTopic_multipleSubscribers_insertionOrderPreserved() {
        List<String> sink = new ArrayList<>();
        registry.subscribe("device.data.update", new RecordingSubscriber("first", sink));
        registry.subscribe("device.data.update", new RecordingSubscriber("second", sink));
        registry.subscribe("device.data.update", new RecordingSubscriber("third", sink));
        registry.publish(event("device.data.update"));
        assertEquals(3, sink.size());
        assertEquals("first", sink.get(0));
        assertEquals("second", sink.get(1));
        assertEquals("third", sink.get(2));
    }

    /** 同一订阅者实例重复 subscribe 同一 key 去重（只收到一次）。 */
    @Test
    public void sameSubscriberResubscribed_deduplicated() {
        List<String> sink = new ArrayList<>();
        EventSubscriber sub = new RecordingSubscriber("only", sink);
        registry.subscribe("device.data.update", sub);
        registry.subscribe("device.data.update", sub);
        registry.publish(event("device.data.update"));
        assertEquals("同一实例重复订阅应去重", 1, sink.size());
    }

    /**
     * Regression（缺陷锁）：取消订阅同 key 两个订阅者之一，另一个必须继续收到事件。
     * 生产上 device.data.update 有 13 处订阅（env-alarm/env-material/env-data-handle/
     * env-access-control/bus-recorder/logicdevice×2/ADM×3/ecat-core-api 等），任一集成
     * 运行时下线触发 unsubscribe，旧实现会移除整个 key 映射使其余订阅者全部静默孤儿化。
     */
    @Test
    public void unsubscribeOneOfTwo_otherStillReceives() {
        List<String> sink = new ArrayList<>();
        registry.subscribe("device.data.update", new RecordingSubscriber("survivor", sink));
        Subscription doomed = registry.subscribe("device.data.update", new RecordingSubscriber("doomed", sink));

        doomed.unsubscribe();
        registry.publish(event("device.data.update"));

        assertEquals("取消一个订阅者后其余订阅者应继续收到事件", 1, sink.size());
        assertEquals("survivor", sink.get(0));
    }

    /** 通配订阅同规则：取消其一，另一个通配订阅者继续收到。 */
    @Test
    public void wildcardUnsubscribeOneOfTwo_otherStillReceives() {
        List<String> sink = new ArrayList<>();
        registry.subscribe("device.*", new RecordingSubscriber("survivor", sink));
        Subscription doomed = registry.subscribe("device.*", new RecordingSubscriber("doomed", sink));

        doomed.unsubscribe();
        registry.publish(event("device.status"));

        assertEquals(1, sink.size());
        assertEquals("survivor", sink.get(0));
    }

    /** 取消唯一订阅者后不再投递，且 key 清理后重复订阅-发布行为正确。 */
    @Test
    public void unsubscribeOnlySubscriber_stopsDelivery() {
        List<String> sink = new ArrayList<>();
        Subscription sub = registry.subscribe("device.data.update", new RecordingSubscriber("a", sink));

        sub.unsubscribe();
        registry.publish(event("device.data.update"));
        assertTrue(sink.isEmpty());

        // 清理后再订阅应恢复正常投递（映射已删，computeIfAbsent 重建）
        registry.subscribe("device.data.update", new RecordingSubscriber("b", sink));
        registry.publish(event("device.data.update"));
        assertEquals(1, sink.size());
        assertEquals("b", sink.get(0));
    }

    /** 通配订阅取消唯一订阅者后不再投递。 */
    @Test
    public void wildcardUnsubscribeOnlySubscriber_stopsDelivery() {
        List<String> sink = new ArrayList<>();
        Subscription sub = registry.subscribe("device.*", new RecordingSubscriber("w", sink));

        sub.unsubscribe();
        registry.publish(event("device.status"));
        assertTrue(sink.isEmpty());
    }

    /** 前一个订阅者抛异常不得影响同 key 后续订阅者收到事件（异常隔离，改造前后一致）。 */
    @Test
    public void subscriberException_doesNotAffectOthers() {
        List<String> sink = new ArrayList<>();
        registry.subscribe("device.data.update", event -> { throw new IllegalStateException("boom"); });
        registry.subscribe("device.data.update", new RecordingSubscriber("after", sink));

        registry.publish(event("device.data.update"));

        assertEquals("异常订阅者之后的订阅者应正常收到事件", 1, sink.size());
        assertEquals("after", sink.get(0));
    }

    /** 高频并发 sanity：多线程混合 publish 同一精确 topic，无并发修改异常且计数不丢（同步扇出不变）。 */
    @Test
    public void concurrentPublishOnExactTopic_allDelivered() throws InterruptedException {
        AtomicInteger received = new AtomicInteger();
        registry.subscribe("device.data.update", event -> received.incrementAndGet());

        int threads = 4;
        int perThread = 1_000;
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    registry.publish(event("device.data.update"));
                }
            });
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        assertEquals("4×1000 次发布应全部投递", threads * perThread, received.get());
    }
}
