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

package com.ecat.core.Task.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.locks.LockSupport;

import com.ecat.core.Task.NamedThreadFactory;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Mdc.MdcContext;
import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * 调度 v2 门面：对 141 个 {@code getScheduledExecutor()} 调用点保持
 * {@link ScheduledExecutorService} 契约不变，内部换成「表轮计时 + 共享 worker 池 + 每设备
 * 逻辑车道 + 执法层（硬超时/看门狗/熔断）」三层引擎（取代旧全局 2 线程
 * ScheduledThreadPoolExecutor——两个阻塞任务即全平台轮询停摆的结构缺陷）。
 *
 * <p>契约保持点（对齐 STPE）：
 * <ul>
 *   <li>fixedRate 按名义起始时刻重排（滞后则立即补跑）、fixedDelay 按完成时刻重排</li>
 *   <li>周期任务抛未捕获异常即永久停止调度（仅把旧引擎的静默死亡补一行 WARN 点名）</li>
 *   <li>shutdown 后新提交抛 {@link RejectedExecutionException}；shutdownNow 中断在跑任务并
 *       返回未执行任务</li>
 *   <li>MDC 语义与 MdcScheduledExecutorService 一致：提交时捕获上下文；周期任务每次执行换新
 *       traceId（复用 TraceContext 同一实现）</li>
 * </ul>
 *
 * <p>车道键：提交时 MDC 的 integration.coordinate（集成坐标）优先；无坐标时回退提交方任务类
 * 名（lambda 后缀已剥，ConcurrentHashMap 按类缓存——零栈遍历）；com.ecat.core 自身任务落
 * 单一 "core" 车道。
 *
 * <p>线程预算：1 timer + N worker（默认 6，有界）+ 1 watchdog，全部 daemon、全部具名。
 *
 * @author coffee
 */
public final class SchedulerEngine extends AbstractExecutorService implements ScheduledExecutorService {

    private static final Log log = LogFactory.getLogger(SchedulerEngine.class);

    /** 引擎生命周期：RUNNING → SHUTDOWN（优雅）→ TERMINATED；RUNNING → STOP（shutdownNow）→ TERMINATED。 */
    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int STOP = 2;

    private final SchedulerConfig config;
    private final SchedulerClock clock;
    private final SchedulerMetrics metrics = new SchedulerMetrics();
    private final TimingWheel wheel;
    private final LaneDispatcher dispatcher;
    private final TaskWatchdog watchdog;
    private final CommBreakerRegistry commBreakers;
    private final Thread timerThread;
    private final Map<Class<?>, String> classLabelCache = new ConcurrentHashMap<>();
    /** executorFor 显式车道视图缓存（每 key 一个薄路由对象，不建池不建线程）。 */
    private final Map<String, LaneKeyExecutor> laneViews = new ConcurrentHashMap<>();
    private volatile int state = RUNNING;
    private volatile boolean terminated = false;

    public SchedulerEngine() {
        this(SchedulerConfig.fromSystemProperties(), SchedulerClock.SYSTEM, true);
    }

    /**
     * @param startBackgroundThreads false = 虚拟时钟测试模式：不启动 timer/worker/watchdog 线程，
     *                               测试手动驱动 wheel.expireUpTo / dispatcher / watchdog.scanOnce
     */
    SchedulerEngine(SchedulerConfig config, SchedulerClock clock, boolean startBackgroundThreads) {
        this.config = config;
        this.clock = clock;
        this.wheel = new TimingWheel(config.getWheelSlots(),
                TimeUnit.MILLISECONDS.toNanos(config.getTickMillis()), clock, this::fireTask);
        this.watchdog = new TaskWatchdog(config, clock, metrics, startBackgroundThreads);
        this.dispatcher = new LaneDispatcher(this, config, clock, watchdog, metrics, startBackgroundThreads);
        this.commBreakers = new CommBreakerRegistry(config, clock);
        if (startBackgroundThreads) {
            this.timerThread = new NamedThreadFactory("ecat-sched-timer", true).newThread(this::timerLoop);
            this.timerThread.start();
        } else {
            this.timerThread = null;
        }
    }

