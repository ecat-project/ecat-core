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

package com.ecat.core.Shutdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationDeviceBase;
import com.ecat.core.Integration.IntegrationRegistry;
import com.ecat.core.State.StateManager;
import com.ecat.core.Task.TaskManager;
import com.ecat.core.Task.engine.SchedulerClock;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

/**
 * core 全量停机编排（C2 优雅停机 / 重启零丢尾）——策略层：定义真实阶段与顺序，
 * 机制（预算/超时/不硬等）见 {@link ShutdownOrchestrator}。
 *
 * <p><b>阶段顺序及依据</b>（数据流：设备轮询 → 总线发布 → 消费者攒批 → 落库/状态 DB）：
 * <ol>
 * <li><b>scheduler-quiesce 停「新工作」</b>：调度引擎优雅 shutdown——周期任务到期即取消不再
 *     重排、已排队一次性任务跑完（SchedulerEngine.shutdown 的 SHUTDOWN 语义），有界等待在跑
 *     任务结束。先停新工作让事件流有不动点，后续 drain 才可能穷尽。</li>
 * <li><b>device-wind-down 设备收尾（源先停）</b>：设备型集成（{@link IntegrationDeviceBase}）
 *     onPause——停轮询/关连接，并 commit+close 本集成设备的状态 DB（IntegrationDeviceBase.onPause
 *     契约）。设备收尾自身会发布终态事件（最后一批属性/状态变更），故必须排在 drain 之前、
 *     消费者仍存活时执行，这些终态事件才能进消费者队列被下一阶段 flush——这是 drain 放在
 *     设备收尾<b>之后</b>的原因。</li>
 * <li><b>bus-drain 总线排空（汇后停）</b>：服务型集成 onPause——各批量消费者
 *     AbstractBatchBusConsumer.shutdown() = 中断消费线程 + drain 队列残留 + buffer 尾批统一
 *     flush（该契约早已存在，缺的正是停机路径对它的调用）。①②产生的全部事件（含设备终态）
 *     在此落库，即「重启零丢尾」主干。</li>
 * <li><b>state-flush 状态持久化兜底</b>：StateManager.shutdown commit+close 剩余状态 DB
 *     （设备型集成的 DB 已在②各自关闭，此处兜底非设备型/直挂设备）。此阶段不中途放弃——
 *     未 close 的 DB 恰会丢要保的尾批，宁可超预算也跑完（超时仅 WARN 记录）。</li>
 * <li><b>release-integrations</b>：全部集成 onRelease——清日志上下文/类加载器映射等。放最后
 *     因为①~④的日志与集成定位依赖这些映射。</li>
 * <li><b>pools-shutdown</b>：TaskManager.shutdownAll——引擎 shutdownNow（若①未终止则强制）+
 *     托管 executor 池收尾，之后 hook 返回、JVM 退出。</li>
 * </ol>
 *
 * <p>预算为卡死上限而非预期耗时：正常停机各阶段毫秒级完成；只有集成收尾挂死时才吃满预算。
 * 总预算 100s = 六阶段预算之和（最坏情况各阶段全部吃满时，后段仍拿全额份额、不发生
 * 「前段吃光→数据关键阶段被级联 SKIPPED」；各常量注释含 2026-08-16 终验实测依据）。
 * 配套 stop 脚本的 SIGKILL 宽限窗须 ≥ 总预算 + JVM 退出余量（~10s，即 110s），否则编排被
 * SIGKILL 截断（部署侧配置见 C4；core-integration-test 停机宽限窗同步 110s）。
 *
 * @author coffee
 */
public final class CoreShutdown {

    private static final Log log = LogFactory.getLogger(CoreShutdown.class);

