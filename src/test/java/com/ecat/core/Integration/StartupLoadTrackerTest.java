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
}
