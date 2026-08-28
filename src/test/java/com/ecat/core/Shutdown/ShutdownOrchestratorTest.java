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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ecat.core.Utils.SchedulerClock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 编排器机制层测试（虚拟时钟确定性驱动，零真实等待）：
 * ①全阶段顺序 ②阶段超时不硬等继续下一阶段 + WARN ③总预算有界（耗尽后 SKIPPED）+ 阶段预算
 * 与剩余总预算取小 ④阶段异常捕获不阻断后续。
 */
public class ShutdownOrchestratorTest {

    private MutableClock clock;
    private List<String> journal;
    private Logger orchestratorLogger;
    private ListAppender<ILoggingEvent> appender;

    @Before
    public void setUp() {
        clock = new MutableClock();
        journal = new ArrayList<>();
        orchestratorLogger = (Logger) org.slf4j.LoggerFactory.getLogger(ShutdownOrchestrator.class);
        appender = new ListAppender<>();
        appender.start();
        orchestratorLogger.addAppender(appender);
    }

    @After
    public void tearDown() {
        orchestratorLogger.detachAppender(appender);
    }

    /** 记录型桩阶段：进 journal、可选推进虚拟时钟、可选返回 false / 抛异常、记录收到的 deadline。 */
    private static final class StubStage implements ShutdownStage {
        final String name;
        final long budgetMillis;
        final Boolean result;          // null = 抛异常
        final long advanceMillis;      // execute 内推进的虚拟时钟
        final List<String> journal;
        final AtomicLong seenDeadline;

        StubStage(String name, long budgetMillis, Boolean result, long advanceMillis,
                List<String> journal, AtomicLong seenDeadline) {
            this.name = name;
            this.budgetMillis = budgetMillis;
            this.result = result;
            this.advanceMillis = advanceMillis;
            this.journal = journal;
            this.seenDeadline = seenDeadline;
        }

        @Override
        public String name() { return name; }

        @Override
        public long budgetMillis() { return budgetMillis; }

        @Override
        public boolean execute(long deadlineNanos, SchedulerClock clock) {
            journal.add(name);
            if (seenDeadline != null) {
                seenDeadline.set(deadlineNanos);
            }
            if (advanceMillis > 0) {
                ((MutableClock) clock).advanceMillis(advanceMillis);
            }
            if (result == null) {
                throw new IllegalStateException(name + "-boom");
            }
            return result;
        }
    }

    private static String warnMessages(ListAppender<ILoggingEvent> appender) {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            if ("WARN".equals(event.getLevel().toString())) {
                sb.append(event.getFormattedMessage()).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    public void allStagesRunInOrderAndReportCompleted() {
        List<ShutdownStage> stages = new ArrayList<>();
        stages.add(new StubStage("a", 1000, true, 0, journal, null));
        stages.add(new StubStage("b", 1000, true, 0, journal, null));
        stages.add(new StubStage("c", 1000, true, 0, journal, null));

        ShutdownOrchestrator.ShutdownReport report =
                new ShutdownOrchestrator(clock, 10_000, stages).run();

        assertThat(journal, is(Arrays.asList("a", "b", "c")));
        assertTrue(report.allCompleted());
        for (ShutdownOrchestrator.StageReport stage : report.getStages()) {
            assertThat(stage.getOutcome(), is(ShutdownOrchestrator.StageOutcome.COMPLETED));
        }
    }

    @Test
    public void stageTimeoutRecordsWarnAndDoesNotBlockLaterStages() {
        List<ShutdownStage> stages = new ArrayList<>();
        stages.add(new StubStage("a", 1000, true, 0, journal, null));
        stages.add(new StubStage("stuck", 1000, false, 500, journal, null)); // 超预算 500ms 返回 false
        stages.add(new StubStage("c", 1000, true, 0, journal, null));

        ShutdownOrchestrator.ShutdownReport report =
                new ShutdownOrchestrator(clock, 10_000, stages).run();

        // stuck 阶段未硬等：c 仍被执行
        assertThat(journal, is(Arrays.asList("a", "stuck", "c")));
        assertThat(report.find("stuck").getOutcome(), is(ShutdownOrchestrator.StageOutcome.TIMEOUT));
        assertThat(report.find("c").getOutcome(), is(ShutdownOrchestrator.StageOutcome.COMPLETED));
        assertThat(report.allCompleted(), is(false));

        String warns = warnMessages(appender);
        assertTrue("应记 SHUTDOWN_STAGE_TIMEOUT WARN: " + warns,
                warns.contains("SHUTDOWN_STAGE_TIMEOUT") && warns.contains("stuck"));
    }

    @Test
    public void totalBudgetExhaustedSkipsRemainingStagesWithWarn() {
        List<ShutdownStage> stages = new ArrayList<>();
        stages.add(new StubStage("hog", 1000, true, 5000, journal, null)); // 吃掉全部总预算（1s）
        stages.add(new StubStage("b", 1000, true, 0, journal, null));
        stages.add(new StubStage("c", 1000, true, 0, journal, null));

        ShutdownOrchestrator.ShutdownReport report =
                new ShutdownOrchestrator(clock, 1000, stages).run();

        assertThat(journal, is(Arrays.asList("hog")));
        assertThat(report.find("b").getOutcome(), is(ShutdownOrchestrator.StageOutcome.SKIPPED));
        assertThat(report.find("c").getOutcome(), is(ShutdownOrchestrator.StageOutcome.SKIPPED));

        String warns = warnMessages(appender);
        assertTrue("总预算耗尽应 WARN SHUTDOWN_STAGE_SKIPPED: " + warns,
                warns.contains("SHUTDOWN_STAGE_SKIPPED") && warns.contains("total-budget-exhausted"));
    }

    @Test
    public void stageBudgetClampedToRemainingTotalBudget() {
        AtomicLong seenDeadline = new AtomicLong();
        List<ShutdownStage> stages = new ArrayList<>();
        // 先耗掉 800ms（总预算 1000ms 剩 200ms），下一阶段预算 5000ms 应被钳到 200ms
        stages.add(new StubStage("warmup", 1000, true, 800, journal, null));
        stages.add(new StubStage("clamped", 5000, true, 0, journal, seenDeadline));

        new ShutdownOrchestrator(clock, 1000, stages).run();

        // warmup 推进 800ms 后 clamped 才开始，此刻即 clock 当前值（clamped 自身推进 0）
        long clampedStart = clock.currentMillis();
        assertThat("deadline 应为阶段开始 + min(5000, 剩余200) = +200ms",
                seenDeadline.get(), is((clampedStart + 200L) * 1_000_000L));
    }

    @Test
    public void stageExceptionRecordsFailedAndContinues() {
        List<ShutdownStage> stages = new ArrayList<>();
        stages.add(new StubStage("boom", 1000, null, 0, journal, null));   // result=null → 抛异常
        stages.add(new StubStage("b", 1000, true, 0, journal, null));

        ShutdownOrchestrator.ShutdownReport report =
                new ShutdownOrchestrator(clock, 10_000, stages).run();

        assertThat(journal, is(Arrays.asList("boom", "b")));
        assertThat(report.find("boom").getOutcome(), is(ShutdownOrchestrator.StageOutcome.FAILED));
        assertThat(report.find("b").getOutcome(), is(ShutdownOrchestrator.StageOutcome.COMPLETED));
    }
}
