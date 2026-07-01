package com.ecat.core.Bus.consumer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * AbstractBatchBusConsumer 骨架单测——N 满 flush、T 超时 flush、shutdown drain、flush 失败不杀线程、
 * drop-oldest 反压、ctor 严格校验、计数。
 * 
 * @author coffee
 */
public class AbstractBatchBusConsumerTest {

    /** 测试用 batch consumer：把每次 flush 的批次收集到 flushed；flushLatch 等待 flush 次数。 */
    private static final class CollectingBatchConsumer extends AbstractBatchBusConsumer<Integer> {
        final List<List<Integer>> flushed = new CopyOnWriteArrayList<List<Integer>>();
        final CountDownLatch flushLatch; // 等待 flush 次数
        final AtomicInteger flushCount = new AtomicInteger();

        CollectingBatchConsumer(int capacity, int batchSize, long flushIntervalMs, int expectFlushes) {
            super("test-batch", capacity, batchSize, flushIntervalMs);
            this.flushLatch = new CountDownLatch(expectFlushes);
        }

        @Override
        protected void flush(List<Integer> batch) {
            flushed.add(new ArrayList<Integer>(batch));
            flushCount.incrementAndGet();
            flushLatch.countDown();
        }
    }

    /** N 满 flush：投 batchSize 条 → 触发一次 flush，批内含全部 N 条，顺序保留。 */
    @Test
    public void flushWhenBatchSizeReached() throws InterruptedException {
        int batchSize = 5;
        CollectingBatchConsumer c = new CollectingBatchConsumer(100, batchSize, 60000L, 1);
        for (int i = 0; i < batchSize; i++) {
            c.onEvent(i);
        }
        assertTrue("攒满 N 应触发 flush", c.flushLatch.await(2, TimeUnit.SECONDS));
        assertEquals(1, c.flushed.size());
        assertEquals(batchSize, c.flushed.get(0).size());
        for (int i = 0; i < batchSize; i++) {
            assertEquals("顺序应保留", i, c.flushed.get(0).get(i).intValue());
        }
        assertEquals(batchSize, c.getProcessedCount());
        assertEquals(1, c.getFlushedBatchesCount());
        c.shutdown();
    }

    /** T 超时 flush：投 <N 条后不再投，等 flushIntervalMs 超时 → flush 残留。 */
    @Test
    public void flushOnTimeoutWhenBufferNonEmpty() throws InterruptedException {
        long interval = 300L;
        // batchSize=100（大，不触发 N），靠超时
        CollectingBatchConsumer c = new CollectingBatchConsumer(100, 100, interval, 1);
        long t0 = System.nanoTime();
        c.onEvent(10);
        c.onEvent(20);
        assertTrue("超时应 flush 残留", c.flushLatch.await(2, TimeUnit.SECONDS));
        long dtMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue("flush 应在超时后发生（非立即），实际=" + dtMs, dtMs >= interval - 50);
        assertEquals(1, c.flushed.size());
        assertEquals(2, c.flushed.get(0).size());
        c.shutdown();
    }

    /** 超时但 buffer 空 → 不 flush（flushBuffer 早返回）。 */
    @Test
    public void noFlushOnTimeoutWhenBufferEmpty() throws InterruptedException {
        long interval = 200L;
        CollectingBatchConsumer c = new CollectingBatchConsumer(100, 100, interval, 1);
        Thread.sleep(interval * 3); // 不投任何事件，等几个 interval
        assertEquals("空 buffer 超时不应 flush", 0, c.flushCount.get());
        c.shutdown();
    }

    /** shutdown drain：投 <N 条（未达阈值、未等够超时），立即 shutdown → 残留被 drain flush。 */
    @Test
    public void shutdownDrainsResidualBuffer() throws InterruptedException {
        CollectingBatchConsumer c = new CollectingBatchConsumer(100, 100, 60000L, 1);
        c.onEvent(1);
        c.onEvent(2);
        c.onEvent(3);
        c.shutdown(); // 没等 60s 超时，直接 shutdown
        assertTrue("shutdown 应 drain 残留", c.flushLatch.await(2, TimeUnit.SECONDS));
        assertEquals(1, c.flushed.size());
        assertEquals(3, c.flushed.get(0).size());
    }

    /** flush 抛异常 → onFlushError 吞掉，消费线程存活继续处理后续事件。 */
    @Test
    public void flushErrorDoesNotKillConsumer() throws InterruptedException {
        final CountDownLatch firstFlush = new CountDownLatch(1);
        final AtomicInteger successAfterError = new AtomicInteger();
        AbstractBatchBusConsumer<Integer> c = new AbstractBatchBusConsumer<Integer>("test-err", 100, 2, 60000L) {
            @Override
            protected void flush(List<Integer> batch) {
                if (firstFlush.getCount() > 0) {
                    firstFlush.countDown();
                    throw new RuntimeException("故意失败");
                }
                successAfterError.addAndGet(batch.size());
            }
        };
        c.onEvent(0);
        c.onEvent(1); // 攒满 2 → flush 抛异常
        assertTrue("首次 flush 应发生", firstFlush.await(2, TimeUnit.SECONDS));
        c.onEvent(2);
        c.onEvent(3); // 再攒满 2 → 这次 flush 成功
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (successAfterError.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertEquals("异常后消费线程应存活，后续 flush 成功", 2, successAfterError.get());
        c.shutdown();
    }

    /** drop-oldest：worker 卡在 flush 时，队列满后继续投 → 丢最旧；onEvent 不阻塞。 */
    @Test
    public void dropOldestWhenQueueFull() throws InterruptedException {
        final CountDownLatch inFlush = new CountDownLatch(1);
        final CountDownLatch hold = new CountDownLatch(1);
        AbstractBatchBusConsumer<Integer> c = new AbstractBatchBusConsumer<Integer>("test-drop", 4, 2, 60000L) {
            @Override
            protected void flush(List<Integer> batch) {
                inFlush.countDown();
                try {
                    hold.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        c.onEvent(0);
        c.onEvent(1); // 攒满 batchSize=2 → worker 进入 flush 阻塞
        assertTrue("worker 应进入 flush", inFlush.await(2, TimeUnit.SECONDS));
        // worker 卡在 flush，后续 onEvent 只能入队；队列 cap=4 → 满 → drop-oldest
        long t0 = System.nanoTime();
        for (int i = 2; i < 60; i++) {
            c.onEvent(i);
        }
        long dtMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue("onEvent 即使 worker stalled 也应毫秒级返回，实际=" + dtMs, dtMs < 1000);
        assertTrue("队列满应丢最旧", c.getDroppedCount() > 0);
        hold.countDown();
        c.shutdown();
    }

    /** ctor 严格校验：batchSize<=0 抛 IllegalArgumentException。 */
    @Test(expected = IllegalArgumentException.class)
    public void rejectNonPositiveBatchSize() {
        new AbstractBatchBusConsumer<Integer>("bad", 10, 0, 1000L) {
            @Override
            protected void flush(List<Integer> batch) {
            }
        };
    }

    /** ctor 严格校验：flushIntervalMs<=0 抛 IllegalArgumentException。 */
    @Test(expected = IllegalArgumentException.class)
    public void rejectNonPositiveFlushInterval() {
        new AbstractBatchBusConsumer<Integer>("bad", 10, 10, 0L) {
            @Override
            protected void flush(List<Integer> batch) {
            }
        };
    }
}
