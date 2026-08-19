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

package com.ecat.core.Task;

import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * 统一硬超时看门狗执行器（arch-review 27 号）：用独立小池承载「可能挂死的第三方/集成代码」，
 * 保证三件事——隔离（烂代码最多吃光本池 N 条线程，引擎/总线/Web 不受影响）、有账
 * （超时前 T-1s 抓栈 WARN 点名 + 记账统计）、系统活着（池满新提交立即 REJECTED，带 gate/label）。
 *
 * <p>诚实边界：Java 无法安全地杀死不可中断的任务——本设施不假装杀掉。超时时刻
 * watchdog 对 worker 线程发 interrupt 并让 {@link GuardedFuture} 以 TimeoutException 完成
 * （调用方立即解除阻塞）；若任务忽略 interrupt，它继续占槽直到自然结束，最坏情况是该设施
 * 拒绝服务（4 槽全占死 + 新提交全 REJECTED），系统其余部分照常运行。
 *
 * <p>调度模型：gate=串行键（同 gate FIFO，异 gate 并行）。每个 gate 一个 {@link GateLane}
 * （在途任务 + 等待队列）。提交时若 gate 已有在途/等待任务则入该 gate 等待队列（不占池槽）；
 * 否则抢占一个池槽（信号量，池大小=槽数），抢不到立即 REJECTED。worker 完成任务后优先
 * 接续本 gate 的下一个任务（槽位保持占用），异 gate 不插队。
 *
 * <p>MDC/traceId 随提交捕获、在 worker 内恢复，等价 MdcExecutorService 语义（serial 的
 * guarded 视图依赖这一点）。
 *
 * @author coffee
 */
public class GuardedExecutor {

    private static final Log log = LogFactory.getLogger(GuardedExecutor.class);

    /** 默认池大小：独立小池 4 条 daemon（隔离上限即 4 条线程）。 */
    static final int DEFAULT_POOL_SIZE = 4;
    /** 超时前提前抓栈点名的时间窗（先抓后断=证据不是噪声）。 */
    static final long STACK_SNAPSHOT_LEAD_MS = 1000;
    /** watchdog 扫描周期上限（最近的 deadline 更近则睡到 deadline）。 */
    private static final long WATCHDOG_SCAN_MS = 200;

    /** 全局共享实例：组件示范接入（tcp 首连/serial 执行通道）与 IntegrationManager 启动接入都用它。 */
    private static final GuardedExecutor DEFAULT = new GuardedExecutor(DEFAULT_POOL_SIZE, "guarded-exec");

    private final Semaphore slots;
    private final LinkedBlockingDeque<GuardTask> readyQueue = new LinkedBlockingDeque<>();
    /** gate → 车道（在途任务 + 等待队列）；lane 存在 ⇔ 该 gate 占着一个池槽。由 laneLock 保护。 */
    private final Map<String, GateLane> lanes = new ConcurrentHashMap<>();
    private final Object laneLock = new Object();
    private final ConcurrentLinkedDeque<GuardTask> running = new ConcurrentLinkedDeque<>();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong timedOut = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final Thread[] workers;
    private final Thread watchdog;
    private volatile boolean shutdown = false;

