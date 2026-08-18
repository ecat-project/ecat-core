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

package com.ecat.core.Observability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ecat.core.Task.engine.SchedulerEngine;
import com.ecat.core.Task.engine.SchedulerMetrics;
import com.ecat.core.Task.engine.BlockingTaskStatus;
import com.ecat.core.Task.engine.TaskCircuitBreaker;
import com.ecat.core.Task.engine.WorkerStatus;

/**
 * system_health 调度节：调度 v2 引擎的只读快照（14 号架构 §5.4-2）。
 *
 * <p>数据全部来自引擎现成埋点（SchedulerMetrics LongAdder / 熔断器状态 / 车道队列计数 /
 * worker 忙闲印记），读端点时一次性组装——热路径上没有任何为观测而生的格式化或字符串拼接。
 *
 * <p>速率类指标（executedPerSecond）由 {@link SystemHealthService} 读时差分注入，不在本类。
 */
final class SchedulerHealth {

    private SchedulerHealth() {
    }

    /** 引擎快照；engine 为 null（组装缺失）时如实返回 null，不编造空结构。 */
    static Map<String, Object> collect(SchedulerEngine engine) {
        if (engine == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();

        SchedulerMetrics m = engine.getMetrics();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("submitted", m.getSubmitted().sum());
        metrics.put("executed", m.getExecuted().sum());
        metrics.put("failed", m.getFailed().sum());
        metrics.put("rejected", m.getRejected().sum());
        metrics.put("hardTimeouts", m.getHardTimeouts().sum());
        metrics.put("breakerSkipped", m.getBreakerSkipped().sum());
        metrics.put("breakerRearmed", m.getBreakerRearmed().sum());
        metrics.put("slowTaskReports", m.getSlowTaskReports().sum());
        metrics.put("blockingTasks", m.getBlockingTasks().sum());
        metrics.put("periodicDiedOnException", m.getPeriodicDiedOnException().sum());
        out.put("metrics", metrics);

        // 当前钉死早段清单（103000 观测补盲）：运行超 1s 未完的任务（lane + 任务类 + 已跑 ms），
        // 读端点时组装——「worker 被单任务长时间占用」无需 jstack 即可直读
        List<Map<String, Object>> blockingTaskList = new ArrayList<>();
        for (BlockingTaskStatus b : engine.blockingTaskStatuses()) {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("lane", b.getLaneKey());
            bm.put("task", b.getTaskLabel());
            bm.put("elapsedMillis", b.getElapsedMillis());
            blockingTaskList.add(bm);
        }
        out.put("blockingTaskList", blockingTaskList);

        Map<String, Object> queues = new LinkedHashMap<>();
        queues.put("laneQueuedTotal", engine.queuedTotal());
        queues.put("wheelPending", engine.wheelPendingCount());
        out.put("queues", queues);
        out.put("laneCount", engine.laneCount());

        int busy = 0;
        List<Map<String, Object>> workers = new ArrayList<>();
        for (WorkerStatus w : engine.workerStatuses()) {
            if (w.getRunningLane() != null) {
                busy++;
            }
            Map<String, Object> wm = new LinkedHashMap<>();
            wm.put("thread", w.getThreadName());
            wm.put("runningLane", w.getRunningLane());
            workers.add(wm);
        }
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("count", engine.workerCount());
        pool.put("busy", busy);
        pool.put("idle", engine.workerCount() - busy);
        pool.put("list", workers);
        out.put("workers", pool);

        Map<String, Object> breakers = new LinkedHashMap<>();
        for (Map.Entry<String, TaskCircuitBreaker> e : engine.getBreakers().entrySet()) {
            breakers.put(e.getKey(), breakerEntry(e.getValue()));
        }
        out.put("breakers", breakers);

        // D2 设备通讯熔断表（键 = coordinate:deviceId，与车道表分离）：风暴设备在这里现形
        Map<String, Object> commBreakers = new LinkedHashMap<>();
        for (Map.Entry<String, TaskCircuitBreaker> e : engine.getCommBreakers().entrySet()) {
            commBreakers.put(e.getKey(), breakerEntry(e.getValue()));
        }
        out.put("commBreakers", commBreakers);
        return out;
    }

    /** 单个熔断器条目（车道表与通讯熔断表共用字段集；commFailures 区分两类诱因）。 */
    private static Map<String, Object> breakerEntry(TaskCircuitBreaker b) {
        Map<String, Object> bm = new LinkedHashMap<>();
        bm.put("state", b.getState().name());
        bm.put("consecutiveFailures", b.getConsecutiveFailures());
        bm.put("cooldownRemainingMillis", b.cooldownRemainingMillis());
        bm.put("openCount", b.getOpenCount());
        bm.put("skippedCount", b.getSkippedCount());
        bm.put("commFailures", b.getCommFailureCount());
        bm.put("lastFailureSummary", b.getLastFailureSummary());
        return bm;
    }
}
