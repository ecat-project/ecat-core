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

package com.ecat.core.Integration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.ecat.core.Observability.BootTraceContext;
import com.ecat.core.Observability.StartupReportHolder;

/**
 * 启动加载分集成耗时追踪器 —— 启动健康报告（startup-report）的数据源。
 *
 * <p>动机（arch-review 02 号 P0-1）：vision-analysis 曾在 createEntry 里同步等待外部服务
 * 125.75s（占总启动 83.5%），旧日志完全不可见——两行「entries loaded」之间 2 分钟静默，
 * 只能人工滚动 50MB 滚动日志拼时间线。本追踪器沿加载路径埋点（每集成生命周期耗时 + entry
 * 恢复耗时 + 各阶段失败），在 ALL_LOADED 发布点汇成一行 INFO 报告，{@code startup-report:}
 * 前缀可直接 grep。
 *
 * <p>线程模型：集成加载在 integration-manager-0 线程、entry 恢复在 main 线程，两侧并发写入，
 * 全部用并发容器；仅在启动期存活（loadIntegrations 局部创建，报告输出后即弃）。
 *
 * @author coffee
 */
class StartupLoadTracker {

    /** 每坐标耗时槽位：[0]=生命周期加载（onLoad→onStart），[1]=entry 恢复（mergeEntries+createEntry 循环+onAllExistEntriesLoaded） */
    private static final int SLOT_LIFECYCLE = 0;
    private static final int SLOT_ENTRY_RESTORE = 1;

    private final Map<String, long[]> perIntegrationNanos = new ConcurrentHashMap<>();
    private final AtomicInteger entriesRestored = new AtomicInteger();
    private final List<String> failures = new CopyOnWriteArrayList<>();
    /** 看门狗超时清单（GuardedExecutor 执法）：与 failures 分列，报告里独立成节便于点名慢元凶 */
    private final List<String> timeouts = new CopyOnWriteArrayList<>();

    /** 记录某集成的生命周期加载耗时（成功路径） */
    void recordLoadNanos(String coordinate, long nanos) {
        addNanos(coordinate, SLOT_LIFECYCLE, nanos);
    }

    /** 记录某集成的 entry 恢复阶段耗时（坐标级整段，含 onAllExistEntriesLoaded） */
    void recordEntryRestoreNanos(String coordinate, long nanos) {
        addNanos(coordinate, SLOT_ENTRY_RESTORE, nanos);
    }

    /** 计数一个成功恢复的 entry（createEntry 正常返回） */
    void recordEntryRestored() {
        entriesRestored.incrementAndGet();
    }

    /** 记录一次启动期失败（stage：load / entry:{entryId} / onAllExistEntriesLoaded） */
    void recordFailure(String coordinate, String stage) {
        failures.add(coordinate + ":" + stage);
    }

    /**
     * 记录一次看门狗超时（GuardedExecutor 执法，stage：onStart / entry-restore）。
     * 与 {@link #recordFailure} 分开记账：超时是「被硬超时跳过」不是普通失败，
     * 报告中独立成 timeouts 节，直接点名挂死元凶。
     */
    void recordTimeout(String coordinate, String stage) {
        timeouts.add(coordinate + ":" + stage);
    }

    /**
     * 渲染一行报告。totalNanos 为调用方传入的整个启动加载阶段耗时（loadIntegrations 进入
     * 到 ALL_LOADED 发布点），报告即最终账——A4-2 后该点已等真完成。
     *
     * <p>boot 代际（arch-review 25 号杠杆④）：BootTraceContext 已有 boot id 时嵌入
     * {@code boot=<ULID>}，多代启动共用一个日志文件时按 id 分段。
     */
    String render(long totalNanos) {
        List<Map.Entry<String, long[]>> sorted = sortedByTotalDesc();

        StringBuilder sb = new StringBuilder("startup-report: ");
        sb.append("total=").append(TimeUnit.NANOSECONDS.toMillis(totalNanos)).append("ms");
        String bootId = BootTraceContext.getBootId();
        if (bootId != null) {
            sb.append(" boot=").append(bootId);
        }
        sb.append(" integrations=").append(perIntegrationNanos.size());
        sb.append(" entries=").append(entriesRestored.get());
        sb.append(" failed=").append(failures.size());
        if (!failures.isEmpty()) {
            sb.append(" failures=").append(failures);
        }
        if (!timeouts.isEmpty()) {
            sb.append(" timeouts=").append(timeouts.size()).append(" timeoutList=").append(timeouts);
        }
        sb.append(" top5=[");
        for (int i = 0; i < sorted.size() && i < 5; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            long[] nanos = sorted.get(i).getValue();
            sb.append(sorted.get(i).getKey()).append('=')
                    .append(TimeUnit.NANOSECONDS.toMillis(nanos[SLOT_LIFECYCLE] + nanos[SLOT_ENTRY_RESTORE]))
                    .append("ms");
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * 结构化快照（25 号 A 项）：与 {@link #render} 同源同账，存入 StartupReportHolder
     * 供 core-api 的 boot / integration-load-times 端点读取——日志环会被流量冲掉，
     * 端点是事后查看启动耗时的唯一出口。按 SRP 只导出不可再算的事实（boot 标识/总耗时/
     * 全量分项耗时），计数与失败名单留给 render 的日志行与 registry 现算。
     */
    StartupReportHolder.Snapshot toSnapshot(long totalNanos) {
        long totalMs = TimeUnit.NANOSECONDS.toMillis(totalNanos);
        Map<String, Long> perIntegrationMs = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : sortedByTotalDesc()) {
            long[] nanos = e.getValue();
            perIntegrationMs.put(e.getKey(),
                    TimeUnit.NANOSECONDS.toMillis(nanos[SLOT_LIFECYCLE] + nanos[SLOT_ENTRY_RESTORE]));
        }
        long generatedAtMillis = System.currentTimeMillis();
        return new StartupReportHolder.Snapshot(
                BootTraceContext.getBootId(),
                generatedAtMillis - totalMs, // 加载阶段起点：快照生成时刻回推总耗时
                totalMs,
                perIntegrationMs);
    }

    private List<Map.Entry<String, long[]>> sortedByTotalDesc() {
        List<Map.Entry<String, long[]>> sorted = new ArrayList<>(perIntegrationNanos.entrySet());
        sorted.sort(Comparator.comparingLong(
                (Map.Entry<String, long[]> e) -> e.getValue()[SLOT_LIFECYCLE] + e.getValue()[SLOT_ENTRY_RESTORE])
                .reversed());
        return sorted;
    }

    private void addNanos(String coordinate, int slot, long nanos) {
        perIntegrationNanos.computeIfAbsent(coordinate, k -> new long[2])[slot] += nanos;
    }
}
