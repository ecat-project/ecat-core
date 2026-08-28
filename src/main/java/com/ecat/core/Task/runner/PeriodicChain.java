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

package com.ecat.core.Task.runner;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * 完成点重排周期链句柄与链体（http 域 S0 穿刺实现抽取，29 号 v2 库级工具）：
 * 每拍一个单发提交，轮事务 {@link CompletableFuture} 结算点触发下一拍重排——
 * 单飞由结构保证（在飞期间不排下一拍），异常轮（含 begin 同步抛）<b>永不注销</b>。
 *
 * <p><b>逐轮 MDC</b>（复用 core {@link TraceContext}，消费方免写）：提交时捕获上下文
 * （coordinate），到拍恢复并生成新 traceId（每轮独立追踪）；结算续段恢复<b>本轮</b>上下文
 * 后取下一拍——完成线程可能是任意线程，凭轮内捕获的快照还原。
 *
 * <p><b>停机语义</b>：续段排拍遇 {@link RejectedExecutionException} 显式收口（链终止可
 * 观测，不静默死在 CF 回调里）；{@link #start()} 首发不收口——在已停机的执行器上起链是
 * 调用方错误，严格上抛。
 *
 * <p><b>cancel 竞态收口</b>：置停链标志后撤销当前待发单发（false=不中断在飞执行，交给
 * 域侧超时执法）；{@link #trackPending} 与 {@link #cancel()} 的交错由「先登记后查标志」
 * 收口——cancel 先行则登记方当场撤销。链上中段单发（重试退避等）经 {@link #fireAfter}
 * 提交即纳入同一收口。
 */
public final class PeriodicChain {

    private static final Log log = LogFactory.getLogger(PeriodicChain.class);

    private final PeriodicRunner runner;
    private final String name;
    private final Supplier<? extends CompletableFuture<?>> round;
    private final RoundSchedule schedule;

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile ScheduledFuture<?> pendingShot;
    private volatile Map<String, String> submitContext;

    PeriodicChain(PeriodicRunner runner, String name,
            Supplier<? extends CompletableFuture<?>> round, RoundSchedule schedule) {
        this.runner = runner;
        this.name = name;
        this.round = round;
        this.schedule = schedule;
    }

    /**
     * 起链：捕获提交上下文并按 {@link RoundSchedule#firstDelayMillis()} 排首发。
     *
     * @return this（流式：{@code runner.periodic(...).start()}）
     * @throws RejectedExecutionException 执行器已停机（调用方错误，严格上抛）
     * @throws IllegalStateException 链已 start（双链竞态守卫）
     */
    public PeriodicChain start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("链已 start，不可复用: " + name);
        }
        this.submitContext = TraceContext.capture();
        trackPending(runner.scheduler().fireAfter(this::fireRound,
                Math.max(0L, schedule.firstDelayMillis())));
        return this;
    }

    /** 停链（幂等）：置停链标志并撤销待发单发；在飞轮的结果被丢弃、不再重排。 */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            ScheduledFuture<?> pending = pendingShot;
            if (pending != null) {
                pending.cancel(false);
            }
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** 周期链是否仍在（周期链不会自然结束，false 仅因 cancel 或执行器停机）。 */
    public boolean isRunning() {
        return !cancelled.get();
    }

    /**
     * 链上中段单发（重试退避/超时执法等域语义）：MDC 包装语义同
     * {@link PeriodicRunner#fireAfter(Runnable, long)}，且纳入链 cancel 收口。
     *
     * @throws RejectedExecutionException 执行器已停机（调用方按域语义收口）
     */
    public ScheduledFuture<?> fireAfter(Runnable command, long delayMillis) {
        ScheduledFuture<?> future = runner.fireAfter(command, delayMillis);
        trackPending(future);
        return future;
    }

    /** 自排链每拍登记待发单发；cancel 已发生则当场撤销（与 {@link #cancel()} 竞态收口）。 */
    void trackPending(ScheduledFuture<?> future) {
        this.pendingShot = future;
        if (cancelled.get()) {
            future.cancel(false);
        }
    }

    /**
     * 一拍的发射体（执行器线程上执行）：过期即弃判定 → 逐轮 MDC（提交 coordinate+新
     * traceId）→ 发起轮事务 → 结算续段（恢复本轮上下文）重排下一拍。
     */
    private void fireRound() {
        if (cancelled.get()) {
            return;
        }
        FireDecision decision = schedule.onFire();
        if (!decision.shouldRun()) {
            arm(decision.rearmDelayMillis());
            return;
        }
        Map<String, String> previous = TraceContext.capture();
        CompletableFuture<?> transaction;
        Map<String, String> roundContext;
        try {
            TraceContext.restore(submitContext);
            TraceContext.setTraceId(TraceContext.generateTraceId());
            // 本轮上下文写回（含本轮 traceId）：结算续段可能在任意完成线程执行，凭此恢复
            roundContext = TraceContext.capture();
            try {
                transaction = round.get();
                if (transaction == null) {
                    // 轮体契约违反（须返回轮事务 CF）：按异常轮结算并显式点名，不得让 NPE
                    // 在执行器线程上无声终止链路（永不注销原则优先，错误逐轮可见）
                    throw new IllegalStateException("轮体返回 null（契约：返回轮事务 CF）");
                }
            } catch (RuntimeException e) {
                // begin 同步抛=异常轮等价（failedFuture 语义）：结算续段照常重排——永不注销。
                // 消费方需感知异常（记账/回调）请在轮体内自捕；此处兜的是「永不注销」原则本身
                log.error("[periodic] {} 轮体 begin 异常（按异常轮结算）: {}",
                        name, e.getMessage(), e);
                CompletableFuture<Object> failed = new CompletableFuture<>();
                failed.completeExceptionally(e);
                transaction = failed;
            }
        } finally {
            TraceContext.restore(previous);
        }
        transaction.whenComplete((value, failure) -> {
            Map<String, String> settlePrevious = TraceContext.capture();
            try {
                TraceContext.restore(roundContext);
                settleAndRearm();
            } finally {
                TraceContext.restore(settlePrevious);
            }
        });
    }

    /** 结算续段重排（完成线程执行，已恢复本轮 MDC）：任何终态都重排——异常永不注销。 */
    private void settleAndRearm() {
        if (cancelled.get()) {
            return;
        }
        arm(schedule.onSettleRearmMillis());
    }

    /** 排下一拍单发；停机期 REE 显式收口（链终止可观测，不静默死在 CF 回调里）。 */
    private void arm(long delayMillis) {
        try {
            trackPending(runner.scheduler().fireAfter(this::fireRound, Math.max(0L, delayMillis)));
        } catch (RejectedExecutionException e) {
            log.warn("[periodic] {} 定时器已停机，周期链终止", name);
        }
    }
}