    /**
     * 总预算（毫秒）：六阶段共享上限；耗尽后剩余阶段 SKIPPED，停机必然退出。
     *
     * <p>取值 = 六阶段预算之和（见下方各阶段常量）：最坏情况（各阶段全部吃满卡死上限）时，
     * 每个阶段仍拿到全额份额——尤其数据关键的 bus-drain/state-flush 不会被前段（quiesce 35s）
     * 耗尽剩余额度而级联 SKIPPED。若预算和 > 总预算，级联饿死在病态停机里必然重演。
     *
     * <p>100s 只在多阶段同时挂死的病态场景触达：正常停机秒级（终验 20.8s 的主体是旧
     * quiesce 预算不足必然烧满 + 1 个 stuck 集成份额）；2.5GB 目标机为无人值守采集单机，
     * 停机由 stop 脚本/服务管理器等待且 SIGKILL 兜底，100s 与 80s 的运维差异可忽略，
     * 换来的是数据关键阶段的最坏份额保障。state-flush 的「跑完全程不放弃」例外可越出
     * 本上限（数据安全优先于预算），SIGKILL 宽限窗按 总预算+~10s 配置即覆盖。
     */
    public static final long TOTAL_BUDGET_MS = 100_000L;

    /**
     * = 在跑任务硬超时（默认 30s，ecat.scheduler.hard-timeout-millis）+ 5s 车道排空余量。
     * quiesce 语义是「停触发 + 等在跑跑完」：等待预算低于在跑任务硬超时时，一次长事务在跑
     * 即必然超时——终验（2026-08-16，20.8s 停机）实证旧 10s 预算下串口事务（5s 硬超时）在跑
     * + 车道排空未完即 timeout(10001ms)，引擎仍在跑并连锁到 device-wind-down：对同一串口的
     * onPause 撞上在跑事务吃满 5s 单集成份额（该阶段 timeout(8377ms) = 5000ms 份额 +
     * ~3.4s 健康收尾）。预算 ≥ 硬超时后，在跑任务至多被看门狗中断于 30s、车道随后排空，
     * 等待有真完成的机会。部署上调大 hard-timeout-millis 时须同步复核本值。
     * 超长延迟一次性任务（如熔断 300s 冷却重试）仍明确不等——由 pools-shutdown 的
     * shutdownNow 取消，重试类任务停机丢弃无害。
     */
    static final long SCHEDULER_QUIESCE_MS = 35_000L;

    /**
     * 实测健康收尾 ~3.4s/全部设备型集成（终验 8377ms 扣除 5000ms 单集成 stuck 份额）；
     * 15s ≈ 2 个 stuck 设备份额（2×5s）+ 健康收尾 + 余量，预算只吃卡死场景。
     */
    static final long DEVICE_WIND_DOWN_MS = 15_000L;

    /**
     * 实测健康排空 2349ms（终验）；25s ≈ 2 个 stuck 服务消费者份额（2×10s）+ 健康排空 +
     * 余量。旧值 30s 无实测依据，为 quiesce 提至 35s 让出额度（总额恒定约束）。
     */
    static final long BUS_DRAIN_MS = 25_000L;

    /**
     * 实测 19ms（终验）；DB commit+close 是数据安全动作，预算只作超限记录不放弃
     * （见 {@link StateFlushStage}），15s 覆盖慢盘/页缓存压力下的落盘。
     */
    static final long STATE_FLUSH_MS = 15_000L;

    /**
     * 实测健康 release 全程 28ms（终验，含 1 个快速失败项）；onRelease 只清日志上下文/
     * 类加载器映射，进程即将退出、泄漏无代价——5s = 1 个 stuck 份额即止，省出额度给
     * 数据关键阶段（quiesce/drain/flush）。
     */
    static final long RELEASE_INTEGRATIONS_MS = 5_000L;

    /**
     * shutdownNow 内 timerThread.join(2000ms) 是最大单项；引擎线程全 daemon，
     * 超预算乃至被 SKIPPED 也不阻塞 JVM 退出（5s 而非旧 10s 同为让额 quiesce）。
     */
    static final long POOLS_SHUTDOWN_MS = 5_000L;

    /** 单集成 onPause 上限：设备收尾关串口/连接，正常毫秒级，卡死上限 5s 只吃自己份额。 */
    static final long PER_INTEGRATION_DEVICE_PAUSE_MS = 5_000L;
    /** 单集成 drain 上限：批写消费者 flush 尾批要落库，给到 10s。 */
    static final long PER_INTEGRATION_SERVICE_PAUSE_MS = 10_000L;
    /** 单集成 onRelease 上限：清映射/关资源，正常极快。 */
    static final long PER_INTEGRATION_RELEASE_MS = 5_000L;

