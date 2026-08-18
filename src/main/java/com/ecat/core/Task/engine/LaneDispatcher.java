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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.ecat.core.Task.NamedThreadFactory;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

/**
 * 执行层：共享有界 worker 池 + 车道公平调度。
 *
 * <p>worker 循环：从就绪环（BlockingQueue，天然轮转 = 公平出队）取一条车道 → 弹一个任务 →
 * 执行完毕后才决定是否把该车道重新挂环。「完成后再挂环」是同车道串行的关键：车道在任务
 * 执行期间不在环内，其他 worker 拿不到它，杜绝同键并发。
 *
 * <p>背压：全引擎在排任务总量有界（maxQueued，防 OOM——旧 STPE 无界队列在故障风暴下只表现为
 * 周期漂移，无告警无上限）。满时拒绝并记录：WARN 一行 + 计数器 + 任务以
 * {@link java.util.concurrent.RejectedExecutionException} 完成（get() 不悬空、可观测、不静默）。
 *
 * <p>中断卫生：每个任务结束后清线程中断标志——硬超时中断或任务体吞掉中断都可能把标志留给
 * 车道上的下一个任务（跨任务污染，STPE 单池同样存在），清一次成本为零。
 *
 * <p>停机：shutdown() 后 worker 继续排空已入队任务后退出；shutdownNow() 中断 worker
 * （阻塞在 take 的立即退出，执行中的收到中断由任务体自行响应）。
 *
 * @author coffee
 */
final class LaneDispatcher {

    private static final Log log = LogFactory.getLogger(LaneDispatcher.class);

    private final SchedulerEngine engine;
    private final SchedulerConfig config;
    private final SchedulerClock clock;
    private final TaskWatchdog watchdog;
    private final SchedulerMetrics metrics;

    private final Map<String, Lane> lanes = new ConcurrentHashMap<>();
    /** 就绪环：等待执行的车道（每车道至多一个实例，idle CAS 去重）。 */
    private final LinkedBlockingQueue<Lane> readyRing = new LinkedBlockingQueue<>();
    private final AtomicInteger queuedTotal = new AtomicInteger();
    private final AtomicInteger activeRunners = new AtomicInteger();
    private final List<Thread> workers = new ArrayList<>();
    /**
     * 每 worker 当前正在执行的车道键（null = 空闲）。纯观测印记：任务前后各一次原子写，
     * 不参与任何调度决策——system_health 的「各 worker 忙闲」从这里读。
     */
    private final AtomicReferenceArray<String> workerLanes;
    private volatile boolean shutdown = false;

    /**
     * timer 线程退役标记：优雅停机后 timer 仍继续扫轮，把已提交且在轮上等待的一次性任务
     * 到期送入车道（STPE executeExistingDelayedTasksAfterShutdown 语义）；当表轮清空、timer
     * 退出前置此标记，worker 才允许在队列排空后终止。shutdownNow 路径 worker 被直接中断，与此标记无关。
     */
    private volatile boolean timerRetired = false;

    LaneDispatcher(SchedulerEngine engine, SchedulerConfig config, SchedulerClock clock,
                   TaskWatchdog watchdog, SchedulerMetrics metrics, boolean startThreads) {
        this.engine = engine;
        this.config = config;
        this.clock = clock;
        this.watchdog = watchdog;
        this.metrics = metrics;
        this.workerLanes = new AtomicReferenceArray<>(config.getWorkers());
        if (startThreads) {
            NamedThreadFactory factory = new NamedThreadFactory("ecat-sched-worker", true);
            for (int i = 0; i < config.getWorkers(); i++) {
                final int workerIndex = i;
                Thread worker = factory.newThread(() -> workerLoop(workerIndex));
                workers.add(worker);
                worker.start();
            }
        }
    }

    Lane lane(String key) {
        return lanes.computeIfAbsent(key, k -> new Lane(k, config, clock));
    }

    int laneCount() {
        return lanes.size();
    }

    int queuedTotal() {
        return queuedTotal.get();
    }

    /**
     * 任务入车道队列。返回 false = 排队总量达上限被拒绝（调用方负责让任务可观测失败）。
     */
    boolean enqueue(EngineTask<?> task) {
        if (queuedTotal.incrementAndGet() > config.getMaxQueuedTasks()) {
            queuedTotal.decrementAndGet();
            return false;
        }
        Lane lane = lane(task.laneKey);
        lane.queue.add(task);
        if (lane.idle.compareAndSet(true, false)) {
            readyRing.offer(lane);
        }
        return true;
    }

    private void workerLoop(int workerIndex) {
        try {
            while (true) {
                Lane lane = readyRing.poll(100, TimeUnit.MILLISECONDS);
                if (lane == null) {
                    // 优雅停机判定：环空 + 无排队 + 无在跑任务（活跃计数不为 0 时任务完成还可能回环）
                    // 且 timer 已退役（表轮清空，不再有到期任务会入队）——缺 timerRetired 时，
                    // 还在表轮上等待的一次性任务会在 worker 全退后到期无人执行（021700 第二段）。
                    if (shutdown && timerRetired && queuedTotal.get() == 0 && activeRunners.get() == 0) {
                        return;
                    }
                    continue;
                }
                EngineTask<?> task = pollLive(lane);
                if (task == null) {
                    releaseLane(lane);
                    continue;
                }
                queuedTotal.decrementAndGet();
                activeRunners.incrementAndGet();
                workerLanes.set(workerIndex, task.laneKey);
                try {
                    runTask(task, lane);
                } catch (Throwable t) {
                    // worker 线程是常驻资源：引擎侧编排代码的意外（任务体异常已被 FutureTask 吸收，
                    // 正常不应触达）只记 ERROR 不外抛——外抛会静默杀死 worker 缩减池容量
                    log.error("SCHED_WORKER_TASK_FAILED lane={} task={} 执行编排异常（worker 已继续）",
                            lane.key, task.taskLabel, t);
                } finally {
                    workerLanes.set(workerIndex, null);
                    activeRunners.decrementAndGet();
                    afterTask(lane);
                }
            }
        } catch (InterruptedException e) {
            // shutdownNow 中断退出
            Thread.currentThread().interrupt();
        }
    }

