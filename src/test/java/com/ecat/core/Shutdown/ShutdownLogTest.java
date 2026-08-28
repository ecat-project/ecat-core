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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ecat.core.Utils.SchedulerClock;

/**
 * 停机期 stdout 通道测试（C2 修复：停机日志可见性）：伪造 System.out（{@link ShutdownLog#setOut}
 * seam 注入内存 PrintStream）断言 stage 行与最终汇总行都出现在 stdout 通道——app 通道
 * （AsyncAppender）在 JVM 退出期不可靠，stdout 是停机期唯一有保底的出口；且 stdout 失效
 * （写抛 RuntimeException）绝不终止停机序列。
 *
 * <p>虚拟时钟驱动（零真实等待，测试纪律：禁 sleep 同步）：TIMEOUT/FAILED/SKIPPED 全部
 * 由 MutableClock 手动推进与桩阶段确定性构造，耗时断言精确到毫秒。
 */
public class ShutdownLogTest {

    private final MutableClock clock = new MutableClock();
    private final List<String> journal = new ArrayList<>();
    private ByteArrayOutputStream stdoutBytes;
    private PrintStream stdout;

    @Before
    public void setUp() {
        stdoutBytes = new ByteArrayOutputStream();
        stdout = new PrintStream(stdoutBytes, true);
        ShutdownLog.setOut(stdout);
    }

    @After
    public void tearDown() {
        ShutdownLog.setOut(System.out);
    }

    /** 记录型桩阶段（同 ShutdownOrchestratorTest 口径）：进 journal、可选推进虚拟时钟、
     *  可选返回 false（TIMEOUT）/ 抛异常（FAILED）。 */
    private static final class StubStage implements ShutdownStage {
        final String name;
        final Boolean result;      // null = 抛异常
        final long advanceMillis;  // execute 内推进的虚拟时钟
        final List<String> journal;

        StubStage(String name, Boolean result, long advanceMillis, List<String> journal) {
            this.name = name;
            this.result = result;
            this.advanceMillis = advanceMillis;
            this.journal = journal;
        }

        @Override
        public String name() { return name; }

        @Override
        public long budgetMillis() { return 5_000L; }

        @Override
        public boolean execute(long deadlineNanos, SchedulerClock clock) {
            journal.add(name);
            if (advanceMillis > 0) {
                ((MutableClock) clock).advanceMillis(advanceMillis);
            }
            if (result == null) {
                throw new IllegalStateException(name + "-boom");
            }
            return result;
        }
    }

    private String capturedStdout() {
        stdout.flush();
        return stdoutBytes.toString();
    }

    /** 完整路径：每个 STAGE_DONE 行与最终 SUMMARY 行都落在 stdout 通道。 */
    @Test
    public void stageDoneLinesAndSummaryAppearOnStdoutChannel() {
        List<ShutdownStage> stages = new ArrayList<>();
        stages.add(new StubStage("a", true, 0, journal));
        stages.add(new StubStage("b", true, 0, journal));

        new ShutdownOrchestrator(clock, 10_000, stages).run();

        String out = capturedStdout();
        assertTrue("应含 STAGE_DONE(a): " + out, out.contains("SHUTDOWN_STAGE_DONE stage=a elapsedMs=0"));
        assertTrue("应含 STAGE_DONE(b): " + out, out.contains("SHUTDOWN_STAGE_DONE stage=b elapsedMs=0"));
        assertTrue("应含 COMPLETE 行: " + out, out.contains("SHUTDOWN_SEQUENCE_COMPLETE"));
        assertTrue("应含 SUMMARY 行: " + out, out.contains(
                "SHUTDOWN_SUMMARY totalElapsedMs=0 stages=[a:completed(0ms), b:completed(0ms)]"));
    }

