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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.ecat.core.Log.LogMarkers;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

import lombok.Getter;

/**
 * 每车道熔断器：连续失败 N 次 → OPEN（冷却期内周期任务直接跳过）→ 半开探测 1 个任务 →
 * 成功关闭 / 失败重开。参数与状态转移日志对应 TB Gateway 的「5 次失败 → 5 分钟冷却」模式
 * （调研结论：受限资源下对持续失败设备停止无效轮询，比反复超时占用 worker 便宜得多）。
 *
 * <p>两个命名空间共用本状态机：调度车道熔断（任务异常经 {@link #onOutcome}）与设备通讯熔断
 * （D2，通讯失败经 {@link #onCommFailure}/{@link #onCommSuccess}，见 {@link CommBreakerRegistry}）；
 * 两类失败信号同权计数，状态转移日志均带 COMM marker 进 comm-health.log 通道。
 *
 * <p>状态机（全部转移一行一事，INFO + COMM marker 进 comm-health.log 通道）：
 * <ul>
 *   <li>CLOSED→OPEN：{@code CIRCUIT_OPEN (连续失败 5 次: {最后错误摘要}, 冷却 300s)}</li>
 *   <li>OPEN→PROBING：冷却期满后第一个任务被接纳为探测</li>
 *   <li>PROBING→CLOSED：探测成功</li>
 *   <li>PROBING→OPEN：探测失败（复用 CIRCUIT_OPEN 日志行，冷却重新计时）</li>
 * </ul>
 *
 * <p>准入规则（admit 在表轮到期、任务入车道队列之前由 timer 线程调用）：
 * <ul>
 *   <li>CLOSED：全部放行</li>
 *   <li>OPEN（冷却未满）：周期任务跳过；一次性任务放行——重连/发现这类恢复动作必须能执行，
 *       否则熔断期间设备永远无法自愈</li>
 *   <li>OPEN（冷却已满）：第一个任务（周期或一次性）转为探测，进入 PROBING</li>
 *   <li>PROBING：后续周期任务跳过（探测唯一性）；一次性任务放行，其结果同样驱动状态转移</li>
 * </ul>
 *
 * <p>线程模型：admit/onOutcome 可能来自 timer 线程与多个 worker 线程，一把 ReentrantLock
 * 串行化（每车道每任务一次，速率 = 任务周期级，锁开销可忽略）。
 *
 * <p>测试注入：时钟经构造器注入（冷却判定全部走 clock，不读墙钟）。
 *
 * @author coffee
 */
public class TaskCircuitBreaker {

    /** 熔断状态（对外只读快照经 {@link #getState()}）。 */
    public enum State { CLOSED, OPEN, PROBING }

    /** 准入判定结果。 */
    public enum Decision {
        /** 放行（CLOSED 常态，或 OPEN 期间的一次性恢复任务）。 */
        ADMIT,
        /** 放行且本任务消耗了探测名额（OPEN 冷却期满后第一个被接纳的任务，其结果驱动 PROBING→CLOSED/OPEN）。 */
        ADMIT_AS_PROBE,
        /** 跳过：OPEN/PROBING 期间的周期任务。 */
        SKIP_PERIODIC
    }

    private static final Log log = LogFactory.getLogger(TaskCircuitBreaker.class);

    @Getter private final String laneKey;
    private final int failThreshold;
    private final long cooldownNanos;
    private final SchedulerClock clock;

    private final ReentrantLock lock = new ReentrantLock();
    @Getter private State state = State.CLOSED;
    @Getter private int consecutiveFailures = 0;
    @Getter private long cooldownEndNanos = 0L;
    @Getter private long skippedCount = 0L;
    /** 最近一次失败的摘要（进 OPEN 日志行，供运维直接看到病因）。 */
    @Getter private String lastFailureSummary = "";

    /** OPEN→PROBING 转移次数（B2 自观测暴露用）。 */
    private final AtomicLong openCount = new AtomicLong();
    @Getter private volatile long openedAtNanos = -1L;

    /** 累计通讯失败上报次数（B2 自观测；任务异常不计入，区分两类熔断诱因）。 */
    private final AtomicLong commFailureCount = new AtomicLong();

    /** 通讯失败触发的 OPEN 日志 reason 前缀（区分任务异常诱因）。 */
    static final String COMM_FAILURE_REASON_PREFIX = "通讯失败: ";

    public TaskCircuitBreaker(String laneKey, int failThreshold, long cooldownMillis, SchedulerClock clock) {
        this.laneKey = laneKey;
        this.failThreshold = failThreshold;
        this.cooldownNanos = TimeUnit.MILLISECONDS.toNanos(cooldownMillis);
        this.clock = clock;
    }

