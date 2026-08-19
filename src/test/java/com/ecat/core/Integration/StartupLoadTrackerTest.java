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

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * StartupLoadTracker 渲染契约单测。
 *
 * <p>报告行是运维 grep 的接口，格式契约必须锁死：
 * {@code startup-report: total=Nms integrations=N entries=N failed=N [failures=[..]] top5=[coord=Nms, ..]}
 *
 * @author coffee
 */
public class StartupLoadTrackerTest {

    private static long ms(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    @Test
    public void render_containsCountsAndTop5SortedByTotalDesc() {
        StartupLoadTracker tracker = new StartupLoadTracker();
        tracker.recordLoadNanos("com.ecat:fast", ms(10));
        tracker.recordLoadNanos("com.ecat:slow", ms(2000));
        tracker.recordLoadNanos("com.ecat:mid", ms(200));
        tracker.recordEntryRestoreNanos("com.ecat:slow", ms(3000));
        tracker.recordEntryRestoreNanos("com.ecat:mid", ms(100));
        tracker.recordEntryRestored();
        tracker.recordEntryRestored();
        tracker.recordEntryRestored();

        String line = tracker.render(ms(6000));

        assertTrue(line.startsWith("startup-report: "));
        assertTrue(line, line.contains("total=6000ms"));
        assertTrue(line, line.contains("integrations=3"));
        assertTrue(line, line.contains("entries=3"));
        assertTrue(line, line.contains("failed=0"));
        assertFalse("无失败时不应有 failures 段", line.contains("failures="));
        // top5 按生命周期+entry 恢复总耗时降序：slow=5000 > mid=300 > fast=10
        assertTrue(line, line.contains("top5=[com.ecat:slow=5000ms, com.ecat:mid=300ms, com.ecat:fast=10ms]"));
    }

    @Test
    public void render_listsFailureStagesWhenPresent() {
        StartupLoadTracker tracker = new StartupLoadTracker();
        tracker.recordLoadNanos("com.ecat:good", ms(5));
        tracker.recordFailure("com.ecat:bad", "load");
        tracker.recordFailure("com.ecat:bad2", "entry:e-42");

        String line = tracker.render(ms(100));

        assertTrue(line, line.contains("failed=2"));
        assertTrue(line, line.contains("failures=[com.ecat:bad:load, com.ecat:bad2:entry:e-42]"));
    }

    @Test
    public void render_capsTop5AtFiveIntegrations() {
        StartupLoadTracker tracker = new StartupLoadTracker();
        for (int i = 0; i < 6; i++) {
            tracker.recordLoadNanos("com.ecat:i" + i, ms(100 * (6 - i)));
        }

        String line = tracker.render(ms(1000));

        // 最慢的 5 个上榜（i0=600ms..i4=200ms），最快的第 6 名 i5=100ms 不上榜
        assertTrue(line, line.contains("com.ecat:i0=600ms"));
        assertTrue(line, line.contains("com.ecat:i4=200ms"));
        assertFalse("top5 最多 5 项", line.contains("com.ecat:i5"));
    }

    /** 看门狗超时独立成节（与 failures 分列点名挂死元凶）；无超时不输出该节。 */
    @Test
    public void render_listsTimeoutsInOwnSectionWhenPresent() {
        StartupLoadTracker tracker = new StartupLoadTracker();
        tracker.recordTimeout("com.ecat:hung", "onStart");
        tracker.recordTimeout("com.ecat:hung", "entry-restore");
        tracker.recordFailure("com.ecat:other", "load");

        String line = tracker.render(ms(1000));
        assertTrue(line, line.contains("timeouts=2"));
        assertTrue(line, line.contains("timeoutList=[com.ecat:hung:onStart, com.ecat:hung:entry-restore]"));
        assertTrue("failures 节保持独立", line.contains("failed=1"));

        StartupLoadTracker clean = new StartupLoadTracker();
        assertFalse("无超时不输出 timeouts 节", clean.render(ms(100)).contains("timeouts="));
    }

    /** boot id 嵌入（25 号杠杆④）：BootTraceContext 有 boot id 时报告行带 boot=<ULID>。 */
    @Test
    public void render_embedsBootIdWhenPresent() {
        com.ecat.core.Observability.BootTraceContext.beginBoot();
        try {
            StartupLoadTracker tracker = new StartupLoadTracker();
            tracker.recordLoadNanos("com.ecat:only", ms(5));
            String line = tracker.render(ms(100));
            assertTrue(line, line.contains(" boot=" + com.ecat.core.Observability.BootTraceContext.getBootId()));
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }

    /**
     * 结构化快照（SRP 瘦身后）：只导出不可再算的事实——boot 标识/起点/总耗时/全量分项耗时
     * （生命周期+entry 恢复两段合计，按总耗时降序）；计数与失败名单不进快照（日志行与 registry 现算）。
     */
    @Test
    public void toSnapshot_exportsPerIntegrationMsSortedDesc() {
        StartupLoadTracker tracker = new StartupLoadTracker();
        tracker.recordLoadNanos("com.ecat:slow", ms(2000));
        tracker.recordEntryRestoreNanos("com.ecat:slow", ms(3000));
        tracker.recordLoadNanos("com.ecat:fast", ms(100));
        tracker.recordEntryRestored();
        tracker.recordFailure("com.ecat:bad", "load");
        tracker.recordTimeout("com.ecat:hung", "onStart");

        com.ecat.core.Observability.StartupReportHolder.Snapshot snap = tracker.toSnapshot(ms(6000));
        assertEquals("totalMs=整个启动阶段耗时（入参），非 top1 集成耗时", 6000, snap.getTotalMs());
        assertEquals("分项耗时=两段合计", Long.valueOf(5000),
                snap.getPerIntegrationMs().get("com.ecat:slow"));
        assertEquals(Long.valueOf(100), snap.getPerIntegrationMs().get("com.ecat:fast"));
        assertEquals("按总耗时降序（端点输出顺序稳定）",
                java.util.Arrays.asList("com.ecat:slow", "com.ecat:fast"),
                new java.util.ArrayList<>(snap.getPerIntegrationMs().keySet()));
        long startedAt = snap.getStartedAtMillis();
        assertTrue("起点=快照生成时刻回推总耗时（加载阶段起点推导值）",
                startedAt > 0 && startedAt <= System.currentTimeMillis());
    }
}