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

package com.ecat.core.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * LogBuffer 并发与解耦契约测试。
 *
 * <p>背景：SSE 日志广播存在三锁死锁（logback appender 锁 / CopyOnWrite 锁 / Undertow SSE 连接锁
 * 顺序反转）。死锁根因之一是「投递(send, 取 SSE 连接锁) 与 put 同步耦合、且在 CopyOnWriteArraySet.removeIf
 * 持锁期间做 I/O」。本组测试把修复必须满足的四条契约固化下来，防止回归：
 * <ol>
 *   <li>订阅者 send 阻塞时，put 必须迅速返回 —— I/O 与投递解耦（死锁+冻平台的根因消除）。</li>
 *   <li>慢消费下按容量丢最旧，ring 始终有界 —— 背压（ring 容量即上界）。</li>
 *   <li>并发 put + subscribe/unsubscribe 保持活限 —— 不死锁、不 ConcurrentModificationException、不饿死。</li>
 *   <li>快订阅者按入环顺序收到 —— 单写者保序。</li>
 * </ol>
 *
 * <p>设计约束（必须遵守）：LogEntry 单一引用不变量 —— ring buffer 是 LogEntry 的唯一长期引用，
 * 投递路径不得引入第二份长期引用（历史上有过广播队列因双引用泄漏被移除，见 LogBufferTest 守卫测试）。
 * 故投递改为「信号唤醒 + 读 ring 增量」，信号不携带 LogEntry。
 *
 * @author coffee
 */
public class LogBufferConcurrencyTest {

    private LogBuffer buffer;

    @Before
    public void setUp() {
        // 小容量便于测丢最旧
        buffer = new LogBuffer(10);
    }

    @After
    public void tearDown() {
        if (buffer != null) {
            buffer.close();
        }
    }