    public GuardedExecutor(int poolSize, String threadPrefix) {
        this.slots = new Semaphore(poolSize);
        this.workers = new Thread[poolSize];
        for (int i = 0; i < poolSize; i++) {
            workers[i] = new Thread(this::workerLoop, threadPrefix + "-" + i);
            workers[i].setDaemon(true);
            workers[i].start();
        }
        watchdog = new Thread(this::watchdogLoop, threadPrefix + "-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    // ==================== 共享实例便捷 API ====================

    /**
     * 默认超时：系统属性 {@code ecat.guarded.timeout-ms}，未配置 60s（配置读取集中在
     * {@link com.ecat.core.Config.EcatConfig}，含非法值严格报错）。
     */
    public static long defaultTimeoutMs() {
        return com.ecat.core.Config.EcatConfig.guardedTimeoutMs();
    }

    /** 向共享实例提交受看门狗保护的任务（gate=串行键，同 gate FIFO / 异 gate 并行）。 */
    public static <T> GuardedFuture<T> submit(String gate, String label, Callable<T> task, long timeoutMs) {
        return DEFAULT.doSubmit(gate, label, task, timeoutMs);
    }

    /**
     * 共享实例的 ExecutorService 视图：组件把原线程池替换为此视图即可零改造获得护栏
     * （label 取任务类名）。视图不持有池所有权——生命周期与批量方法不支持。
     */
    public static ExecutorService guardedExecutorFor(String gate, long timeoutMs) {
        return new GuardedView(DEFAULT, gate, timeoutMs);
    }

    /** 共享实例账目（一行可 grep 的统计）。 */
    public static String getStats() {
        return DEFAULT.stats();
    }

    // ==================== 核心 ====================

    public <T> GuardedFuture<T> doSubmit(String gate, String label, Callable<T> task, long timeoutMs) {
        if (shutdown) {
            throw new RejectedExecutionException("GuardedExecutor 已关闭, gate=" + gate + ", label=" + label);
        }
        if (gate == null || gate.isEmpty() || label == null || label.isEmpty()) {
            throw new IllegalArgumentException("gate/label 必须非空 (gate=" + gate + ", label=" + label + ")");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs 必须为正, gate=" + gate + ", label=" + label);
        }

        GuardTask<T> guardTask = new GuardTask<>(gate, label, task,
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs));

        // gate 车道已存在 → 前驱在途/排队中，入等待队列按 FIFO 接续（不占池槽，不 REJECTED）；
        // 车道不存在 → 本任务是车道头：先抢池槽（抢不到=池满立即 REJECTED），再建车道入就绪队列。
        boolean enqueuedToExistingLane = false;
        synchronized (laneLock) {
            GateLane lane = lanes.get(gate);
            if (lane != null) {
                lane.waiting.addLast(guardTask);
                enqueuedToExistingLane = true;
            }
        }
        if (!enqueuedToExistingLane) {
            if (!slots.tryAcquire()) {
                rejected.incrementAndGet();
                // 拒绝=真实工作被丢弃，必须在源头落 ERROR：GuardedView.execute 路径下本异常会被
                // CompletableFuture 吸收进 future 异常完成，调用方可能零日志——可见性不能依赖调用方自觉。
                // 点名占槽者（真凶）：被拒任务无辜，排障要抓的是占槽的谁、占多久、是否僵尸。
                log.error("guarded-task-rejected: gate={} label={} 池满({} 槽全被在途任务占用), 拒绝提交, "
                        + "占槽者: [{}], stats={}",
                        gate, label, DEFAULT_POOL_SIZE, describeOccupants(), stats());
                throw new RejectedExecutionException("GuardedExecutor 池满(" + DEFAULT_POOL_SIZE
                        + " 槽全被在途任务占用; 不可中断任务占槽至自然结束是有意的诚实边界), gate="
                        + gate + ", label=" + label + ", stats=" + stats());
            }
            synchronized (laneLock) {
                lanes.put(gate, new GateLane());
            }
            readyQueue.add(guardTask);
        }
        return guardTask.future;
    }

    private void workerLoop() {
        while (!shutdown) {
            GuardTask task;
            try {
                task = readyQueue.takeFirst();
            } catch (InterruptedException e) {
                continue;
            }
            while (task != null) {
                runOne(task);
                task = promoteNextInLane(task.gate);
                if (task == null) {
                    slots.release();
                }
            }
        }
    }

    /** 车道头完成后的晋升：等待队列取下一个作为车道头（worker 直接接续，槽位保持占用）；空则拆车道。 */
    private GuardTask promoteNextInLane(String gate) {
        synchronized (laneLock) {
            GateLane lane = lanes.get(gate);
            if (lane == null) {
                // 理论不可达（车道在任务完成前必须存在）；防御性放回槽位由调用方 release 兜底
                return null;
            }
            GuardTask next = lane.waiting.pollFirst();
            if (next != null) {
                return next;
            }
            lanes.remove(gate);
            return null;
        }
    }

    private void runOne(GuardTask task) {
        // 排队期间已被 watchdog 超时完成的任务不再执行（future 已 TimeoutException 完成）
        if (task.future.isDone()) {
            completed.incrementAndGet();
            return;
        }
        task.runner = Thread.currentThread();
        task.startedAtNanos = System.nanoTime();
        running.add(task);
        try {
            task.future.run(); // GuardedFuture 是 FutureTask：正常/异常完成都落在 future 上
        } finally {
            running.remove(task);
            task.runner = null;
            completed.incrementAndGet();
            if (task.timeoutEnforcedAtNanos >= 0) {
                long overMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - task.timeoutEnforcedAtNanos);
                log.warn("guarded-task-late-end: gate={} label={} 在超时执法 {}ms 后才自然结束"
                        + "(不可中断占槽的诚实边界), 该槽位现已归还, stats={}",
                        task.gate, task.label, overMs, stats());
                // 本任务的超时 interrupt 若未被任务消费，清除残留中断标志，防止 worker 空转自旋
                Thread.interrupted();
            }
        }
    }

