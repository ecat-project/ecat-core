package com.ecat.core.Bus.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 攒批消费者骨架——事件先入 buffer，攒满 N 条或定时 T 毫秒无新事件后，由独占线程一次性 {@link #flush(List)} 落库。
 *
 * <p>继承 {@link BusConsumerBase} 地基；循环形态固定为「poll(T) → buffer.add → 满 N / 超时 T → flush」。
 * 与 {@link AbstractBusConsumer}（per-event）并列，二者各持一种纯循环形态，集成按需继承其一。
 *
 * <p><b>buffer 单线程独占</b>：只有消费线程 add/clear；{@link #shutdown()} 先 awaitTermination 确保消费线程
 * 已停，再在调用线程 drain 残留——无竞态，故 buffer 无需同步。
 *
 * <p><b>构造不逃逸</b>：先初始化 buffer/batchSize/flushIntervalMs 字段，末尾 {@link #start()}，规避 this 逃逸。
 *
 * @param <E> 事件/载荷类型
 * 
 * @author coffee
 */
public abstract class AbstractBatchBusConsumer<E> extends BusConsumerBase<E> {

    private final List<E> buffer = new ArrayList<E>();
    private final int batchSize;
    private final long flushIntervalMs;
    private final AtomicLong flushedBatches = new AtomicLong();

    /**
     * @param name            consumer 名（线程名/日志）
     * @param capacity        队列容量（drop-oldest 目标）
     * @param batchSize       攒批阈值（buffer 达此条数立即 flush），必须 &gt; 0
     * @param flushIntervalMs 定时 flush 间隔（无新事件超时此值则 flush 残留），必须 &gt; 0
     */
    protected AbstractBatchBusConsumer(String name, int capacity, int batchSize, long flushIntervalMs) {
        super(name, capacity);
        if (batchSize <= 0 || flushIntervalMs <= 0) {
            throw new IllegalArgumentException(
                    "batchSize/flushIntervalMs 必须 > 0: name=" + name
                            + " batchSize=" + batchSize + " flushIntervalMs=" + flushIntervalMs);
        }
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        // 字段已就绪，启动 worker 安全（无 this 逃逸）
        start();
    }

    /** batch 循环形态（final：锁死，集成只覆盖 flush）。 */
    @Override
    protected final void runLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            E event = pollNext(flushIntervalMs); // null = 超时 或 被中断
            if (event != null) {
                buffer.add(event);
                incrementProcessed();
            } else if (Thread.currentThread().isInterrupted()) {
                return; // 被中断（非超时），退出；残留由 shutdown drain
            }
            // flush 触发：① 攒满 batchSize ② 定时超时且 buffer 非空（低流量不积压）
            if (buffer.size() >= batchSize || (event == null && !buffer.isEmpty())) {
                flushBuffer();
            }
        }
    }

    /** copy-then-clear：即使 flush 抛异常，buffer 已清空，不重试阻塞消费线程；失败经 onFlushError 可见。 */
    private void flushBuffer() {
        if (buffer.isEmpty()) {
            return;
        }
        List<E> batch = new ArrayList<E>(buffer);
        buffer.clear();
        long t0 = System.nanoTime();
        try {
            flush(batch);
            flushedBatches.incrementAndGet();
        } catch (RuntimeException ex) {
            onFlushError(batch, ex);
        }
        noteElapsed(t0);
    }

    /** 子类实现：把一批事件落库（重活在消费线程，不在总线线程）。 */
    protected abstract void flush(List<E> batch);

    /** flush 抛运行时异常的回调：默认记 error（带 batch size），子类可覆盖。吞异常保线程存活，但失败必须可见。 */
    protected void onFlushError(List<E> batch, RuntimeException error) {
        log.error("bus 批量 flush 异常（已吞掉保证消费线程存活；batch size=" + batch.size() + "）", error);
    }

    /** 停服：先中断消费线程并等其退出，再把「队列残留 + buffer 残留」一并 flush，保证不丢。
     *  worker 已停后由调用线程独占 queue/buffer，无竞态；顺序 buffer 在前（更早 poll 进来）+ 队列残留。 */
    @Override
    public void shutdown() {
        super.shutdown(); // worker.shutdownNow() 请求中断
        try {
            // 等消费线程真正退出，确保后续 drain 在调用线程独占 queue/buffer，无竞态
            if (!awaitWorker(5, TimeUnit.SECONDS)) {
                log.warn("bus batch consumer worker 未在 5s 内退出，残留可能未 drain: name={}", getName());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // worker 已停：队列里还没 poll 的 + buffer 里已 poll 未满批的，合并后统一 flush
        buffer.addAll(drainQueue());
        flushBuffer();
    }

    public long getFlushedBatchesCount() { return flushedBatches.get(); }
}
