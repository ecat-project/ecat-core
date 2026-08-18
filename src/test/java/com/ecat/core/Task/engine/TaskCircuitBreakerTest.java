/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.Task.engine;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

/**
 * 熔断状态机单测：注入时钟驱动冷却，全部确定性断言。
 *
 * <p>覆盖：5 连败→OPEN→冷却跳过→半开探测→成功关闭/失败重开；成功清零计数；
 * OPEN 期一次性任务放行；探测被取消的名额归还。
 */
public class TaskCircuitBreakerTest {

    private static final int FAIL_THRESHOLD = 5;
    private static final long COOLDOWN_MS = 300_000L;

    private MutableClock clock;
    private TaskCircuitBreaker breaker;

    @Before
    public void setUp() {
        clock = new MutableClock();
        breaker = new TaskCircuitBreaker("lane-test", FAIL_THRESHOLD, COOLDOWN_MS, clock);
    }

    private void failNTimes(int n) {
        for (int i = 0; i < n; i++) {
            breaker.onOutcome(true, "RuntimeException: boom-" + i);
        }
    }

    @Test
    public void closedStateAdmitsEverything() {
        assertThat(breaker.admit(true), is(TaskCircuitBreaker.Decision.ADMIT));
        assertThat(breaker.admit(false), is(TaskCircuitBreaker.Decision.ADMIT));
        assertThat(breaker.getState(), is(TaskCircuitBreaker.State.CLOSED));
    }

    @Test
    public void fiveConsecutiveFailuresOpenCircuit() {
        failNTimes(4);
        assertThat("4 次失败不应 OPEN", breaker.getState(), is(TaskCircuitBreaker.State.CLOSED));

        breaker.onOutcome(true, "RuntimeException: boom-4");
        assertThat("5 连败应 OPEN", breaker.getState(), is(TaskCircuitBreaker.State.OPEN));
        assertThat(breaker.getOpenCount(), is(1L));
        assertThat("连败计数在 OPEN 时归零（重开按新一轮计）", breaker.getConsecutiveFailures(), is(0));
        assertThat("最后错误摘要进状态", breaker.getLastFailureSummary(), is("RuntimeException: boom-4"));
    }

    @Test
    public void successResetsConsecutiveFailureCount() {
        failNTimes(4);
        breaker.onOutcome(false, null);
        failNTimes(4);
        assertThat("中途成功清零计数，4+4 不应 OPEN", breaker.getState(), is(TaskCircuitBreaker.State.CLOSED));
    }

    @Test
    public void openSkipsPeriodicButAdmitsOneShotDuringCooldown() {
        failNTimes(FAIL_THRESHOLD);

        assertThat("冷却期内周期任务跳过", breaker.admit(true), is(TaskCircuitBreaker.Decision.SKIP_PERIODIC));
        assertThat(breaker.getSkippedCount(), is(1L));
        assertThat("冷却期内一次性任务放行（恢复动作必须可执行）",
                breaker.admit(false), is(TaskCircuitBreaker.Decision.ADMIT));
        assertThat(breaker.getState(), is(TaskCircuitBreaker.State.OPEN));
    }

    @Test
    public void afterCooldownFirstTaskBecomesProbe() {
        failNTimes(FAIL_THRESHOLD);
        clock.advanceMillis(COOLDOWN_MS);

        assertThat("冷却期满第一个任务作为探测放行",
                breaker.admit(true), is(TaskCircuitBreaker.Decision.ADMIT_AS_PROBE));
        assertThat(breaker.getState(), is(TaskCircuitBreaker.State.PROBING));

        assertThat("探测未决期间后续周期任务跳过",
                breaker.admit(true), is(TaskCircuitBreaker.Decision.SKIP_PERIODIC));
    }

    @Test
    public void probeSuccessClosesCircuit() {
        failNTimes(FAIL_THRESHOLD);
        clock.advanceMillis(COOLDOWN_MS);
        breaker.admit(true);

        breaker.onOutcome(false, null);
        assertThat("探测成功应 CLOSED", breaker.getState(), is(TaskCircuitBreaker.State.CLOSED));
        assertThat(breaker.getConsecutiveFailures(), is(0));
    }