    private void watchdogLoop() {
        while (!shutdown) {
            long now = System.nanoTime();
            long nearestDeadlineNanos = Long.MAX_VALUE;
            for (GuardTask task : running) {
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(task.deadlineNanos - now);
                if (remainingMs <= 0) {
                    enforceTimeout(task);
                } else {
                    if (remainingMs <= STACK_SNAPSHOT_LEAD_MS && !task.stackWarned) {
                        warnWithStack(task);
                    }
                    nearestDeadlineNanos = Math.min(nearestDeadlineNanos, task.deadlineNanos);
                }
            }
            long sleepMs = WATCHDOG_SCAN_MS;
            if (nearestDeadlineNanos != Long.MAX_VALUE) {
                sleepMs = Math.min(sleepMs,
                        Math.max(1, TimeUnit.NANOSECONDS.toMillis(nearestDeadlineNanos - System.nanoTime())));
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** T-1s 抓栈点名：先抓后断，超时执法前的证据不是噪声。 */
    private void warnWithStack(GuardTask task) {
        task.stackWarned = true;
        Thread runner = task.runner;
        StackTraceElement[] stack = runner != null ? runner.getStackTrace() : new StackTraceElement[0];
        log.warn("guarded-task-slow: gate={} label={} 即将超时(剩余<={}ms), 卡在: {}",
                task.gate, task.label, STACK_SNAPSHOT_LEAD_MS, formatTopFrames(stack, 8));
    }

    /** T 时刻执法：interrupt worker + future 以 TimeoutException 完成 + 记账 + 触发超时回调。 */
    private void enforceTimeout(GuardTask task) {
        if (!task.timeoutEnforced.compareAndSet(false, true)) {
            return;
        }
        timedOut.incrementAndGet();
        task.timeoutEnforcedAtNanos = System.nanoTime();
        // 执法顺序三步,顺序即契约:
        // 1. 先完成 future(TimeoutException)——可中断任务被 interrupt 后会以自身异常完成,
        //    先 setException 者胜,后续结果被 FutureTask 静默丢弃;
        // 2. 再触发超时回调——调用方(如 tcp 首连)在回调里终态自己的业务 future,必须赶在
        //    interrupt 让任务自身抢跑完成之前;
        // 3. 最后 interrupt——对不可中断任务只是尽力通知,占槽至自然结束是诚实边界。
        task.future.completeTimeout(new TimeoutException("guarded-task-timeout: gate=" + task.gate
                + ", label=" + task.label));
        task.future.fireOnTimeoutCallbacks();
        Thread runner = task.runner;
        if (runner != null) {
            runner.interrupt();
        }
        log.error("guarded-task-timeout: gate={} label={} 超时, 已以 TimeoutException 完成 future 并 interrupt"
                + "(若任务不可中断将继续占槽到自然结束), stats={}", task.gate, task.label, stats());
    }

    private static String formatTopFrames(StackTraceElement[] stack, int limit) {
        if (stack.length == 0) {
            return "(未运行/无栈)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.length && i < limit; i++) {
            if (i > 0) {
                sb.append(" <- ");
            }
            sb.append(stack[i]);
        }
        return sb.toString();
    }

    public String stats() {
        return "GuardedExecutor[completed=" + completed.get() + ", timedOut=" + timedOut.get()
                + ", rejected=" + rejected.get() + ", running=" + running.size()
                + ", activeGates=" + lanes.size() + "]";
    }

    /**
     * 池满拒绝日志用的占槽者点名：被拒任务是无辜受害者，真凶是占着槽的在途任务。
     * 逐个列 gate/label/已运行时长；超时已执法仍占槽的（不可中断僵尸）显式标注——
     * 这类任务是池容量被吃的直接原因，其卡点栈已由 guarded-task-slow WARN 提前捕获，
     * 此处按 gate/label 可 grep 关联，不重复打栈。
     */
    private String describeOccupants() {
        StringBuilder sb = new StringBuilder();
        for (GuardTask task : running) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append("gate=").append(task.gate).append(" label=").append(task.label);
            if (task.startedAtNanos > 0) {
                sb.append(" 已运行")
                        .append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - task.startedAtNanos))
                        .append("ms");
            }
            if (task.timeoutEnforcedAtNanos >= 0) {
                sb.append("(超时已执法仍占槽=僵尸, 已超时")
                        .append(TimeUnit.NANOSECONDS.toMillis(
                                System.nanoTime() - task.timeoutEnforcedAtNanos))
                        .append("ms)");
            }
        }
        return sb.toString();
    }

    /** 测试隔离用：中断所有 worker/watchdog（daemon 线程不阻塞 JVM 退出）。 */
    public void shutdownNow() {
        shutdown = true;
        watchdog.interrupt();
        for (Thread w : workers) {
            w.interrupt();
        }
    }

    // ==================== 任务载体 ====================

    /**
     * gate 车道：车道存在（map entry）⇔该 gate 的头任务已占一槽（在途/就绪），
     * waiting=同 gate 后续 FIFO 等待者（头任务本身不重复入 waiting，由 readyQueue/worker 持有）。
     */
    private static final class GateLane {
        final Deque<GuardTask> waiting = new ConcurrentLinkedDeque<>();
    }