    // =====================================================================
    // ScheduledExecutorService 契约（141 调用点的门面）
    // =====================================================================

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        Map<String, String> ctx = TraceContext.capture();
        String label = classLabel(command);
        EngineTask<Void> task = new EngineTask<>(
                new EngineTask.Instrumented<>(asCallable(TraceContext.wrapRunnable(command, ctx))),
                0L, 0L, laneKey(ctx, label), label, clock);
        return submit(task, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        Map<String, String> ctx = TraceContext.capture();
        String label = classLabel(callable);
        EngineTask<V> task = new EngineTask<>(
                new EngineTask.Instrumented<>(TraceContext.wrapCallable(callable, ctx)),
                0L, 0L, laneKey(ctx, label), label, clock);
        return submit(task, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (period <= 0L) {
            throw new IllegalArgumentException("Non-positive period: " + period);
        }
        Map<String, String> ctx = TraceContext.capture();
        String label = classLabel(command);
        EngineTask<Void> task = new EngineTask<>(
                new EngineTask.Instrumented<>(asCallable(TraceContext.wrapPeriodicRunnable(command, ctx))),
                0L, unit.toNanos(period), laneKey(ctx, label), label, clock);
        return submit(task, initialDelay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (delay <= 0L) {
            throw new IllegalArgumentException("Non-positive delay: " + delay);
        }
        Map<String, String> ctx = TraceContext.capture();
        String label = classLabel(command);
        // 负数编码 fixedDelay（STPE 同款约定），worker 完成时刻起算
        EngineTask<Void> task = new EngineTask<>(
                new EngineTask.Instrumented<>(asCallable(TraceContext.wrapPeriodicRunnable(command, ctx))),
                0L, -unit.toNanos(delay), laneKey(ctx, label), label, clock);
        return submit(task, initialDelay, unit);
    }

    private <V> ScheduledFuture<V> submit(EngineTask<V> task, long delay, TimeUnit unit) {
        rejectIfShutdown();
        metrics.getSubmitted().increment();
        long delayNanos = delay <= 0L ? 0L : unit.toNanos(delay);
        task.deadlineNanos = addClamped(clock.nanoTime(), delayNanos);
        if (delayNanos == 0L) {
            dispatchNow(task);
        } else {
            wheel.add(task);
        }
        return task;
    }

    // =====================================================================
    // ExecutorService 契约（execute/submit/CompletableFuture 通道）
    // =====================================================================

    @Override
    public void execute(Runnable command) {
        rejectIfShutdown();
        metrics.getSubmitted().increment();
        if (command instanceof EngineTask) {
            // AbstractExecutorService.submit/invokeAll 的内部通路：任务已构造，直接派发
            dispatchNow((EngineTask<?>) command);
            return;
        }
        Map<String, String> ctx = TraceContext.capture();
        String label = classLabel(command);
        EngineTask<Void> task = new EngineTask<>(
                new EngineTask.Instrumented<>(asCallable(TraceContext.wrapRunnable(command, ctx))),
                0L, 0L, laneKey(ctx, label), label, clock);
        dispatchNow(task);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        // submit/invokeAll 默认机制经此构造任务再走 execute；车道键按提交方类解析
        Map<String, String> ctx = TraceContext.capture();
        String label = classLabel(runnable);
        return new EngineTask<>(
                new EngineTask.Instrumented<>(() -> {
                    TraceContext.wrapRunnable(runnable, ctx).run();
                    return value;
                }),
                0L, 0L, laneKey(ctx, label), label, clock);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        Map<String, String> ctx = TraceContext.capture();
        String label = classLabel(callable);
        return new EngineTask<>(
                new EngineTask.Instrumented<>(TraceContext.wrapCallable(callable, ctx)),
                0L, 0L, laneKey(ctx, label), label, clock);
    }

    // =====================================================================
    // 显式车道视图（IO 收敛 P0：按「资源坐标」分道的调用方通道，如 modbus source 事务）
    // =====================================================================

    /**
     * 返回绑定指定车道键的 {@link ExecutorService} 视图：execute/submit/invokeAll 的任务全部
     * 路由到该车道（同 key 严格串行、异 key 并行），车道/熔断/看门狗/硬超时执法与 MDC 推导
     * 任务完全同权。供分道粒度无法用提交方 MDC 集成坐标表达的调用方使用——典型是 IO 事务须按
     * 「资源坐标」分道：多设备/多集成共享一条连接时，同连接的事务必须串行（防帧碰撞），异连接并行。
     *
     * <p>laneKey 命名约定（调用方统一「资源域前缀 + 连接标识」）：{@code modbus-source:{conn}}，
     * 如 {@code modbus-source:127.0.0.1:502}。显式键与 MDC 推导键共用同一键空间（同名字符串即
     * 同车道），资源域前缀用于避免与集成坐标撞名。
     *
     * <p>视图轻量：每 key 缓存一个薄路由对象（{@code executorFor(k)} 幂等返回同实例），不建池
     * 不建线程；生命周期跟随引擎——引擎停机后视图提交抛 {@link RejectedExecutionException}，
     * 视图自身的 shutdown/shutdownNow 显式拒绝（共享引擎窗口无独立停机语义，资源回收由调用方
     * 自持标志位表达，见 {@link LaneKeyExecutor}）。
     *
     * @param laneKey 显式车道键；null/blank 直接抛 {@link IllegalArgumentException}——
     *                严格模式，不静默落默认道（静默合道 = 同源事务并发，正是本 API 要消灭的事故）
     */
    public ExecutorService executorFor(String laneKey) {
        if (laneKey == null || laneKey.trim().isEmpty()) {
            throw new IllegalArgumentException("executorFor 显式车道键必须非空: " + laneKey);
        }
        return laneViews.computeIfAbsent(laneKey, k -> new LaneKeyExecutor(this, k));
    }

    // 以下三个构造器供 LaneKeyExecutor 使用：与门面 MDC 推导通道同构（提交时 MDC 捕获传播 +
    // 类名标签 + Instrumented 失败捕获），唯一差异是车道键用显式键、不经 laneKey() 推导

    EngineTask<Void> laneTaskFor(Runnable command, String laneKey) {
        Map<String, String> ctx = TraceContext.capture();
        String label = classLabel(command);
        return new EngineTask<>(
                new EngineTask.Instrumented<>(asCallable(TraceContext.wrapRunnable(command, ctx))),
                0L, 0L, laneKey, label, clock);
    }

    <T> EngineTask<T> laneTaskFor(Callable<T> callable, String laneKey) {
        Map<String, String> ctx = TraceContext.capture();
        String label = classLabel(callable);
        return new EngineTask<>(
                new EngineTask.Instrumented<>(TraceContext.wrapCallable(callable, ctx)),
                0L, 0L, laneKey, label, clock);
    }

    <T> EngineTask<T> laneTaskFor(Runnable runnable, T value, String laneKey) {
        Map<String, String> ctx = TraceContext.capture();
        String label = classLabel(runnable);
        return new EngineTask<>(
                new EngineTask.Instrumented<>(() -> {
                    TraceContext.wrapRunnable(runnable, ctx).run();
                    return value;
                }),
                0L, 0L, laneKey, label, clock);
    }

    /** 视图提交通道：停机拒绝 + 提交计数 + 零延迟直通派发（与 execute() 主通道同权）。 */
    void dispatchExplicit(EngineTask<?> task) {
        rejectIfShutdown();
        metrics.getSubmitted().increment();
        dispatchNow(task);
    }

    // =====================================================================
    // 表轮到期 → 车道（timer 线程 / 虚拟时钟测试线程调用）
    // =====================================================================

    /** 表轮到期回调：熔断准入判定 → 入车道队列；跳过的周期任务按周期语义重挂轮。 */
    void fireTask(EngineTask<?> task) {
        if (state != RUNNING) {
            // 优雅停机对齐 STPE 默认策略（executeExistingDelayedTasksAfterShutdown=true）：
            // 停机后周期任务取消重排；一次性任务已提交并在表轮内等待的，到期仍须入车道跑完、
            // future 正常完成——静默丢弃会让等待方永远挂起（bug-record-20260817-021700）。
            if (task.isPeriodic()) {
                if (!task.isCancelled()) {
                    task.cancel(false);
                }
                return;
            }
            // 非周期任务停机期照常入队（下方流程不变）
        }
        if (task.isCancelled()) {
            return;
        }
        Lane lane = dispatcher.lane(task.laneKey);
        TaskCircuitBreaker.Decision decision = lane.breaker.admit(task.isPeriodic());
        if (decision == TaskCircuitBreaker.Decision.SKIP_PERIODIC) {
            metrics.getBreakerSkipped().increment();
            rearmPeriodic(task, task.deadlineNanos);
            metrics.getBreakerRearmed().increment();
            return;
        }
        if (decision == TaskCircuitBreaker.Decision.ADMIT_AS_PROBE) {
            task.admittedAsProbe = true;
        }
        if (!dispatcher.enqueue(task)) {
            if (task.admittedAsProbe) {
                // 探测名额被拒绝消耗：不留 PROBING 悬挂（无 worker outcome 会卡死探测状态）
                lane.breaker.onOutcome(true, "车道排队达上限，探测任务被拒绝");
            }
            rejectComplete(task);
        }
    }

    /**
     * worker 完成周期执行后重排下一轮。
     * fixedRate：名义网格 +period（滞后则到期即刻补跑，对齐 STPE 的 catch-up）；
     * fixedDelay：完成时刻 +delay。
     */
    void reschedulePeriodic(EngineTask<?> task) {
        long now = clock.nanoTime();
        long next = task.periodNanos > 0L
                ? addClamped(task.deadlineNanos, task.periodNanos)
                : addClamped(now, -task.periodNanos);
        task.deadlineNanos = next;
        wheel.add(task);
    }

    /** 熔断跳过路径的重排：fixedDelay 以到期时刻近似完成时刻（跳过路径无执行完成点）。 */
    private void rearmPeriodic(EngineTask<?> task, long nominalNanos) {
        long next = task.periodNanos > 0L
                ? addClamped(task.deadlineNanos, task.periodNanos)
                : addClamped(nominalNanos, -task.periodNanos);
        task.deadlineNanos = next;
        wheel.add(task);
    }

    /** 零延迟任务的直通派发（execute/submit 不得经表轮多等一个 tick）。 */
    private void dispatchNow(EngineTask<?> task) {
        if (!dispatcher.enqueue(task)) {
            rejectComplete(task);
        }
    }

    private void rejectComplete(EngineTask<?> task) {
        metrics.getRejected().increment();
        log.warn("SCHED_REJECT lane={} task={} 全引擎排队总量达上限 {}，任务被丢弃（可观测拒绝）",
                task.laneKey, task.taskLabel, config.getMaxQueuedTasks());
        if (task.isPeriodic()) {
            task.cancel(false);
        } else {
            task.failNow(new RejectedExecutionException("调度器排队达上限: lane=" + task.laneKey));
        }
    }

    // =====================================================================
    // timer 线程：只做「到点把任务放进车道队列」，绝不执行任务体
    // =====================================================================

    private void timerLoop() {
        while (true) {
            long nextBoundary = wheel.nextBoundaryNanos(clock.nanoTime());
            long sleep = nextBoundary - clock.nanoTime();
            if (sleep > 0L) {
                LockSupport.parkNanos(sleep);
            }
            if (Thread.interrupted()) {
                // shutdownNow：槽链表所有权交还调用方（drainAll 安全前提）
                return;
            }
            if (state != RUNNING) {
                wheel.cancelPeriodicTasks();
                if (wheel.liveTaskCount() == 0) {
                    // 表轮已清空：此后不再有任何到期任务入队，放行 worker 的优雅终止
                    dispatcher.retireTimer();
                    return;
                }
            }
            try {
                wheel.expireUpTo(clock.nanoTime());
            } catch (Throwable t) {
                // timer 线程是全局命脉：单次到期处理的意外（防御性兜底，正常路径不应触达）
                // 记 ERROR 后继续，不允许静默死亡拖停全部调度
                log.error("SCHED_TIMER_TICK_FAILED 表轮到期处理异常（已恢复继续）", t);
            }
        }
    }

    // =====================================================================
    // 生命周期
    // =====================================================================

    @Override
    public void shutdown() {
        if (state == RUNNING) {
            state = SHUTDOWN;
        }
        dispatcher.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        state = STOP;
        dispatcher.shutdownNow();
        if (timerThread != null) {
            timerThread.interrupt();
            try {
                timerThread.join(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (watchdog != null) {
            watchdog.shutdown();
        }
        List<Runnable> pending = new ArrayList<>(wheel.drainAll());
        pending.addAll(dispatcher.drainQueues());
        for (Runnable r : pending) {
            ((EngineTask<?>) r).cancel(false);
        }
        terminated = true;
        return pending;
    }

    @Override
    public boolean isShutdown() {
        return state != RUNNING;
    }

    @Override
    public boolean isTerminated() {
        return terminated || (state != RUNNING && timerThreadDead() && dispatcher.workersDead());
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!isTerminated()) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            LockSupport.parkNanos(1_000_000L);
        }
        return true;
    }

    private boolean timerThreadDead() {
        Thread t = timerThread;
        return t == null || !t.isAlive();
    }

    // =====================================================================
    // 车道键解析与工具
    // =====================================================================

    /** MDC 坐标优先；缺失回退任务定义类名（lambda 剥壳）；core 自身任务归并单一 "core" 车道。 */
    private String laneKey(Map<String, String> capturedContext, String label) {
        if (capturedContext != null) {
            String coordinate = capturedContext.get(MdcContext.INTEGRATION_COORDINATE_KEY);
            if (coordinate != null && !coordinate.isEmpty()) {
                return coordinate;
            }
        }
        return label.startsWith("com.ecat.core.") ? "core" : label;
    }

    /** 任务定义类名（剥 lambda 合成后缀），按类缓存——零栈遍历的调用类解析。 */
    private String classLabel(Object command) {
        return classLabelCache.computeIfAbsent(command.getClass(), SchedulerEngine::stripLambdaSuffix);
    }

    static String stripLambdaSuffix(Class<?> cls) {
        String name = cls.getName();
        int lambda = name.indexOf("$$Lambda");
        return lambda > 0 ? name.substring(0, lambda) : name;
    }

    private static Callable<Void> asCallable(Runnable runnable) {
        return () -> {
            runnable.run();
            return null;
        };
    }

    private static long addClamped(long a, long b) {
        long sum = a + b;
        // 溢出（Long.MAX_VALUE 延迟等极端参数）钳到极大值而非负数立即触发
        return ((a ^ sum) & (b ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }

    private void rejectIfShutdown() {
        if (state != RUNNING) {
            throw new RejectedExecutionException("调度器已停机: state=" + state);
        }
    }

    // =====================================================================
    // B2 自观测接口：metrics/熔断表/队列/worker 忙闲均为只读快照，
    // system_health（com.ecat.core.Observability）从这里采集
    // =====================================================================

    public SchedulerMetrics getMetrics() {
        return metrics;
    }

    /** 各车道熔断状态快照（B2 system_health 暴露用）。 */
    public Map<String, TaskCircuitBreaker> getBreakers() {
        return dispatcher.breakers();
    }

    // =====================================================================
    // D2 设备通讯失败熔断（与车道解耦的独立命名空间，键 = coordinate:deviceId）
    // =====================================================================

    /** 设备通讯失败上报（语义与键空间契约见 {@link CommBreakerRegistry}）。 */
    public void reportCommFailure(String key, String summary) {
        commBreakers.reportFailure(key, summary);
    }

    /** 设备通讯成功上报：清零连败。 */
    public void reportCommSuccess(String key) {
        commBreakers.reportSuccess(key);
    }

    /** 轮询守卫：true = 本轮轮询应跳过；false 时若处于冷却期满则本轮作为探测放行。 */
    public boolean isCommOpen(String key) {
        return commBreakers.isCommOpen(key);
    }

    /** 各设备通讯熔断快照（B2 system_health 的 commBreakers 表）。 */
    public Map<String, TaskCircuitBreaker> getCommBreakers() {
        return commBreakers.snapshot();
    }

    /** 车道排队总量（全引擎在排任务数，含即将执行与等待车道串行的）。 */
    public int queuedTotal() {
        return dispatcher.queuedTotal();
    }

    /** 表轮上挂着的任务数（已提交未到期 + 到期待触发；与车道排队共同构成调度积压全貌）。 */
    public int wheelPendingCount() {
        return wheel.liveTaskCount();
    }

    /** 车道数（活跃提交方分组数，通常≈集成为单位的逻辑隔离数）。 */
    public int laneCount() {
        return dispatcher.laneCount();
    }

    /** worker 池线程数。 */
    public int workerCount() {
        return dispatcher.workerCount();
    }

    /** 正在执行任务的 worker 数（忙）。 */
    public int activeWorkerCount() {
        return dispatcher.activeRunnerCount();
    }

    /** 各 worker 忙闲快照（线程名 + 当前执行车道，null = 空闲）。 */
    public List<WorkerStatus> workerStatuses() {
        return dispatcher.workerStatuses();
    }

    /**
     * 当前运行超 blocking 阈值（1s，slow 10s 前的早段）的任务清单：车道键 + 任务类 + 已跑 ms。
     * system_health 直读（103000 观测补盲）——替代 jstack 偶然抓取「worker 被单任务钉死」。
     */
    public List<BlockingTaskStatus> blockingTaskStatuses() {
        return watchdog.blockingSnapshot();
    }
}
