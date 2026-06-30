package com.ecat.core.Bus.consumer;

import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消费者骨架（opt-in）——给各集成一个统一的"独立有界队列 + 独占线程 + 反压 + 计量"模板。
 *
 * <p>设计纪律（避免反向依赖）：本类只提供骨架——队列、独占消费线程、丢最旧反压、慢消费计时、
 * 丢弃/处理计数。"如何投递、如何消费、何时落库"由子类的 consume 决定。BusRegistry 不依赖本类。
 *
 * <p>onEvent 是总线唯一入口：必须纳秒级返回（只入队），重活在消费线程的 consume 里；总线侧的
 * 慢 listener 观测会守住这个契约。
 *
 * <p>反压=丢最旧保新（时间序列语义正确）：队列满时丢最旧腾位，绝不阻塞生产者（设备轮询线程）。
 *
 * @param <E> 事件/载荷类型
 */
public abstract class AbstractBusConsumer<E> {

    private final ArrayBlockingQueue<E> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final ExecutorService worker;
    private final String name;
    private final Log log = LogFactory.getLogger(getClass());

    /** 慢消费阈值（毫秒）——超过则触发 onSlowConsume 回调，便于子类记日志/告警。 */
    private static final long SLOW_CONSUME_MS = 1000L;

    protected AbstractBusConsumer(String name, int capacity) {
        this.name = name;
        this.queue = new ArrayBlockingQueue<E>(capacity);
        this.worker = Executors.newSingleThreadExecutor(new NamedDaemonFactory(name));
        this.worker.submit(this::loop);
    }

    /**
     * 总线入口：非阻塞投递；队列满则丢最旧保新。子类可 override 改反压策略（如必须不丢则改阻塞），
     * 但任何实现都不得阻塞总线发布线程。
     */
    public void onEvent(E event) {
        while (!queue.offer(event)) {
            E stale = queue.poll();
            if (stale != null) {
                dropped.incrementAndGet();
            }
        }
    }

    /** 消费线程主循环：阻塞取事件、计时、交子类 consume；单线程串行保证事件处理不并发。 */
    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            E event;
            try {
                event = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long t0 = System.nanoTime();
            try {
                consume(event);
            } catch (RuntimeException e) {
                onConsumeError(event, e);
            }
            processed.incrementAndGet();
            long dtMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            if (dtMs > SLOW_CONSUME_MS) {
                onSlowConsume(dtMs);
            }
        }
    }

    /** 子类实现：处理一个事件（重活在此，跑在消费线程，不在总线线程）。 */
    protected abstract void consume(E event);

    /** 消费抛运行时异常时的回调：默认记 error 日志（不静默吞），子类可覆盖做更细处理。
     *  吞掉异常是为保证消费线程不被单条坏事件拖死，但异常必须可见——严格模式不静默兜底。 */
    protected void onConsumeError(E event, RuntimeException error) {
        log.error("bus 消费异常（已吞掉保证消费线程存活；可覆盖 onConsumeError 细化处理）: event=" + event, error);
    }

    /** 慢消费（&gt;1s）回调，默认空，子类可覆盖记日志/告警。 */
    protected void onSlowConsume(long elapsedMs) { }

    public String getName() { return name; }
    public long getDroppedCount() { return dropped.get(); }
    public long getProcessedCount() { return processed.get(); }
    public int getQueueSize() { return queue.size(); }

    public void shutdown() {
        worker.shutdownNow();
    }

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