    private final class GuardTask<T> {
        final String gate;
        final String label;
        final GuardedFuture<T> future;
        final long deadlineNanos;
        volatile Thread runner;
        volatile boolean stackWarned;
        final AtomicBoolean timeoutEnforced = new AtomicBoolean();
        /** >=0 表示已被超时执法（执法时刻），任务自然结束时补记 late-end 日志。 */
        volatile long timeoutEnforcedAtNanos = -1;
        /** 开跑时刻（worker 接手时打点）；>0 可算已运行时长，用于池满拒绝日志点名占槽者。 */
        volatile long startedAtNanos = -1;

        GuardTask(String gate, String label, Callable<T> task, long deadlineNanos) {
            this.gate = gate;
            this.label = label;
            this.deadlineNanos = deadlineNanos;
            // MDC/traceId 提交时捕获、worker 内恢复（与 MdcExecutorService 等价语义）
            Map<String, String> mdc = TraceContext.capture();
            this.future = new GuardedFuture<>(() -> {
                Map<String, String> previous = TraceContext.capture();
                TraceContext.restore(mdc);
                try {
                    return task.call();
                } finally {
                    TraceContext.restore(previous);
                }
            });
        }
    }

    /**
     * 受看门狗保护的 Future：watchdog 在超时时刻以 TimeoutException 完成它（调用方 get 立即返回），
     * 任务稍后的真实结果/异常被 FutureTask 静默丢弃（已在超时执法账上）。
     * {@link #onTimeout(Runnable)} 注册超时回调（tcp 首连带外清理用）。
     */
    public static final class GuardedFuture<T> extends FutureTask<T> {
        private final List<Runnable> onTimeoutCallbacks = new CopyOnWriteArrayList<>();
        private final AtomicBoolean timeoutFired = new AtomicBoolean();

        GuardedFuture(Callable<T> callable) {
            super(callable);
        }

        /** watchdog 执法入口（仅限本设施暴露 protected setException）。 */
        void completeTimeout(TimeoutException cause) {
            if (timeoutFired.compareAndSet(false, true)) {
                setException(cause);
            }
        }

        /** 注册超时回调；若超时已发生立即执行。回调异常由设施吞账并 WARN（不得影响执法主流程）。 */
        public void onTimeout(Runnable callback) {
            if (timeoutFired.get()) {
                runCallback(callback);
                return;
            }
            onTimeoutCallbacks.add(callback);
            if (timeoutFired.get()) {
                onTimeoutCallbacks.remove(callback);
                runCallback(callback);
            }
        }

        private void fireOnTimeoutCallbacks() {
            for (Runnable callback : onTimeoutCallbacks) {
                runCallback(callback);
            }
        }

        private void runCallback(Runnable callback) {
            try {
                callback.run();
            } catch (Exception e) {
                log.warn("guarded-task 超时回调异常: {}", e.toString());
            }
        }
    }

    // ==================== ExecutorService 视图 ====================

    /**
     * 组件零改造视图：execute/submit 直通 {@link GuardedExecutor#doSubmit}（label=任务类名）。
     * 生命周期与批量方法（shutdown/invokeAll/...）不支持——视图背后是共享池，不允许局部关停。
     */
    private static final class GuardedView implements ExecutorService {
        private final GuardedExecutor delegate;
        private final String gate;
        private final long timeoutMs;

        GuardedView(GuardedExecutor delegate, String gate, long timeoutMs) {
            this.delegate = delegate;
            this.gate = gate;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public void execute(Runnable command) {
            delegate.doSubmit(gate, labelOf(command), () -> {
                command.run();
                return null;
            }, timeoutMs);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.doSubmit(gate, labelOf(task), task, timeoutMs);
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return delegate.doSubmit(gate, labelOf(task), () -> {
                task.run();
                return result;
            }, timeoutMs);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegate.doSubmit(gate, labelOf(task), () -> {
                task.run();
                return null;
            }, timeoutMs);
        }

        private static String labelOf(Object task) {
            return task.getClass().getName();
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException(
                    "guarded 视图不持有线程池所有权, 不能 shutdown; 池由 GuardedExecutor 统一管理");
        }

        @Override
        public List<Runnable> shutdownNow() {
            throw new UnsupportedOperationException(
                    "guarded 视图不持有线程池所有权, 不能 shutdownNow; 池由 GuardedExecutor 统一管理");
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("guarded 视图无生命周期语义");
        }

        @Override
        public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("guarded 视图不支持批量提交");
        }

        @Override
        public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks,
                long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("guarded 视图不支持批量提交");
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("guarded 视图不支持批量提交");
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("guarded 视图不支持批量提交");
        }
    }
}
