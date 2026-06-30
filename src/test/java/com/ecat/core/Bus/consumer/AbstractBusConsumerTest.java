package com.ecat.core.Bus.consumer;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * AbstractBusConsumer 骨架单测——丢最旧反压、计数、onEvent 非阻塞、消费线程串行处理。
 */
public class AbstractBusConsumerTest {

    /** 测试用 consumer：每事件计数；用 latch 等待处理完成。 */
    private static final class CountingConsumer extends AbstractBusConsumer<Integer> {
        final AtomicInteger consumed = new AtomicInteger();
        final CountDownLatch latch;
        CountingConsumer(int capacity, int expectProcessed) {
            super("test", capacity);
            this.latch = new CountDownLatch(expectProcessed);
        }
        @Override
        protected void consume(Integer event) {
            consumed.incrementAndGet();
            latch.countDown();
        }
    }

    @Test
    public void dropOldestWhenOverflow() throws InterruptedException {
        // 容量 4，投 10 条：最多处理 4，丢最旧 6
        CountingConsumer c = new CountingConsumer(4, 4);
        for (int i = 0; i < 10; i++) {
            c.onEvent(i);
        }
        assertTrue("4 条应在超时前处理完", c.latch.await(2, TimeUnit.SECONDS));
        // 等丢弃计数稳定
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (c.getProcessedCount() + c.getDroppedCount() < 10 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(4, c.getProcessedCount());
        assertEquals(6, c.getDroppedCount());
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
}
