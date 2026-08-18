package com.ecat.core.Bus.consumer;

import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Mdc.TraceContext;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * <p><b>因果链续传（14 号架构 §5.4-3）</b>：总线是同步扇出——{@code onEvent} 在发布线程（poll 线程）
 * 内执行，此刻 MDC 中的 traceId（ULID）即事件因果锚点。onEvent 把它随载荷一起入队，消费线程取出时
 * restore 进自身 MDC，consume 内的日志与任何再发布事件都指向同一 ULID——poll→发布→消费一 grep 到底。
 * 发布线程无 traceId（如裸线程发布的系统事件）则不设置，消费线程 MDC 保持原状（如实缺失，不造 id）。
 *
 * <p><b>自观测（B2 system_health）</b>：每个实例构造时以弱引用登记进 {@link #LIVE_CONSUMERS}，
 * 供健康端点枚举各消费者队列深度/丢批/处理计数；消费逻辑本身零额外成本（计数器早已有，只加枚举）。
 *
 * @param <E> 事件/载荷类型
 *
 * @author coffee
 */
public abstract class BusConsumerBase<E> {

    /** 慢消费/慢 flush 阈值（毫秒）——超过则触发 {@link #onSlowConsume}，便于子类记日志/告警。 */
    static final long SLOW_CONSUME_MS = 1000L;

    /**
     * 全部存活 consumer 实例（弱引用——集成卸载后实例可被 GC，注册表不构成泄漏源）。
     * system_health 读端点时经 {@link #liveConsumers()} 快照枚举。
     */
    private static final CopyOnWriteArrayList<WeakReference<BusConsumerBase<?>>> LIVE_CONSUMERS =
            new CopyOnWriteArrayList<>();

    private final ArrayBlockingQueue<Object> queue;
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
        this.queue = new ArrayBlockingQueue<Object>(capacity);
        this.worker = Executors.newSingleThreadExecutor(new NamedDaemonFactory(name));
        this.inheritedMdc = TraceContext.capture();
        LIVE_CONSUMERS.add(new WeakReference<>(this));
        // 不在此 submit worker —— 留给子类在自身字段初始化后调 start()，规避 this 逃逸。
    }

    /**
     * 总线入口：非阻塞投递；队列满则丢最旧保新。final——DEV-1 确认所有 device.data.update 消费者都用
     * drop-oldest，锁死防止子类误改成阻塞型，破坏「绝不回压设备轮询线程」硬约束。
     *
     * <p>因果续传在此捕获：同步扇出保证本方法运行在发布线程，其 MDC traceId 与
     * {@code BusEvent.getCausationId()} 同源同值。
     */
    public final void onEvent(E event) {
        Entry<E> entry = new Entry<>(event, TraceContext.getTraceId());
        while (!queue.offer(entry)) {
            Object stale = queue.poll();
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
            return unwrap(queue.take());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** 最多等待 timeoutMs；超时或被中断返回 null（被中断时恢复中断标志）。 */
    protected final E pollNext(long timeoutMs) {
        try {
            return unwrap(queue.poll(timeoutMs, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 出队解包 + 因果续传：把发布线程捕获的 traceId restore 进消费线程 MDC。
     * causation 为 null（发布线程无 traceId）时清掉上一条残留的 traceId，保持 MDC 如实。
     */
    @SuppressWarnings("unchecked")
    private E unwrap(Object queued) {
        if (queued == null) {
            return null;
        }
        Entry<?> entry = (Entry<?>) queued;
        if (entry.causationId != null) {
            TraceContext.setTraceId(entry.causationId);
        } else {
            TraceContext.clearTraceId();
        }
        return (E) entry.event;
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
    @SuppressWarnings("unchecked")
    protected final List<E> drainQueue() {
        List<E> out = new ArrayList<E>();
        List<Object> raw = new ArrayList<Object>();
        queue.drainTo(raw);
        for (Object queued : raw) {
            // 队列只装 Entry<E>（onEvent 唯一入队方），强转安全
            out.add((E) ((Entry<?>) queued).event);
        }
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

    /** 队列总容量（size + remainingCapacity，消费中不影响）。system_health 队列深度百分比用。 */
    public int getQueueCapacity() { return queue.size() + queue.remainingCapacity(); }

    /** worker 是否已停（shutdown/shutdownNow 后为 true）。健康端点过滤已停消费者用。 */
    public boolean isShutdown() { return worker.isShutdown(); }

    /**
     * 当前存活（未 shutdown、弱引用未死）的 consumer 快照，按构造顺序。
     * 读路径顺带清理已 GC 的弱引用——注册表不随时间膨胀。
     */
    public static List<BusConsumerBase<?>> liveConsumers() {
        List<BusConsumerBase<?>> out = new ArrayList<>();
        for (WeakReference<BusConsumerBase<?>> ref : LIVE_CONSUMERS) {
            BusConsumerBase<?> consumer = ref.get();
            if (consumer == null) {
                LIVE_CONSUMERS.remove(ref);
            } else if (!consumer.isShutdown()) {
                out.add(consumer);
            }
        }
        return out;
    }

    /** 队列元素：载荷 + 发布线程捕获的因果 traceId（ULID）。 */
    private static final class Entry<E> {
        final E event;
        final String causationId;

        Entry(E event, String causationId) {
            this.event = event;
            this.causationId = causationId;
        }
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