    static final String STAGE_SCHEDULER_QUIESCE = "scheduler-quiesce";
    static final String STAGE_DEVICE_WIND_DOWN = "device-wind-down";
    static final String STAGE_BUS_DRAIN = "bus-drain";
    static final String STAGE_STATE_FLUSH = "state-flush";
    static final String STAGE_RELEASE = "release-integrations";
    static final String STAGE_POOLS = "pools-shutdown";

    private final TaskManager taskManager;       // 可 null（init 未跑完即停机）：对应阶段跳过
    private final StateManager stateManager;     // 可 null：同上
    private final Supplier<List<IntegrationBase>> deviceIntegrations;
    private final Supplier<List<IntegrationBase>> serviceIntegrations;
    private final Supplier<List<IntegrationBase>> allIntegrations;

    public CoreShutdown(TaskManager taskManager, StateManager stateManager,
            Supplier<List<IntegrationBase>> deviceIntegrations,
            Supplier<List<IntegrationBase>> serviceIntegrations) {
        this.taskManager = taskManager;
        this.stateManager = stateManager;
        this.deviceIntegrations = deviceIntegrations;
        this.serviceIntegrations = serviceIntegrations;
        this.allIntegrations = () -> {
            List<IntegrationBase> all = new ArrayList<>(deviceIntegrations.get());
            all.addAll(serviceIntegrations.get());
            return all;
        };
    }

    /**
     * 从注册表构建：按 {@code instanceof IntegrationDeviceBase} 分区为「设备型（源）」与
     * 「服务型（汇，含全部总线消费者宿主）」，坐标排序保证确定性。
     * registry 为 null（EcatCore.init 未执行）时两组均为空，仅剩可执行的兜底阶段。
     */
    public static CoreShutdown forRegistries(TaskManager taskManager, StateManager stateManager,
            IntegrationRegistry integrationRegistry) {
        Supplier<List<IntegrationBase>> device = () -> snapshot(integrationRegistry, true);
        Supplier<List<IntegrationBase>> service = () -> snapshot(integrationRegistry, false);
        return new CoreShutdown(taskManager, stateManager, device, service);
    }

    private static List<IntegrationBase> snapshot(IntegrationRegistry registry, boolean deviceSide) {
        if (registry == null) {
            return new ArrayList<>();
        }
        // TreeSet 按 coordinate 排序：确定性执行顺序（注册表是 HashMap，keySet 无序）
        Set<String> coordinates = new TreeSet<>(registry.getAllCoordinates());
        List<IntegrationBase> out = new ArrayList<>();
        for (String coordinate : coordinates) {
            IntegrationBase integration = registry.getIntegration(coordinate);
            if (integration == null) {
                continue;
            }
            if ((integration instanceof IntegrationDeviceBase) == deviceSide) {
                out.add(integration);
            }
        }
        return out;
    }

    /** 构建并执行停机序列（在 shutdown hook 线程上运行）。 */
    public ShutdownOrchestrator.ShutdownReport run() {
        List<ShutdownStage> stages = new ArrayList<>();
        if (taskManager != null) {
            stages.add(new SchedulerQuiesceStage());
        }
        stages.add(new IntegrationLifecycleStage(STAGE_DEVICE_WIND_DOWN, DEVICE_WIND_DOWN_MS,
                PER_INTEGRATION_DEVICE_PAUSE_MS,
                IntegrationLifecycleStage.LifecycleAction.PAUSE, deviceIntegrations));
        stages.add(new IntegrationLifecycleStage(STAGE_BUS_DRAIN, BUS_DRAIN_MS,
                PER_INTEGRATION_SERVICE_PAUSE_MS,
                IntegrationLifecycleStage.LifecycleAction.PAUSE, serviceIntegrations));
        if (stateManager != null) {
            stages.add(new StateFlushStage());
        }
        stages.add(new IntegrationLifecycleStage(STAGE_RELEASE, RELEASE_INTEGRATIONS_MS,
                PER_INTEGRATION_RELEASE_MS,
                IntegrationLifecycleStage.LifecycleAction.RELEASE, allIntegrations));
        if (taskManager != null) {
            stages.add(new PoolsShutdownStage());
        }

        ShutdownLog.info(log, "SHUTDOWN_SEQUENCE_START stages={} totalBudgetMs={}",
                stageNames(stages), TOTAL_BUDGET_MS);
        ShutdownOrchestrator.ShutdownReport report =
                new ShutdownOrchestrator(SchedulerClock.SYSTEM, TOTAL_BUDGET_MS, stages).run();
        reportStuckStages(report);
        return report;
    }