    @Test
    public void probeFailureReopensWithFreshCooldown() {
        failNTimes(FAIL_THRESHOLD);
        clock.advanceMillis(COOLDOWN_MS);
        breaker.admit(true);

        breaker.onOutcome(true, "RuntimeException: probe-fail");
        assertThat("探测失败应重开", breaker.getState(), is(TaskCircuitBreaker.State.OPEN));
        assertThat(breaker.getOpenCount(), is(2L));

        // 新冷却重新计时：刚过 1ms 仍在冷却内
        clock.advanceMillis(1);
        assertThat(breaker.admit(true), is(TaskCircuitBreaker.Decision.SKIP_PERIODIC));

        // 再次期满可探测
        clock.advanceMillis(COOLDOWN_MS);
        assertThat(breaker.admit(true), is(TaskCircuitBreaker.Decision.ADMIT_AS_PROBE));
    }

    @Test
    public void oneShotSuccessDuringOpenClosesCircuit() {
        failNTimes(FAIL_THRESHOLD);
        assertThat(breaker.admit(false), is(TaskCircuitBreaker.Decision.ADMIT));

        breaker.onOutcome(false, null);
        assertThat("熔断期内一次性任务成功是最强健康信号，直接关闭",
                breaker.getState(), is(TaskCircuitBreaker.State.CLOSED));
    }

    @Test
    public void oneShotFailureDuringOpenExtendsCooldown() {
        failNTimes(FAIL_THRESHOLD);
        assertThat(breaker.admit(false), is(TaskCircuitBreaker.Decision.ADMIT));

        clock.advanceMillis(COOLDOWN_MS);
        breaker.onOutcome(true, "IOException: still-dead");
        assertThat("冷却期内失败应续期冷却而非放探测", breaker.getState(), is(TaskCircuitBreaker.State.OPEN));

        clock.advanceMillis(COOLDOWN_MS - 1);
        assertThat(breaker.admit(true), is(TaskCircuitBreaker.Decision.SKIP_PERIODIC));
        clock.advanceMillis(1);
        assertThat(breaker.admit(true), is(TaskCircuitBreaker.Decision.ADMIT_AS_PROBE));
    }

    @Test
    public void probeAbortedReturnsSlotWithoutCooldownPenalty() {
        failNTimes(FAIL_THRESHOLD);
        clock.advanceMillis(COOLDOWN_MS);
        assertThat(breaker.admit(true), is(TaskCircuitBreaker.Decision.ADMIT_AS_PROBE));

        // 探测任务被取消（设备停止）：名额作废，冷却即刻到期，下个任务直接再探测
        breaker.probeAborted();
        assertThat(breaker.getState(), is(TaskCircuitBreaker.State.OPEN));
        assertThat(breaker.admit(true), is(TaskCircuitBreaker.Decision.ADMIT_AS_PROBE));
    }

    @Test
    public void probeAbortedIsNoOpWhenNotProbing() {
        breaker.probeAborted();
        assertThat(breaker.getState(), is(TaskCircuitBreaker.State.CLOSED));
    }

    @Test
    public void commAndTaskFailuresCountSameWeightInterleaved() {
        breaker.onOutcome(true, "RuntimeException: task-1");
        breaker.onOutcome(true, "RuntimeException: task-2");
        breaker.onCommFailure("so2chr$ timeout-1");
        breaker.onCommFailure("so2chr$ timeout-2");
        assertThat("任务异常与通讯失败同权混排计数", breaker.getConsecutiveFailures(), is(4));

        breaker.onCommFailure("so2chr$ timeout-3");
        assertThat("混排 5 连败同样 OPEN", breaker.getState(), is(TaskCircuitBreaker.State.OPEN));
        assertThat(breaker.getCommFailureCount(), is(3L));
        assertThat("OPEN reason 前缀「通讯失败」区分任务异常诱因",
                breaker.getLastFailureSummary(),
                is(TaskCircuitBreaker.COMM_FAILURE_REASON_PREFIX + "so2chr$ timeout-3"));
    }

    @Test
    public void commSuccessResetsStreakFromBothSources() {
        failNTimes(4);
        breaker.onCommSuccess();
        assertThat("通讯成功清零任务异常连败", breaker.getConsecutiveFailures(), is(0));

        breaker.onCommFailure("so2chr$ timeout");
        breaker.onCommFailure("so2chr$ timeout");
        breaker.onCommFailure("so2chr$ timeout");
        breaker.onCommFailure("so2chr$ timeout");
        assertThat("4 次通讯失败未达阈值", breaker.getState(), is(TaskCircuitBreaker.State.CLOSED));

        breaker.onOutcome(false, null);
        assertThat("任务成功同样清零通讯连败", breaker.getConsecutiveFailures(), is(0));

        breaker.onCommFailure("so2chr$ timeout");
        assertThat("清零后重新计数，1 次不 OPEN", breaker.getState(), is(TaskCircuitBreaker.State.CLOSED));
        assertThat(breaker.getCommFailureCount(), is(5L));
    }
}