    /** 弹出车道队首的活任务（跳过被取消的惰性残留）。 */
    private EngineTask<?> pollLive(Lane lane) {
        EngineTask<?> task;
        while ((task = lane.queue.poll()) != null) {
            if (!task.isCancelled()) {
                return task;
            }
        }
        return null;
    }

    private void releaseLane(Lane lane) {
        lane.idle.set(true);
        // 复位与入队交叠：入队方 CAS 失败（idle 尚为 false）而本线程随后复位 → 丢唤醒。
        // 复位后复查队列非空再抢回挂环权。
        if (!lane.queue.isEmpty() && lane.idle.compareAndSet(true, false)) {
            readyRing.offer(lane);
        }
    }

    private void afterTask(Lane lane) {
        if (!lane.queue.isEmpty()) {
            // 串行核心：任务完成才把车道交还就绪环（执行期间 lane.idle=false 挡住新挂环）
            readyRing.offer(lane);
        } else {
            releaseLane(lane);
        }
    }

    /**
     * 单任务执行编排：看门狗登记 → 跑任务体 → 中断卫生 → 熔断记账 → 周期重排/死亡点名。
     */
    private void runTask(EngineTask<?> task, Lane lane) {
        // 每次执行重置执法层印记（周期任务复用实例，上一轮的 timedOut/slowReported/blockingReported 不得带入）
        task.timedOut = false;
        task.slowReported = false;
        task.blockingReported = false;
        task.runner = Thread.currentThread();
        task.startNanos = clock.nanoTime();
        watchdog.register(task);
        try {
            task.runOnce();
        } finally {
            watchdog.unregister(task);
            task.runner = null;
        }
        metrics.getExecuted().increment();
        // 中断卫生：任务吞掉硬超时/取消中断时标志会残留给同 worker 的下一个任务
        Thread.interrupted();

        if (task.isCancelled()) {
            // 运行中被取消：无业务结果，不参与熔断；探测名额归还
            if (task.admittedAsProbe) {
                lane.breaker.probeAborted();
            }
            return;
        }

        boolean failed = task.body.failure != null || task.timedOut;
        if (failed) {
            metrics.getFailed().increment();
        }
        lane.breaker.onOutcome(failed, task.failureSummary());

        if (task.isPeriodic()) {
            if (failed && !task.timedOut) {
                // STPE 语义：周期任务抛未捕获异常即永久停止调度。旧引擎静默死亡（设备悄悄停轮询，
                // 排障全靠猜），这里补一行死亡点名——调度行为不变，只消除静默。
                metrics.getPeriodicDiedOnException().increment();
                log.warn("周期任务因异常停止调度 lane={} task={} cause={}",
                        lane.key, task.taskLabel, task.failureSummary());
            } else {
                // 成功照常重排；硬超时中断的执行也重排（failed=true 已计入熔断，连续超时 5 次
                // 会 OPEN 冷却——比「一次超时杀死轮询直到重启」更符合自愈目标，见设计取舍）
                engine.reschedulePeriodic(task);
            }
        }
    }

    /** 优雅停机：worker 排空后自退。 */
    void shutdown() {
        shutdown = true;
    }

    /** timer 线程退役（表轮已清空，不再有新任务入队）——放行 worker 的优雅终止判定。 */
    void retireTimer() {
        timerRetired = true;
    }

    /** 立即停机：中断 worker（take 阻塞的退出、执行中的收到中断）。 */
    void shutdownNow() {
        shutdown = true;
        for (Thread worker : workers) {
            worker.interrupt();
        }
    }

    int workerCount() {
        return workers.size();
    }

    int activeRunnerCount() {
        return activeRunners.get();
    }

    /** 全部 worker 线程已退出（isTerminated 判定用；测试模式无线程恒真）。 */
    boolean workersDead() {
        for (Thread worker : workers) {
            if (worker.isAlive()) {
                return false;
            }
        }
        return true;
    }

    /** 清空全部车道队列（shutdownNow 用；任务取消由调用方统一处理）。 */
    List<EngineTask<?>> drainQueues() {
        List<EngineTask<?>> drained = new ArrayList<>();
        for (Lane lane : lanes.values()) {
            EngineTask<?> t;
            while ((t = lane.queue.poll()) != null) {
                queuedTotal.decrementAndGet();
                drained.add(t);
            }
        }
        return drained;
    }

    /** 各车道熔断器快照（B2 自观测）。 */
    Map<String, TaskCircuitBreaker> breakers() {
        Map<String, TaskCircuitBreaker> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Lane> e : lanes.entrySet()) {
            snapshot.put(e.getKey(), e.getValue().breaker);
        }
        return snapshot;
    }

    /** 各 worker 忙闲快照（B2 自观测）：线程名 + 当前执行车道（null = 空闲）。 */
    List<WorkerStatus> workerStatuses() {
        List<WorkerStatus> out = new ArrayList<>(workers.size());
        for (int i = 0; i < workers.size(); i++) {
            out.add(new WorkerStatus(workers.get(i).getName(), workerLanes.get(i)));
        }
        return out;
    }
}