    /**
     * 任务到期时问熔断器是否放行入车道。
     *
     * @param periodic true=周期任务（OPEN/PROBING 期间可跳过）；false=一次性任务（始终放行）
     */
    public Decision admit(boolean periodic) {
        lock.lock();
        try {
            long now = clock.nanoTime();
            switch (state) {
                case CLOSED:
                    return Decision.ADMIT;
                case OPEN:
                    if (now < cooldownEndNanos) {
                        if (periodic) {
                            skippedCount++;
                            return Decision.SKIP_PERIODIC;
                        }
                        return Decision.ADMIT;
                    }
                    // 冷却期满：第一个到期的任务转为探测（对周期任务而言这就是恢复轮询的第一枪）
                    state = State.PROBING;
                    log.info(LogMarkers.COMM, "CIRCUIT_OPEN -> PROBING lane={} 冷却结束，下一任务作为探测放行", laneKey);
                    return Decision.ADMIT_AS_PROBE;
                case PROBING:
                default:
                    if (periodic) {
                        skippedCount++;
                        return Decision.SKIP_PERIODIC;
                    }
                    return Decision.ADMIT;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 记录一次执行结果（worker 在任务终态时调用，每任务恰好一次）。
     *
     * <p>状态矩阵：PROBING 结果决定开合；OPEN 期间一次性任务失败续期冷却、成功直接关闭
     * （车道内任何真实成功都是最强的健康信号）；CLOSED 常规计数。
     */
    public void onOutcome(boolean failed, String failureSummary) {
        lock.lock();
        try {
            switch (state) {
                case PROBING:
                    if (failed) {
                        transitionToOpen(failureSummary);
                    } else {
                        state = State.CLOSED;
                        consecutiveFailures = 0;
                        log.info(LogMarkers.COMM, "PROBING -> CLOSED lane={} 探测成功，恢复调度", laneKey);
                    }
                    return;
                case OPEN:
                    if (failed) {
                        transitionToOpen(failureSummary);
                    } else {
                        state = State.CLOSED;
                        consecutiveFailures = 0;
                        log.info(LogMarkers.COMM, "CIRCUIT_OPEN -> CLOSED lane={} 熔断期内一次性任务执行成功", laneKey);
                    }
                    return;
                case CLOSED:
                default:
                    if (failed) {
                        consecutiveFailures++;
                        lastFailureSummary = failureSummary == null ? "" : failureSummary;
                        if (consecutiveFailures >= failThreshold) {
                            transitionToOpen(lastFailureSummary);
                        }
                    } else {
                        consecutiveFailures = 0;
                    }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 通讯失败信号（D2，设备通讯熔断命名空间的主入口）：什么算「通讯失败」由调用方判定
     * （串口/总线超时、无有效响应、IO 异常），熔断器只数连败——与任务异常
     * {@link #onOutcome(boolean, String)} 同权进入连败计数（同一熔断器内混排亦然），
     * 成功清零，达到阈值 OPEN 并复用既有冷却/半开/探测机制。
     *
     * <p>OPEN 日志行的 reason 以「{@value #COMM_FAILURE_REASON_PREFIX}」前缀区分任务异常触发的 OPEN。
     */
    public void onCommFailure(String summary) {
        commFailureCount.incrementAndGet();
        onOutcome(true, COMM_FAILURE_REASON_PREFIX + (summary == null ? "" : summary));
    }

    /** 通讯成功信号：与任务成功同权（清零连败；PROBING/OPEN 期成功直接关闭）。 */
    public void onCommSuccess() {
        onOutcome(false, null);
    }

    /**
     * 探测任务未得出结果即被取消（设备停止/移除等）：探测名额作废。
     *
     * <p>回到 OPEN 且冷却即刻到期——下个到期任务立刻再获探测资格：取消是提交方主动撤回，
     * 不应惩罚车道整段冷却；也不留在 PROBING（否则后续周期任务会被无限跳过）。
     */
    public void probeAborted() {
        lock.lock();
        try {
            if (state == State.PROBING) {
                state = State.OPEN;
                cooldownEndNanos = clock.nanoTime();
            }
        } finally {
            lock.unlock();
        }
    }

    private void transitionToOpen(String failureSummary) {
        state = State.OPEN;
        cooldownEndNanos = clock.nanoTime() + cooldownNanos;
        consecutiveFailures = 0;
        lastFailureSummary = failureSummary == null ? "" : failureSummary;
        openCount.incrementAndGet();
        openedAtNanos = clock.nanoTime();
        log.info(LogMarkers.COMM, "CIRCUIT_OPEN (连续失败 {} 次: {}, 冷却 {}s) lane={}",
                failThreshold, lastFailureSummary, TimeUnit.NANOSECONDS.toSeconds(cooldownNanos), laneKey);
    }

    /** OPEN 累计次数（B2 自观测）。 */
    public long getOpenCount() {
        return openCount.get();
    }

    /** 累计通讯失败上报次数（B2 自观测）。 */
    public long getCommFailureCount() {
        return commFailureCount.get();
    }

    /**
     * 冷却剩余毫秒数（B2 自观测）：OPEN 状态下距冷却结束的时长，经构造器注入的时钟计算
     * （测试可用虚拟时钟精确断言）。非 OPEN 状态无冷却语义，返回 0。
     */
    public long cooldownRemainingMillis() {
        if (state != State.OPEN) {
            return 0L;
        }
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(cooldownEndNanos - clock.nanoTime()));
    }
}
