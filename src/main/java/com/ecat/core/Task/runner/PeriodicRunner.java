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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * 传输 SDK 周期链工具（29 号 v2「core=业务+库」的库侧：纯代码库类，零中心运行时足迹）。
 *
 * <p><b>为什么是库不是引擎</b>：定时需求的共性（周期+完成点重排）≠执行机制应该共性——
 * 协议执行特点（serial 命令留隙/modbus 从站分块/tcp 墙钟相位/http 相位推送窗）全在域侧，
 * 本类不自带线程池、不注册任何治理，执行器由消费方 SDK 传入并拥有；域间无共享排队点，
 * 跨域饿死结构性不可能。供给两原语：
 * <ul>
 *   <li>{@link #fireAfter(Runnable, long)}——MDC 包装到点单发（提交时捕获上下文、到拍
 *       恢复、无 traceId 补生成），域内超时执法/重试退避直接消费；</li>
 *   <li>{@link #periodic(String, Supplier, RoundSchedule)}——完成点重排周期链：轮事务
 *       CF 结算点触发下一拍，逐轮 MDC（提交 coordinate+每轮新 traceId）内置，网格/相位
 *       策略经 {@link RoundSchedule} 参数化（域差异留域侧实现，http 域 S0 穿刺已验证）。</li>
 * </ul>
 *
 * <p>用法（SDK 自持执行器）：
 * <pre>{@code
 * ScheduledExecutorService pool = new ScheduledThreadPoolExecutor(2,
 *         new NamedThreadFactory("ecat-xxx-sched", true));
 * PeriodicRunner runner = PeriodicRunner.on(pool);
 * PeriodicChain chain = runner.periodic("dev-1", this::pollRound, myGridSchedule).start();
 * host.onRemove(chain::cancel);   // 统一停机挂生命周期
 * }</pre>
 */
public final class PeriodicRunner {

    private final ShotScheduler scheduler;

    private PeriodicRunner(ShotScheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler 不能为 null（传执行器或测试缝替身）");
        }
        this.scheduler = scheduler;
    }

    /** 消费方自带执行器构造（SDK 自持 STPE / 测试自备真池）。 */
    public static PeriodicRunner on(ScheduledExecutorService owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner 不能为 null（执行器由消费方拥有）");
        }
        return new PeriodicRunner((command, delayMillis) ->
                owner.schedule(command, delayMillis, TimeUnit.MILLISECONDS));
    }

    /** 窄缝构造：消费方测试缝替身（捕获型 fake）直接注入。 */
    public static PeriodicRunner on(ShotScheduler seam) {
        return new PeriodicRunner(seam);
    }

    /**
     * MDC 包装单发：提交时捕获上下文（coordinate）、到拍恢复、无 traceId 补生成。
     *
     * @throws java.util.concurrent.RejectedExecutionException 执行器已停机（调用方按域语义收口）
     */
    public ScheduledFuture<?> fireAfter(Runnable command, long delayMillis) {
        Map<String, String> context = TraceContext.capture();
        return scheduler.fireAfter(TraceContext.wrapRunnable(command, context), delayMillis);
    }

    /**
     * 建周期链（未起链）：{@link PeriodicChain#start()} 时按
     * {@link RoundSchedule#firstDelayMillis()} 排首发。
     *
     * @param name     链名（停机/异常日志定位词汇，域侧惯用 target/设备名）
     * @param round    轮体：返回轮事务 CF（结算点即下一拍计时起点）；Boolean=业务成功、
     *                 异常完成=传输错误——任何终态都重排，begin 同步抛按异常轮等价处理
     * @param schedule 周期策略（fixedDelay/fixedRate/相位族，域侧实现）
     */
    public PeriodicChain periodic(String name, Supplier<? extends CompletableFuture<?>> round,
            RoundSchedule schedule) {
        if (name == null) {
            throw new IllegalArgumentException("name 不能为 null");
        }
        if (round == null) {
            throw new IllegalArgumentException("round 不能为 null（一轮做什么）");
        }
        if (schedule == null) {
            throw new IllegalArgumentException("schedule 不能为 null（周期策略）");
        }
        return new PeriodicChain(this, name, round, schedule);
    }

    ShotScheduler scheduler() {
        return scheduler;
    }
}
