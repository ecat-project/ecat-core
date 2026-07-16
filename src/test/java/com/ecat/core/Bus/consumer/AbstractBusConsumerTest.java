package com.ecat.core.Bus.consumer;

import com.ecat.core.Utils.Mdc.MdcCoordinateConverter;
import org.junit.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * AbstractBusConsumer 骨架单测——丢最旧反压、计数、onEvent 非阻塞、消费线程串行处理。
 */
public class AbstractBusConsumerTest {

    @Test
    public void dropOldestWhenOverflow() throws InterruptedException {
        // 稳定控制投递/消费速度的关键：用 latch 把 worker 冻结在 consume() 内，让「投递」与「消费」
        // 两个阶段彻底解耦——投递阶段 worker 绝不抽队列，因此队列是否溢出只取决于容量，与线程调度/机器快慢无关。
        final int capacity = 4;
        final int total = 10;                                       // 共投递 10 条
        final CountDownLatch workerParked = new CountDownLatch(1);  // worker 取到首条并进入 consume()
        final CountDownLatch release = new CountDownLatch(1);       // 放行 worker 开始消费
        final int expectedProcessed = capacity + 1;                 // 1 条 in-flight + capacity 条队列
        final CountDownLatch allProcessed = new CountDownLatch(expectedProcessed);
        final AtomicInteger processed = new AtomicInteger();

        AbstractBusConsumer<Integer> c = new AbstractBusConsumer<Integer>("overflow", capacity) {
            @Override
            protected void consume(Integer event) {
                workerParked.countDown();            // 已取到事件 → 通知主线程；随后阻塞，不再 take
                try {
                    release.await();                 // 冻结：worker 持有 1 条 in-flight，不再抽干队列
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                processed.incrementAndGet();
                allProcessed.countDown();
            }
        };

        // ① 第 1 条用于「唤醒并冻结」worker：确定性建立 in-flight=1，此后 worker 不再消费
        c.onEvent(0);
        assertTrue("worker 应及时取到首条并 park", workerParked.await(2, TimeUnit.SECONDS));

        // ② worker 冻结期间批量投递剩余 9 条：队列必然满到 capacity，必然丢最旧
        for (int i = 1; i < total; i++) {
            c.onEvent(i);
        }
        // 核心（确定性）：9 条进容量 4 的队列 → 丢最旧 5，队列留最新 4
        assertEquals(5, c.getDroppedCount());
        assertEquals(capacity, c.getQueueSize());

        // ③ 放行 worker：处理 in-flight(1) + 队列(4) = 5 条
        release.countDown();
        assertTrue("剩余事件应被处理完", allProcessed.await(2, TimeUnit.SECONDS));
        assertEquals(expectedProcessed, processed.get());

        c.shutdown();
    }

    @Test
    public void onEventDoesNotBlockWhenConsumerStalled() throws InterruptedException {
        // consumer 故意慢（latch 不倒），onEvent 仍应立即返回（只入队+丢旧，不等消费）
        final CountDownLatch hold = new CountDownLatch(1);
        AbstractBusConsumer<Integer> stalled = new AbstractBusConsumer<Integer>("stalled", 2) {
            @Override
            protected void consume(Integer event) {
                try { hold.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        };
        long t0 = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            stalled.onEvent(i); // 消费 stalled，但投递不阻塞
        }
        long dtMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue("onEvent 50 次应毫秒级返回，实际=" + dtMs + "ms", dtMs < 1000);
        hold.countDown();
        stalled.shutdown();
    }

    /** worker 线程应继承构造线程的 integration.coordinate MDC，使 consumer 日志能按集成投送专属 log SSE。 */
    @Test
    public void workerInheritsConstructingThreadCoordinateMdc() throws InterruptedException {
        final String coord = "com.ecat:integration-test-mdc";
        MDC.put(MdcCoordinateConverter.COORDINATE_KEY, coord);
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<String> seen = new AtomicReference<>();
            AbstractBusConsumer<Integer> c = new AbstractBusConsumer<Integer>("mdc-test", 4) {
                @Override
                protected void consume(Integer event) {
                    seen.set(MDC.get(MdcCoordinateConverter.COORDINATE_KEY));
                    latch.countDown();
                }
            };
            c.onEvent(1);
            assertTrue("consume 应在超时前执行", latch.await(2, TimeUnit.SECONDS));
            assertEquals("worker 线程应继承构造线程的 integration.coordinate MDC", coord, seen.get());
            c.shutdown();
        } finally {
            MDC.remove(MdcCoordinateConverter.COORDINATE_KEY);
        }
    }
}
