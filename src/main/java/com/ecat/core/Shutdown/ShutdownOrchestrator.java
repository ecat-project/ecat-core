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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.ecat.core.Task.engine.SchedulerClock;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

/**
 * 停机编排器（C2 优雅停机的机制层）：按序执行 {@link ShutdownStage}，每阶段独立预算，
 * 共享一个总预算；超时/异常只 WARN 不硬等——停机序列必须最终能退（SIGKILL 兜底前尽量多收尾）。
 *
 * <p>预算语义：阶段实际预算 = min(阶段预算, 总预算剩余)；总预算耗尽后未执行阶段直接 SKIPPED。
 * 这保证最坏停机时长有界（各阶段预算之和与总预算取小），而正常情况下各阶段毫秒级完成、
 * 预算只是卡死上限。
 *
 * <p>时钟注入（复用调度引擎的 {@link SchedulerClock} 抽象）：生产 SYSTEM 真实时钟；单测用
 * 可变时钟手动推进，零真实等待驱动「阶段超时/总预算耗尽」分支。
 *
 * <p>阶段异常（含 Error）：编排器捕获记 FAILED 后继续下一阶段——停机路径上任何单点失败
 * 都不应阻止其余收尾（数据 flush 优先于异常上抛）。
 *
 * <p>停机期日志走 {@link ShutdownLog} 双通道（stdout 同步写 + 原 logger 尽力而为）：
 * app 通道全是 AsyncAppender，JVM 退出时队列不保证排空——stdout（fd 直写 core-api.log）
 * 是停机期唯一有保底的出口；序列结束（完整/不完整两路径）再输出一行 SHUTDOWN_SUMMARY。
 *
 * @author coffee
 */
public final class ShutdownOrchestrator {

    private static final Log log = LogFactory.getLogger(ShutdownOrchestrator.class);

    /** 阶段结局：完成 / 超时（含部分单元未完成）/ 异常 / 总预算耗尽未执行。 */
    public enum StageOutcome { COMPLETED, TIMEOUT, FAILED, SKIPPED }

    /** 单阶段执行报告（只读）。 */
    public static final class StageReport {
        private final String stageName;
        private final StageOutcome outcome;
        private final long elapsedMillis;
        private final String detail;

        StageReport(String stageName, StageOutcome outcome, long elapsedMillis, String detail) {
            this.stageName = stageName;
            this.outcome = outcome;
            this.elapsedMillis = elapsedMillis;
            this.detail = detail;
        }

        public String getStageName() { return stageName; }
        public StageOutcome getOutcome() { return outcome; }
        public long getElapsedMillis() { return elapsedMillis; }
        public String getDetail() { return detail; }

        @Override
        public String toString() {
            return stageName + ":" + outcome.name().toLowerCase() + "(" + elapsedMillis + "ms)";
        }
    }

    /** 整次停机编排报告（只读）。 */
    public static final class ShutdownReport {
        private final List<StageReport> stages;
        private final long totalElapsedMillis;

        ShutdownReport(List<StageReport> stages, long totalElapsedMillis) {
            this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
            this.totalElapsedMillis = totalElapsedMillis;
        }

        public List<StageReport> getStages() { return stages; }

        public long getTotalElapsedMillis() { return totalElapsedMillis; }

        /** 全部阶段 COMPLETED（任一 TIMEOUT/FAILED/SKIPPED 即 false——排障入口）。 */
        public boolean allCompleted() {
            for (StageReport stage : stages) {
                if (stage.getOutcome() != StageOutcome.COMPLETED) {
                    return false;
                }
            }
            return true;
        }

        /** 按阶段名查报告；不存在返回 null。 */
        public StageReport find(String stageName) {
            for (StageReport stage : stages) {
                if (stage.getStageName().equals(stageName)) {
                    return stage;
                }
            }
            return null;
        }
    }

    private final SchedulerClock clock;
    private final long totalBudgetNanos;
    private final long totalBudgetMillis;
    private final List<ShutdownStage> stages;

