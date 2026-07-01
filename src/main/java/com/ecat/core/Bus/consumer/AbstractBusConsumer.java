package com.ecat.core.Bus.consumer;

/**
 * per-event 消费者骨架——每条事件进队列后由独占线程串行 consume。
 *
 * <p>继承 {@link BusConsumerBase} 的队列/线程/反压/计量地基；循环形态固定为「阻塞取一条 → consume → 慢计时」。
 * 本类无额外字段，构造器末尾 {@link #start()} 安全（无 this 逃逸）。
 *
 * <p><b>向后兼容</b>：构造器签名 {@code (name, capacity)} 与抽象 {@link #consume(Object)} 不变；
 * 现有 extender（env-data-handle/env-material/env-alarm/env-access-control + 测试）零改动。
 * 需要攒批入库的集成改用 {@link AbstractBatchBusConsumer}。
 *
 * @param <E> 事件/载荷类型
 * 
 * @author coffee
 */
public abstract class AbstractBusConsumer<E> extends BusConsumerBase<E> {

    protected AbstractBusConsumer(String name, int capacity) {
        super(name, capacity);
        // 无额外字段，consume 是抽象钩子；此时所有字段就绪，启动 worker 安全（无 this 逃逸）。
        start();
    }

    /** per-event 循环形态（final：锁死，集成只覆盖 consume）。 */
    @Override
    protected final void runLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            E event = awaitNext();
            if (event == null) {
                return; // 被中断（shutdown）
            }
            long t0 = System.nanoTime();
            try {
                consume(event);
            } catch (RuntimeException e) {
                onConsumeError(event, e);
            }
            noteProcessed(t0);
        }
    }

    /** 子类实现：处理一条事件（重活在消费线程，不在总线线程）。 */
    protected abstract void consume(E event);

    /** consume 抛运行时异常的回调：默认记 error 日志（不静默吞），子类可覆盖做更细处理。
     *  吞掉异常是为保证消费线程不被单条坏事件拖死，但异常必须可见——严格模式不静默兜底。 */
    protected void onConsumeError(E event, RuntimeException error) {
        log.error("bus 消费异常（已吞掉保证消费线程存活；可覆盖 onConsumeError 细化处理）: event=" + event, error);
    }
}
