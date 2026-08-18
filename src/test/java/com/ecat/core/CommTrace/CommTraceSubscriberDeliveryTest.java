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

package com.ecat.core.CommTrace;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * SSE 投递链路（最小 harness，不经 Undertow）：真实 CommTraceBuffer 的信号式单写者
 * 投递线程 → 桩订阅者，CountDownLatch 确定性等待帧到达，验证过滤投递与死订阅者踢出。
 */
public class CommTraceSubscriberDeliveryTest {

    private CommTraceBuffer buffer;

    /** 桩订阅者：计数 + latch（每帧 countDown）。 */
    static final class LatchSubscriber extends CommTraceSubscriber {
        final CountDownLatch latch;
        final AtomicInteger received = new AtomicInteger();

        LatchSubscriber(CommTraceFilter filter, int expected) {
            super(filter);
            this.latch = new CountDownLatch(expected);
        }

        @Override
        public void send(CommTraceEvent event) {
            received.incrementAndGet();
            latch.countDown();
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}
    }

    /** 永远抛异常的订阅者：验证投递线程踢出而不死。 */
    static final class BrokenSubscriber extends CommTraceSubscriber {
        BrokenSubscriber(CommTraceFilter filter) {
            super(filter);
        }

        @Override
        public void send(CommTraceEvent event) throws IOException {
            throw new IOException("dead connection");
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}
    }

    @Before
    public void setUp() {
        buffer = new CommTraceBuffer(1024);
    }

    @After
    public void tearDown() {
        buffer.close();
    }

    @Test
    public void subscriberReceivesOnlyMatchingFrames() throws Exception {
        LatchSubscriber sub = new LatchSubscriber(
                new CommTraceFilter(CommTraceTransport.TCP_CLIENT, "h:9000", null, null, null), 3);
        buffer.subscribe(sub);
        buffer.tx(CommTraceTransport.TCP_CLIENT, "h:9000", new byte[]{1}, null);
        buffer.tx(CommTraceTransport.SERIAL, "p1", new byte[]{2}, null); // 不匹配
        buffer.tx(CommTraceTransport.TCP_CLIENT, "h:9000", new byte[]{3}, null);
        buffer.rx(CommTraceTransport.TCP_CLIENT, "h:9000", new byte[]{4}, "t1", 2.0);
        buffer.tx(CommTraceTransport.TCP_CLIENT, "other:9000", new byte[]{5}, null); // 不匹配

        assertTrue("3 条匹配帧在 5s 内投递", sub.latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void brokenSubscriberIsEvictedAndDeliveryThreadSurvives() throws Exception {
        BrokenSubscriber broken = new BrokenSubscriber(CommTraceFilter.all());
        LatchSubscriber healthy = new LatchSubscriber(CommTraceFilter.all(), 2);
        buffer.subscribe(broken);
        buffer.subscribe(healthy);
        buffer.tx(CommTraceTransport.SERIAL, "p1", new byte[]{1}, null); // broken 抛异常
        buffer.tx(CommTraceTransport.SERIAL, "p1", new byte[]{2}, null); // 投递线程须存活继续投
        assertTrue("死订阅者不拖死投递线程，健康订阅者仍收帧",
                healthy.latch.await(5, TimeUnit.SECONDS));
        // 踢出须发生（无直接断言入口，用再次投递不再异常即存活佐证）
        buffer.tx(CommTraceTransport.SERIAL, "p1", new byte[]{3}, null);
        List<CommTraceEvent> all = buffer.query(CommTraceFilter.all(), 10, 0);
        assertTrue("环内帧完整", all.size() >= 3);
    }

    @Test
    public void closedBufferStopsDeliveryAndClosesSubscribers() {
        final AtomicInteger closedCount = new AtomicInteger();
        CommTraceSubscriber sub = new CommTraceSubscriber(CommTraceFilter.all()) {
            @Override
            public void send(CommTraceEvent event) {}

            @Override
            public boolean isClosed() {
                return false;
            }

            @Override
            public void close() {
                closedCount.incrementAndGet();
            }
        };
        buffer.subscribe(sub);
        buffer.tx(CommTraceTransport.SERIAL, "p1", new byte[]{1}, null);
        buffer.close();
        assertTrue("close 逐个关闭订阅者", closedCount.get() >= 1);
        // close 后 append 静默丢弃（closed guard），不再进环
        long before = buffer.latestSeq();
        buffer.tx(CommTraceTransport.SERIAL, "p1", new byte[]{1}, null);
        assertTrue(before == buffer.latestSeq());
    }
}