    /**
     * @param clock            单调时钟（测试注入虚拟时钟）
     * @param totalBudgetMillis 总预算（毫秒）：全部阶段共享的上限，耗尽后剩余阶段 SKIPPED
     * @param stages           按执行顺序排列的阶段列表（非空）
     */
    public ShutdownOrchestrator(SchedulerClock clock, long totalBudgetMillis, List<ShutdownStage> stages) {
        if (clock == null) {
            throw new IllegalArgumentException("clock 不能为 null");
        }
        if (totalBudgetMillis <= 0) {
            throw new IllegalArgumentException("totalBudgetMillis 必须 > 0: " + totalBudgetMillis);
        }
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("stages 不能为空");
        }
        this.clock = clock;
        this.totalBudgetMillis = totalBudgetMillis;
        this.totalBudgetNanos = totalBudgetMillis * 1_000_000L;
        this.stages = new ArrayList<>(stages);
    }

    /**
     * 执行停机序列。在调用线程（JVM shutdown hook 线程）上串行跑各阶段；
     * 每阶段超时只 WARN 不硬等，异常捕获后继续——保证本方法必然返回。
     */
    public ShutdownReport run() {
        long startNanos = clock.nanoTime();
        List<StageReport> reports = new ArrayList<>();

        for (ShutdownStage stage : stages) {
            long remainingTotal = totalBudgetNanos - (clock.nanoTime() - startNanos);
            if (remainingTotal <= 0L) {
                // 总预算耗尽：不再执行剩余阶段（停机必须最终能退），逐个记 SKIPPED 供报告定位
                ShutdownLog.warn(log, "SHUTDOWN_STAGE_SKIPPED stage={} reason=total-budget-exhausted（总预算 {}ms 已耗尽，跳过以保证停机退出）",
                        stage.name(), totalBudgetMillis);
                reports.add(new StageReport(stage.name(), StageOutcome.SKIPPED, 0L, "总预算耗尽"));
                continue;
            }

            long budgetNanos = Math.min(stage.budgetMillis() * 1_000_000L, remainingTotal);
            long stageStart = clock.nanoTime();
            long deadlineNanos = stageStart + budgetNanos;

            StageOutcome outcome;
            String detail = "";
            try {
                outcome = stage.execute(deadlineNanos, clock) ? StageOutcome.COMPLETED : StageOutcome.TIMEOUT;
            } catch (RuntimeException | Error e) {
                // 阶段异常不阻断后续阶段：其余收尾（尤其数据 flush）优先于单点异常上抛
                ShutdownLog.error(log, "SHUTDOWN_STAGE_FAILED stage={}（异常已捕获，继续后续阶段）", stage.name(), e);
                outcome = StageOutcome.FAILED;
                detail = e.toString();
            }
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(clock.nanoTime() - stageStart);

            switch (outcome) {
                case COMPLETED:
                    ShutdownLog.info(log, "SHUTDOWN_STAGE_DONE stage={} elapsedMs={}", stage.name(), elapsedMillis);
                    break;
                case TIMEOUT:
                    ShutdownLog.warn(log, "SHUTDOWN_STAGE_TIMEOUT stage={} budgetMs={} elapsedMs={}（未完成项已在上方逐条 WARN；不硬等，进入下一阶段）",
                            stage.name(), stage.budgetMillis(), elapsedMillis);
                    break;
                default:
                    break;
            }
            reports.add(new StageReport(stage.name(), outcome, elapsedMillis, detail));
        }

        ShutdownReport report = new ShutdownReport(reports,
                TimeUnit.NANOSECONDS.toMillis(clock.nanoTime() - startNanos));
        if (report.allCompleted()) {
            ShutdownLog.info(log, "SHUTDOWN_SEQUENCE_COMPLETE totalElapsedMs={} stages={}",
                    report.getTotalElapsedMillis(), report.getStages());
        } else {
            ShutdownLog.warn(log, "SHUTDOWN_SEQUENCE_INCOMPLETE totalElapsedMs={} stages={}（存在未完成阶段，尾批可能丢失——按 stage 定位）",
                    report.getTotalElapsedMillis(), report.getStages());
        }
        // 最终汇总单行（仅 stdout——停机期唯一有保底的通道）：完整/不完整两路径都输出，
        // 各阶段 COMPLETED/TIMEOUT/FAILED/SKIPPED 与耗时一行看全，重启后核对/排障直接 grep 此前缀。
        ShutdownLog.emitLine("SHUTDOWN_SUMMARY totalElapsedMs=" + report.getTotalElapsedMillis()
                + " stages=" + report.getStages());
        return report;
    }
}