    private static List<String> stageNames(List<ShutdownStage> stages) {
        List<String> names = new ArrayList<>();
        for (ShutdownStage stage : stages) {
            names.add(stage.name());
        }
        return names;
    }

    /** 收尾汇总：未完成阶段一行汇总，供「重启零丢尾」核对时直接 grep 定位。 */
    private static void reportStuckStages(ShutdownOrchestrator.ShutdownReport report) {
        if (report.allCompleted()) {
            return;
        }
        for (ShutdownOrchestrator.StageReport stage : report.getStages()) {
            if (stage.getOutcome() != ShutdownOrchestrator.StageOutcome.COMPLETED) {
                ShutdownLog.warn(log, "SHUTDOWN_UNFINISHED stage={} outcome={} elapsedMs={} detail={}",
                        stage.getStageName(), stage.getOutcome(), stage.getElapsedMillis(), stage.getDetail());
            }
        }
    }

    // =====================================================================
    // 阶段实现
    // =====================================================================

    /** ①调度引擎优雅停：周期任务不再重排、排队一次性任务跑完，有界等待引擎 TERMINATED。 */
    private final class SchedulerQuiesceStage implements ShutdownStage {
        @Override
        public String name() {
            return STAGE_SCHEDULER_QUIESCE;
        }

        @Override
        public long budgetMillis() {
            return SCHEDULER_QUIESCE_MS;
        }

        @Override
        public boolean execute(long deadlineNanos, SchedulerClock clock) {
            // shutdown()（非 shutdownNow）= SHUTDOWN 语义：fireTask 对周期任务到期即 cancel、
            // 已入车道队列的一次性任务继续执行、worker 排空后自退——不中断在跑任务
            taskManager.getSchedulerEngine().shutdown();
            try {
                long waitNanos = deadlineNanos - clock.nanoTime();
                boolean terminated = waitNanos > 0L
                        && taskManager.getSchedulerEngine().awaitTermination(waitNanos, TimeUnit.NANOSECONDS);
                if (!terminated) {
                    ShutdownLog.warn(log, "SCHEDULER_QUIESCE_UNFINISHED（引擎未在预算内终止：周期任务已停止重排，"
                            + "残留一次性任务由守护 worker 执行、随 JVM 退出；后续 pools-shutdown 会 shutdownNow 兜底）");
                }
                return terminated;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /** ④状态持久化兜底：commit+close 剩余 DB。跑完全程不中途放弃（放弃恰会丢尾批），超时仅记 WARN。 */
    private final class StateFlushStage implements ShutdownStage {
        @Override
        public String name() {
            return STAGE_STATE_FLUSH;
        }

        @Override
        public long budgetMillis() {
            return STATE_FLUSH_MS;
        }

        @Override
        public boolean execute(long deadlineNanos, SchedulerClock clock) {
            stateManager.shutdown();
            boolean withinBudget = clock.nanoTime() <= deadlineNanos;
            if (!withinBudget) {
                ShutdownLog.warn(log, "STATE_FLUSH_OVER_BUDGET（commit+close 已完整执行完毕——数据安全优先于预算，仅记录超时）");
            }
            return withinBudget;
        }
    }

    /** ⑥线程池收尾：引擎 shutdownNow（若①未终止则强制）+ 托管 executor 池。 */
    private final class PoolsShutdownStage implements ShutdownStage {
        @Override
        public String name() {
            return STAGE_POOLS;
        }

        @Override
        public long budgetMillis() {
            return POOLS_SHUTDOWN_MS;
        }

        @Override
        public boolean execute(long deadlineNanos, SchedulerClock clock) {
            taskManager.shutdownAll();
            return true;
        }
    }
}
