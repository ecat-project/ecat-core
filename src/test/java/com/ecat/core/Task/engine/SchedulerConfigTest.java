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
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * 配置加载与校验：默认值、属性覆盖、非法值显式拒绝（静默钳制会掩盖部署失误）、槽数取 2 的幂。
 */
public class SchedulerConfigTest {

    @Test
    public void defaultsMatchThreeLayerDesignNumbers() {
        SchedulerConfig c = SchedulerConfig.fromSystemProperties();
        // workers 默认 6（IO 收敛 P2 上调：57 modbus 源事务并道后实测峰值 3，留双倍余量）
        assertThat(c.getWorkers(), is(6));
        assertThat(c.getTickMillis(), is(100L));
        assertThat(c.getWheelSlots(), is(512));
        assertThat(c.getSlowThresholdMillis(), is(10_000L));
        assertThat(c.getHardTimeoutMillis(), is(30_000L));
        assertThat(c.getBreakerFailThreshold(), is(5));
        assertThat(c.getBreakerCooldownMillis(), is(300_000L));
    }

    @Test
    public void systemPropertyOverrides() {
        System.setProperty("ecat.scheduler.workers", "3");
        System.setProperty("ecat.scheduler.tick-millis", "50");
        try {
            SchedulerConfig c = SchedulerConfig.fromSystemProperties();
            assertThat(c.getWorkers(), is(3));
            assertThat(c.getTickMillis(), is(50L));
        } finally {
            System.clearProperty("ecat.scheduler.workers");
            System.clearProperty("ecat.scheduler.tick-millis");
        }
    }

    @Test
    public void invalidValueFailsLoudly() {
        System.setProperty("ecat.scheduler.workers", "0");
        try {
            SchedulerConfig.fromSystemProperties();
            fail("workers=0 必须显式拒绝");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage().contains("workers"), is(true));
        } finally {
            System.clearProperty("ecat.scheduler.workers");
        }
    }

    @Test
    public void nonNumericValueFailsLoudly() {
        System.setProperty("ecat.scheduler.breaker.cooldown-millis", "abc");
        try {
            SchedulerConfig.fromSystemProperties();
            fail("非数值必须显式拒绝");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage().contains("abc"), is(true));
        } finally {
            System.clearProperty("ecat.scheduler.breaker.cooldown-millis");
        }
    }

    @Test
    public void wheelSlotsRoundsUpToPowerOfTwo() {
        SchedulerConfig c = new SchedulerConfig(2, 100L, 300, 10_000L, 30_000L, 1_000L, 16, 5, 300_000L);
        assertThat("300 槽取整到 512（位掩码取模要求）", c.getWheelSlots(), is(512));
    }
}
