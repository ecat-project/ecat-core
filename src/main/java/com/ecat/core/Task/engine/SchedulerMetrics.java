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

import java.util.concurrent.atomic.LongAdder;

import lombok.Getter;

/**
 * 调度引擎内存计数器。
 *
 * <p>设计依据：可观测性必须是便宜的——全 LongAdder 计数，零热路径锁、零 per-event 日志、
 * 零字符串格式化；B 阶段 system_health 端点（B2 自观测）直接读这些 getter 快照导出。
 *
 * @author coffee
 */
public class SchedulerMetrics {

    /** 累计提交（schedule/execute/submit 全入口）。 */
    @Getter private final LongAdder submitted = new LongAdder();
    /** 累计完成执行（含失败）。 */
    @Getter private final LongAdder executed = new LongAdder();
    /** 执行失败（任务体抛异常或被硬超时判定失败）。 */
    @Getter private final LongAdder failed = new LongAdder();
    /** 车道排队总量达上限被丢弃的任务（可观测拒绝，不静默）。 */
    @Getter private final LongAdder rejected = new LongAdder();
    /** 熔断 OPEN/PROBING 期间被跳过的周期执行次数。 */
    @Getter private final LongAdder breakerSkipped = new LongAdder();
    /** 熔断跳过后按周期语义重新挂轮回表的次数。 */
    @Getter private final LongAdder breakerRearmed = new LongAdder();
    /** 硬超时中断次数。 */
    @Getter private final LongAdder hardTimeouts = new LongAdder();
    /** 看门狗慢任务点名次数。 */
    @Getter private final LongAdder slowTaskReports = new LongAdder();
    /**
     * 任务执行运行超 blocking 阈值（1s，slow 阈值前的早段）的次数——「worker 被单任务长时间
     * 占用」的常设指标（103000 观测补盲：归因时靠 jstack 偶然抓到 6/6 worker 钉死，事后无法
     * 复盘；本计数 + blockingTaskList 把钉死早段变成 system_health 可直读信号）。
     * 采样口径：watchdog 扫描（默认 1s 周期）所见，恰在扫描间隙完成的短任务不计。
     */
    @Getter private final LongAdder blockingTasks = new LongAdder();
    /** 周期任务因异常永久停止调度次数（对齐 STPE「抛异常即停」语义的死亡点名）。 */
    @Getter private final LongAdder periodicDiedOnException = new LongAdder();
}