    /** 不完整路径：TIMEOUT/FAILED/SKIPPED 行 + SUMMARY 单行覆盖全部结局与精确耗时（虚拟时钟确定值）。 */
    @Test
    public void summaryLineCoversAllOutcomesOnStdoutChannel() {
        List<ShutdownStage> stages = new ArrayList<>();
        stages.add(new StubStage("ok", true, 0, journal));       // COMPLETED 0ms
        stages.add(new StubStage("boom", null, 0, journal));     // FAILED 0ms（execute 抛异常）
        stages.add(new StubStage("stuck", false, 400, journal)); // TIMEOUT 400ms（返回 false）
        stages.add(new StubStage("hog", true, 600, journal));    // COMPLETED 600ms
        stages.add(new StubStage("tail", true, 0, journal));     // SKIPPED（总预算 1000ms 恰耗尽）

        new ShutdownOrchestrator(clock, 1_000, stages).run();

        String out = capturedStdout();
        assertTrue("应含 TIMEOUT 行: " + out,
                out.contains("SHUTDOWN_STAGE_TIMEOUT stage=stuck budgetMs=5000 elapsedMs=400"));
        assertTrue("应含 FAILED 行（末位 Throwable 以异常摘要收尾）: " + out,
                out.contains("SHUTDOWN_STAGE_FAILED stage=boom")
                        && out.contains(":: java.lang.IllegalStateException: boom-boom"));
        assertTrue("应含 SKIPPED 行: " + out, out.contains("SHUTDOWN_STAGE_SKIPPED stage=tail"));
        assertTrue("应含 INCOMPLETE 行: " + out, out.contains("SHUTDOWN_SEQUENCE_INCOMPLETE"));
        assertTrue("SUMMARY 应一行看全四种结局与耗时: " + out, out.contains(
                "SHUTDOWN_SUMMARY totalElapsedMs=1000 stages="
                        + "[ok:completed(0ms), boom:failed(0ms), stuck:timeout(400ms), "
                        + "hog:completed(600ms), tail:skipped(0ms)]"));
    }

    /** stdout 通道失效（写抛 RuntimeException，模拟被关闭/替换为坏流）：显式吞掉，停机序列照常完成。 */
    @Test
    public void brokenStdoutDoesNotAbortShutdownSequence() {
        PrintStream broken = new PrintStream(new ByteArrayOutputStream(), true) {
            @Override
            public void println(String x) {
                throw new RuntimeException("stdout channel broken");
            }
        };
        ShutdownLog.setOut(broken);
        try {
            List<ShutdownStage> stages = new ArrayList<>();
            stages.add(new StubStage("a", true, 0, journal));
            stages.add(new StubStage("b", true, 0, journal));

            ShutdownOrchestrator.ShutdownReport report =
                    new ShutdownOrchestrator(clock, 10_000, stages).run();

            assertThat("stdout 坏不应影响阶段执行", journal, is(Arrays.asList("a", "b")));
            assertTrue("stdout 坏不应影响报告产出: " + report.getStages(), report.allCompleted());
        } finally {
            ShutdownLog.setOut(stdout);
        }
    }

    /** CoreShutdown 侧接线：START 行与 SUMMARY 行都过 ShutdownLog 落到 stdout 通道。 */
    @Test
    public void coreShutdownEmitsStartAndSummaryOnStdout() {
        // init 未完成形态（部件 null）：仅剩三个生命周期阶段且目标列表为空——真实时钟下毫秒级、确定性完成
        ShutdownOrchestrator.ShutdownReport report = CoreShutdown.forRegistries(null, null, null).run();

        assertThat(report.allCompleted(), is(true));
        String out = capturedStdout();
        assertTrue("START 行应在 stdout: " + out, out.contains(
                "SHUTDOWN_SEQUENCE_START stages=[device-wind-down, bus-drain, release-integrations]"
                        + " totalBudgetMs=" + CoreShutdown.TOTAL_BUDGET_MS));
        assertTrue("SUMMARY 行应在 stdout: " + out, out.contains(
                "SHUTDOWN_SUMMARY totalElapsedMs=")
                && out.contains("device-wind-down:completed(")
                && out.contains("release-integrations:completed("));
    }
}