    /**
     * 契约 1：订阅者 send 阻塞时，put 必须迅速返回（I/O 与投递解耦）。
     *
     * <p>旧代码 put → pushToSubscribers → removeIf 内同步 send 会跟着阻塞 → 本用例超时失败（RED）。
     * 新代码 put 只写 ring + 发信号 → 即返（GREEN）。
     */
    @Test(timeout = 10000)
    public void put_returns_fast_when_subscriber_send_blocks() throws InterruptedException {
        CountDownLatch blockLatch = new CountDownLatch(1);
        BlockingSubscriber sub = new BlockingSubscriber(blockLatch);
        buffer.subscribe(sub);
        assertEquals("订阅应立即生效", 1, buffer.getSubscriberCount());

        long start = System.nanoTime();
        buffer.put(entry(1, "msg"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // 释放阻塞订阅者，避免 tearDown 时 broadcaster 卡在 send
        blockLatch.countDown();

        assertTrue(
                "put 应在 500ms 内返回（实际 " + elapsedMs + "ms）；慢/阻塞订阅者不得阻塞投递方",
                elapsedMs < 500);
    }

    /**
     * 契约 2：慢消费下按容量丢最旧。
     *
     * <p>订阅者全程阻塞，向其投递远超容量的日志：每次 put 必须快速返回（不被慢订阅者拖），
     * 且 ring 始终不超过容量（最旧被淘汰丢弃）。旧代码第一次 put 就会卡死在同步 send（RED）。
     */
    @Test(timeout = 15000)
    public void drop_oldest_when_subscriber_slow() throws InterruptedException {
        int capacity = 10;
        CountDownLatch blockLatch = new CountDownLatch(1);
        BlockingSubscriber sub = new BlockingSubscriber(blockLatch);
        buffer.subscribe(sub);

        long start = System.nanoTime();
        for (int i = 1; i <= 50; i++) {
            buffer.put(entry(i, "msg" + i));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(
                "50 次 put 应在 3s 内全部返回（实际 " + elapsedMs + "ms）；不得被慢订阅者逐次阻塞",
                elapsedMs < 3000);
        assertEquals(
                "ring 应严格限制在容量内（最旧被丢）", capacity, buffer.size());

        // 释放阻塞订阅者，便于 tearDown
        blockLatch.countDown();
    }

    /**
     * 契约 3：并发 put + subscribe/unsubscribe 保持活限（计数式断言，不靠 JUnit 超时）。
     *
     * <p>这是 prod 死锁竞争窗口的单元级回归：一个线程持续 put，另一线程反复 subscribe/unsubscribe
     * （模拟 SSE 连接建立/关闭竞态），并叠加一个长期阻塞订阅者放大窗口。活限判据：put 线程在窗口内
     * 应完成大量 put（说明没被慢订阅者卡死）。
     *
     * <p>旧代码下，put → pushToSubscribers → removeIf 内同步 send 会被阻塞订阅者卡住（且持 COW 锁，
     * 连带卡住 subscribe/unsubscribe），put 线程几乎无法推进 → 计数远低于阈值（RED）。
     * 新代码下 put 只写 ring + 发信号 → 不被卡 → 计数充足（GREEN）。
     *
     * <p>实现要点：用计数断言而非 JUnit @Test(timeout)，避免旧代码下阻塞的 put 线程残留拖死测试 JVM；
     * blockLatch 在 finally 中释放，确保残留 put 线程能退出；线程均为守护线程。
     */
    @Test(timeout = 30000)
    public void concurrent_put_and_subscribe_unsubscribe_stays_live() throws Exception {
        final int durationMs = 2000;
        final int minPutsForLiveness = 100;
        final CountDownLatch blockLatch = new CountDownLatch(1);
        final BlockingSubscriber blocked = new BlockingSubscriber(blockLatch);
        buffer.subscribe(blocked);

        final AtomicInteger putCount = new AtomicInteger(0);
        final AtomicInteger errors = new AtomicInteger();

        Thread putThread = new Thread(() -> {
            long end = System.currentTimeMillis() + durationMs;
            int i = 0;
            while (System.currentTimeMillis() < end) {
                try {
                    buffer.put(entry(i++, "live-" + i));
                    putCount.incrementAndGet();
                } catch (Throwable e) {
                    errors.incrementAndGet();
                }
            }
        }, "lb-put");
        putThread.setDaemon(true);

        Thread subThread = new Thread(() -> {
            long end = System.currentTimeMillis() + durationMs;
            while (System.currentTimeMillis() < end) {
                try {
                    FastSubscriber s = new FastSubscriber();
                    buffer.subscribe(s);
                    buffer.unsubscribe(s);
                } catch (Throwable e) {
                    errors.incrementAndGet();
                }
            }
        }, "lb-sub-unsub");
        subThread.setDaemon(true);

        putThread.start();
        subThread.start();

        try {
            putThread.join(durationMs + 2000);
            subThread.join(durationMs + 2000);
        } finally {
            // 无论如何释放阻塞订阅者：旧代码下 put 线程卡在 send，必须释放 latch 才能让它退出，
            // 否则残留守护线程虽不阻止 JVM 退出，但会泄漏到下一个用例
            blockLatch.countDown();
            putThread.join(3000);
            subThread.join(3000);
        }

        assertEquals("并发期间不应抛异常（含 ConcurrentModificationException）", 0, errors.get());
        assertTrue(
                "put 应保持活限：" + durationMs + "ms 内应完成 >= " + minPutsForLiveness
                        + " 次 put（实际 " + putCount.get() + "）；慢订阅者不得卡死投递方",
                putCount.get() >= minPutsForLiveness);
    }

    /**
     * 契约 4：快订阅者按入环顺序收到（单写者保序）。
     *
     * <p>此为保序回归保护（旧同步代码亦保序，故本用例非 RED 复现，而是防止异步化后乱序）。
     */
    @Test(timeout = 10000)
    public void fast_subscriber_receives_in_order() throws InterruptedException {
        FastSubscriber sub = new FastSubscriber();
        buffer.subscribe(sub);

        for (int i = 1; i <= 5; i++) {
            buffer.put(entry(i, "msg" + i));
        }
        assertTrue("应收到 5 条", sub.awaitReceived(5, 2000));

        List<String> msgs = sub.getReceivedMessagesSnapshot();
        assertEquals("应收到 5 条", 5, msgs.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("第 " + i + " 条应保序为 msg" + (i + 1), "msg" + (i + 1), msgs.get(i));
        }
    }

    // ========== helpers ==========

    /**
     * 构造测试日志条目。
     *
     * <p>时间戳必须用 {@code System.currentTimeMillis() + seq}，使条目时间戳 ≥ 订阅时间
     * （订阅时记录的是 {@code currentTimeMillis()}），否则会被 LogBuffer 的「订阅时间过滤」
     * （{@code entry.ts >= subscribeTime}）跳过、根本不调 send，导致用例空转。seq 同时作为排序序号。
     */
    private LogEntry entry(long seq, String msg) {
        return new LogEntry(System.currentTimeMillis() + seq, "trace", "core", "INFO",
                "TestLogger", "main", msg, null);
    }

    /**
     * 阻塞型订阅者：send 阻塞在 latch 上，模拟慢/挂起的 SSE 连接（真实场景里 Undertow 连接半死时的代理）。
     */
    private static class BlockingSubscriber extends LogSubscriber {
        private final CountDownLatch blockLatch;
        final AtomicInteger receivedCount = new AtomicInteger(0);

        BlockingSubscriber(CountDownLatch blockLatch) {
            super(new ByteArrayOutputStream());
            this.blockLatch = blockLatch;
        }

        @Override
        public void send(LogEntry entry) throws IOException {
            receivedCount.incrementAndGet();
            try {
                blockLatch.await();
            } catch (InterruptedException e) {
                // close 中断 broadcaster 时由此退出，属正常关闭路径
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 快订阅者：记录收到的消息（顺序敏感），用于保序断言。
     */
    private static class FastSubscriber extends LogSubscriber {
        private final List<String> receivedMessages = new ArrayList<>();

        FastSubscriber() {
            super(new ByteArrayOutputStream());
        }

        @Override
        public synchronized void send(LogEntry entry) throws IOException {
            receivedMessages.add(entry.getMessage());
        }

        synchronized List<String> getReceivedMessagesSnapshot() {
            return new ArrayList<>(receivedMessages);
        }

        boolean awaitReceived(int n, int timeoutMs) throws InterruptedException {
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                synchronized (this) {
                    if (receivedMessages.size() >= n) {
                        return true;
                    }
                }
                Thread.sleep(10);
            }
            return false;
        }
    }
}
