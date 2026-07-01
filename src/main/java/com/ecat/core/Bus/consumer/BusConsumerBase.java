package com.ecat.core.Bus.consumer;

import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Mdc.TraceContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消费者共享地基——持有「独立有界队列 + 独占消费线程 + drop-oldest 反压 + 计量 + 慢计时」这套
 * per-event 与 batch 两种消费模式共用的基础设施。
 *
 * <p><b>构造器故意不起线程</b>：worker 的启动（{@link #start()}）留给子类在<b>自身字段初始化后</b>
 * 调用。若在基类构造器里 submit worker，子类字段（如 batch 模式的 buffer）尚未初始化，worker 会读到
 * null——经典「构造器泄漏 this」。把启动时机下放到子类构造器末尾，规避该逃逸。
 *
 * <p><b>循环形态由子类定</b>：{@link #runLoop()} 是抽象钩子；{@link AbstractBusConsumer}（per-event，
 * take + 逐条 consume）与 {@link AbstractBatchBusConsumer}（batch，poll + 攒批 + flush）各 final 实现
 * 一种纯循环形态。集成只继承那两个中间类，不直接继承本类——因此 {@link #start()} 永远被正确调用。
 *
 * <p><b>反压=丢最旧保新</b>（时间序列语义正确）：{@link #onEvent(Object)} 队列满时丢最旧腾位，绝不阻塞
 * 总线发布线程（设备轮询线程）。
 *
 * @param <E> 事件/载荷类型
 * 
 * @author coffee
 */
public abstract class BusConsumerBase<E> {

    /** 慢消费/慢 flush 阈值（毫秒）——超过则触发 {@link #onSlowConsume}，便于子类记日志/告警。 */
    static final long SLOW_CONSUME_MS = 1000L;

    private final ArrayBlockingQueue<E> queue;
    private final ExecutorService worker;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final String name;
    /**
     * 构造线程的完整 MDC 快照。consumer 由集成在其 onStart 线程构造，而该线程经
     * {@code IntegrationBase} 构造器已 {@code MDC.put("integration.coordinate", ...)}，故此快照含
     * 归属集成的 coordinate。{@link #start()} 把它 restore 到 worker 线程，使 consumer 侧日志带
     * coordinate → log 模块按 coordinate 投送各集成专属 log SSE。
     */
    private final Map<String, String> inheritedMdc;
    /** 子类共享的 logger；按实际子类 getClass() 取，故每个具体 consumer 各自的 logger。 */
    protected final Log log = LogFactory.getLogger(getClass());

    protected BusConsumerBase(String name, int capacity) {
        this.name = name;
        this.queue = new ArrayBlockingQueue<E>(capacity);
        this.worker = Executors.newSingleThreadExecutor(new NamedDaemonFactory(name));
        this.inheritedMdc = TraceContext.capture();
        // 不在此 submit worker —— 留给子类在自身字段初始化后调 start()，规避 this 逃逸。
    }

    /**
     * 总线入口：非阻塞投递；队列满则丢最旧保新。final——DEV-1 确认所有 device.data.update 消费者都用
     * drop-oldest，锁死防止子类误改成阻塞型，破坏「绝不回压设备轮询线程」硬约束。
     */
    public final void onEvent(E event) {
        while (!queue.offer(event)) {
            E stale = queue.poll();
            if (stale != null) {
                dropped.incrementAndGet();
            }
        }
    }

    /** 循环形态——由两个中间类各自 final 实现（per-event / batch）。 */
    protected abstract void runLoop();

    /** 启动独占消费线程（submit runLoop，包一层 MDC 传播）。由中间类构造器末尾调用——确保子类字段已就绪，规避 this 逃逸。 */
    protected final void start() {
        // wrapRunnable 把构造线程的 MDC（含 integration.coordinate）restore 到 worker 线程，
        // 使 consumer 侧日志带 coordinate，log 模块据此按集成投送专属 log SSE。
        worker.submit(TraceContext.wrapRunnable(this::runLoop, inheritedMdc));
    }

    /** 阻塞取下一条事件；被中断时恢复中断标志并返回 null（循环据此退出）。 */
    protected final E awaitNext() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** 最多等待 timeoutMs；超时或被中断返回 null（被中断时恢复中断标志）。 */
    protected final E pollNext(long timeoutMs) {
        try {
            return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** processed 计数 +1（事件被消费线程接收/入 buffer 时调）。 */
    protected final void incrementProcessed() {
        processed.incrementAndGet();
    }

    /** 仅慢计时（不增 processed），返回耗时毫秒；&gt;{@link #SLOW_CONSUME_MS} 触发 {@link #onSlowConsume}。batch 量 flush 用。 */
    protected final long noteElapsed(long startNanos) {
        long dtMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        if (dtMs > SLOW_CONSUME_MS) {
            onSlowConsume(dtMs);
        }
        return dtMs;
    }

    /** processed +1 + 慢计时。per-event 量 consume 用（consume 可能慢，需计时）。 */
    protected final void noteProcessed(long startNanos) {
        incrementProcessed();
        noteElapsed(startNanos);
    }

    /** 等 worker 线程退出（配合 {@link #shutdown()} 的 shutdownNow 使用）；返回是否在超时前退出。 */
    protected final boolean awaitWorker(long timeout, TimeUnit unit) throws InterruptedException {
        return worker.awaitTermination(timeout, unit);
    }

    /** 把队列中残留事件排空到一个新 List（保持 FIFO 顺序）。shutdown drain 用——worker 已停时由调用线程独占调用。 */
    protected final List<E> drainQueue() {
        List<E> out = new ArrayList<E>();
        queue.drainTo(out);
        return out;
    }

    /** 慢消费/慢 flush 回调，默认空，子类可覆盖记日志/告警。 */
    protected void onSlowConsume(long elapsedMs) { }

    /** 默认立即中断消费线程；batch 子类覆盖以加 drain-flush。 */
    public void shutdown() {
        worker.shutdownNow();
    }

    public String getName() { return name; }
    public long getDroppedCount() { return dropped.get(); }
    public long getProcessedCount() { return processed.get(); }
    public int getQueueSize() { return queue.size(); }

    private static final class NamedDaemonFactory implements ThreadFactory {
        private final String name;
        NamedDaemonFactory(String name) { this.name = name; }
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "bus-consumer-" + name);
            t.setDaemon(true);
            return t;
        }
    }
}
