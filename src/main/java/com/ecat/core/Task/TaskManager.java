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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import com.ecat.core.Utils.Mdc.MdcScheduledExecutorService;

/**
 * TaskManager class is responsible for managing the shared business scheduler.
 *
 * <p>调度终态（W7，R7 战役）：集中调度引擎（SchedulerEngine）与执行 API
 * （CoreExecutionApi）已整目录退役——定时/执行语义归各域自持（传输 SDK 的
 * SdkTimers 域池 + core 库级 {@link com.ecat.core.Task.runner.PeriodicRunner}），
 * 本类只剩一类职责：集成层业务计时器（bizScheduler，纯计算 tick）。
 * 会阻塞的 IO 轮询禁走本类任何入口，归域侧池（SerialIoPool/ModbusIoPool 等）。
 *
 * <p>createMdc* 托管线程池工厂已随 E4-1 收编退役：全部消费方迁
 * {@link com.ecat.core.Task.runner.HostedExecutors}（MDC + 命名 + 宿主停机绑定
 * 三合一），不再需要进程级 managedExecutors 停机账本——池的生命周期挂宿主
 * （集成 onRemove sweep），不挂 core 进程。
 *
 * <p>Usage example:
 * <pre>
 * // For scheduled tasks (timers, periodic execution)
 * ScheduledExecutorService scheduler = taskManager.getBizScheduler();
 * scheduler.scheduleWithFixedDelay(() -> {...}, 0, 5, TimeUnit.SECONDS);
 * </pre>
 *
 * @author coffee
 */
public class TaskManager {

    /**
     * 集成层业务计时器（S2；W7 后是本类唯一的共享调度器）：普通 ScheduledThreadPoolExecutor
     * ——业务 tick（时钟显示、随机源、指标采集等纯计算节律）。W7 引擎退役后旧入口
     * {@code getExecutorService()}（别名）已物理删除（R7 收尾），全部消费面统一走
     * {@link #getBizScheduler()}：毫秒级纯内存计算（demo 型糖模板 tick 等），
     * 与池的既有定位一致。
     *
     * <p>共享单池而非每集成一池：集成数 ~85，每集成一池是线程爆炸；实际消费面只有
     * 服务型集成的个位数周期/一次性任务，全部毫秒级纯内存计算，2 线程足够（一条在执行、
     * 一条保并行，无 IO 任务排队）。生命周期随 core：停机由 CoreShutdown 的
     * scheduler-quiesce 阶段停新工作（保住「事件流不动点」语义）、shutdownAll 兜底
     * shutdownNow。
     */
    private final ScheduledExecutorService bizScheduler;

    public TaskManager() {
        // 业务池构造：removeOnCancel 清除已取消句柄（一次性延迟任务取消后不再滞留队列），
        // 守护线程 + NamedThreadFactory 具名（B6 线程归因，ThreadNamingArchTest 规则 2 立法）。
        ScheduledThreadPoolExecutor raw = new ScheduledThreadPoolExecutor(2,
            new NamedThreadFactory("ecat-biz-sched", true));
        raw.setRemoveOnCancelPolicy(true);
        this.bizScheduler = MdcScheduledExecutorService.wrap(raw);
    }

    /**
     * 集成层业务计时器（S2）：{@link IntegrationBase#getBizScheduler()} 的底层池，
     * 普通具名守护 STPE（ecat-biz-sched-N）+ MDC 包装（TraceContext 传播、周期任务每执行
     * 换 traceId）。业务 tick 专用——会阻塞的 IO 轮询禁走此池（2 线程小池，IO 会饿死全部
     * 业务计时），IO 归域侧自持池（SerialIoPool/ModbusIoPool 等）。
     */
    public ScheduledExecutorService getBizScheduler() {
        return this.bizScheduler;
    }

    /**
     * Shutdown the integration business scheduler.
     * （quiesce 阶段已优雅 shutdown，此处兜底强停；createMdc* 托管账本已随 E4-1 退役，
     * 池生命周期归各宿主，本方法只余共享业务池。）
     */
    public void shutdownAll() {
        // Shutdown integration business scheduler（quiesce 阶段已优雅 shutdown，此处兜底强停）
        this.bizScheduler.shutdownNow();
    }
}
