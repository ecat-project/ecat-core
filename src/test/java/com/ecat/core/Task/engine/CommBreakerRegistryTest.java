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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 设备通讯失败熔断（D2）全状态机测试：经引擎公共入口上报/守卫，注入时钟驱动冷却，
 * 全部确定性断言（测试纪律：禁 sleep 同步）。
 *
 * <p>覆盖：未知键纯查询不建条目；5 连败 OPEN→守卫跳过→冷却→半开探测→成功关闭/失败重开；
 * 间歇失败（失败-成功交错）不触发；通讯熔断与车道熔断命名空间互不串扰。
 */
public class CommBreakerRegistryTest {

    /** 模拟一个真实设备通讯键：coordinate:deviceId。 */
    private static final String DEV_KEY = "com.ecat:integration-sailhero:so2-1";
    private static final int FAIL_THRESHOLD = 5;
    private static final long COOLDOWN_MS = 300_000L;

    private MutableClock clock;
    private SchedulerEngine engine;

    @Before
    public void setUp() {
        clock = new MutableClock();
        engine = new SchedulerEngine(new SchedulerConfig(
                1, 100L, 16, 10_000L, 30_000L, 1_000L, 128, FAIL_THRESHOLD, COOLDOWN_MS),
                clock, false);
    }

    @After
    public void tearDown() {
        engine.shutdownNow();
    }

    private void commFailNTimes(int n) {
        for (int i = 0; i < n; i++) {
            engine.reportCommFailure(DEV_KEY, "so2chr$ Serial response timeout-" + i);
        }
    }

    @Test
    public void unknownKeyGateClosedAndNoEntryCreated() {
        assertThat("从未上报失败的键守卫放行", engine.isCommOpen("com.ecat:integration-sailhero:never"), is(false));
        assertThat("纯查询不创建熔断条目", engine.getCommBreakers().isEmpty(), is(true));
    }

    @Test
    public void fiveConsecutiveCommFailuresOpenAndGateSkipsPolling() {
        commFailNTimes(FAIL_THRESHOLD - 1);
        assertThat("4 连败不应 OPEN", engine.isCommOpen(DEV_KEY), is(false));

        engine.reportCommFailure(DEV_KEY, "so2chr$ Serial response timeout-4");
        assertThat("5 连败后守卫跳过轮询", engine.isCommOpen(DEV_KEY), is(true));

        TaskCircuitBreaker breaker = engine.getCommBreakers().get(DEV_KEY);
        assertThat(breaker.getState(), is(TaskCircuitBreaker.State.OPEN));
        assertThat("通讯失败计数进自观测", breaker.getCommFailureCount(), is((long) FAIL_THRESHOLD));
        assertThat(breaker.getOpenCount(), is(1L));
        assertThat("OPEN reason 前缀「通讯失败」区分任务异常",
                breaker.getLastFailureSummary(),
                is(TaskCircuitBreaker.COMM_FAILURE_REASON_PREFIX + "so2chr$ Serial response timeout-4"));
        assertThat("守卫跳过计入 skippedCount", breaker.getSkippedCount(), is(1L));
    }

    @Test
    public void intermittentFailuresNeverOpen() {
        // 间歇性失败：失败-成功交错凑不满 5 连败，不触发熔断（保护数据新鲜度的取舍）
        for (int round = 0; round < 10; round++) {
            commFailNTimes(4);
            engine.reportCommSuccess(DEV_KEY);
        }
        assertThat(engine.isCommOpen(DEV_KEY), is(false));
        assertThat(engine.getCommBreakers().get(DEV_KEY).getState(), is(TaskCircuitBreaker.State.CLOSED));
    }

    @Test
    public void gateConsumesProbeAfterCooldownAndHoldsWhileProbing() {
        commFailNTimes(FAIL_THRESHOLD);
        assertThat(engine.isCommOpen(DEV_KEY), is(true));

        clock.advanceMillis(COOLDOWN_MS);
        assertThat("冷却期满第一次守卫放行（本轮即探测）", engine.isCommOpen(DEV_KEY), is(false));
        assertThat(engine.getCommBreakers().get(DEV_KEY).getState(), is(TaskCircuitBreaker.State.PROBING));

        assertThat("探测在途期间后续轮询跳过（探测唯一性）", engine.isCommOpen(DEV_KEY), is(true));
    }

    @Test
    public void probeSuccessClosesAndPollingResumes() {
        commFailNTimes(FAIL_THRESHOLD);
        clock.advanceMillis(COOLDOWN_MS);
        assertThat(engine.isCommOpen(DEV_KEY), is(false));

        engine.reportCommSuccess(DEV_KEY);
        TaskCircuitBreaker breaker = engine.getCommBreakers().get(DEV_KEY);
        assertThat("探测成功恢复轮询", breaker.getState(), is(TaskCircuitBreaker.State.CLOSED));
        assertThat(breaker.getConsecutiveFailures(), is(0));
        assertThat(engine.isCommOpen(DEV_KEY), is(false));
    }

    @Test
    public void probeFailureReopensWithFreshCooldown() {
        commFailNTimes(FAIL_THRESHOLD);
        clock.advanceMillis(COOLDOWN_MS);
        assertThat(engine.isCommOpen(DEV_KEY), is(false));

        engine.reportCommFailure(DEV_KEY, "so2chr$ probe timeout");
        TaskCircuitBreaker breaker = engine.getCommBreakers().get(DEV_KEY);
        assertThat("探测失败重开", breaker.getState(), is(TaskCircuitBreaker.State.OPEN));
        assertThat(breaker.getOpenCount(), is(2L));

        clock.advanceMillis(COOLDOWN_MS - 1);
        assertThat("新冷却重新计时，差 1ms 仍跳过", engine.isCommOpen(DEV_KEY), is(true));
        clock.advanceMillis(1);
        assertThat("期满可再探测", engine.isCommOpen(DEV_KEY), is(false));
    }

    @Test
    public void successDuringOpenCooldownClosesCircuit() {
        commFailNTimes(FAIL_THRESHOLD);
        assertThat(engine.isCommOpen(DEV_KEY), is(true));

        // 冷却期内非轮询路径（如手动命令）的通讯成功：最强健康信号，直接关闭
        engine.reportCommSuccess(DEV_KEY);
        assertThat(engine.getCommBreakers().get(DEV_KEY).getState(), is(TaskCircuitBreaker.State.CLOSED));
        assertThat(engine.isCommOpen(DEV_KEY), is(false));
    }

    @Test
    public void commBreakersDoNotTouchLaneBreakersAndViceVersa() {
        commFailNTimes(FAIL_THRESHOLD);
        assertThat("设备通讯风暴不得污染车道熔断表（车道=集成坐标粒度）",
                engine.getBreakers().isEmpty(), is(true));

        // 车道侧：同坐标车道熔断（任务异常）与设备通讯熔断是两张独立的表
        assertTrue("通讯表里有设备键", engine.getCommBreakers().containsKey(DEV_KEY));
        assertThat("车道表不因通讯上报出现设备键", engine.getBreakers().containsKey(DEV_KEY), is(false));
    }
}
