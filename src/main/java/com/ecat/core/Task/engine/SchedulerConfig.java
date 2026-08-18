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

import lombok.Value;

/**
 * 调度 v2 引擎配置（不可变）。
 *
 * <p>来源：系统属性（前缀 {@code ecat.scheduler.}），全部有默认值——2 核 Edge 形态下无需任何
 * 配置即可工作。默认值依据 14 号架构文档 §5.1 三层模型：512 槽 × 100ms tick（万级周期任务
 * O(1) 入槽）、worker 池 6 线程（IO 收敛 P2 后按实测并发度上调，见 fromSystemProperties 处注释；
 * 原三层模型为 4）、慢任务 10s / 硬超时 30s、熔断 5 连败 → 300s 冷却（TB Gateway
 * 同款参数）。
 *
 * <p>非法值（如 workers ≤ 0）直接抛 {@link IllegalArgumentException}：配置错误必须当场暴露，
 * 不做静默钳制（严格模式，静默修正会掩盖部署失误）。
 *
 * @author coffee
 */
@Value
public class SchedulerConfig {

    /** 执行层 worker 池线程数（有界；每设备隔离靠逻辑车道不靠线程）。属性 ecat.scheduler.workers */
    int workers;

    /** 触发层表轮 tick 时长（毫秒）。任务到期精度为 [0, tick) 内抖动。属性 ecat.scheduler.tick-millis */
    long tickMillis;

    /** 表轮槽数（自动向上取整到 2 的幂，位掩码取模 O(1)）。属性 ecat.scheduler.wheel-slots */
    int wheelSlots;

    /** 慢任务看门狗阈值（毫秒）：超时打一行采样栈点名，不中断。属性 ecat.scheduler.slow-threshold-millis */
    long slowThresholdMillis;

    /** 硬超时阈值（毫秒）：中断执行线程并标记失败。属性 ecat.scheduler.hard-timeout-millis */
    long hardTimeoutMillis;

    /** 看门狗扫描间隔（毫秒）。属性 ecat.scheduler.watchdog-interval-millis */
    long watchdogIntervalMillis;

    /** 全引擎车道排队任务总量上限（防 OOM；超出记录+丢弃可观测）。属性 ecat.scheduler.max-queued */
    int maxQueuedTasks;

    /** 熔断连续失败阈值。属性 ecat.scheduler.breaker.fail-threshold */
    int breakerFailThreshold;

    /** 熔断冷却时长（毫秒）。属性 ecat.scheduler.breaker.cooldown-millis */
    long breakerCooldownMillis;

    /**
     * 从系统属性构建，全部参数可用 ecat.scheduler.* 覆盖。
     */
    public static SchedulerConfig fromSystemProperties() {
        return new SchedulerConfig(
                // 默认 6（原 4）：modbus source 事务车道并入引擎后（IO 收敛 P2：57 个 per-source
                // 专池线程收敛为引擎车道，事务在最坏 timeout×(retries+1) 内独占 worker），
                // live core 21 次 jstack 采样实测 57 源在途事务均值 0.9 / 峰值 3——4 覆盖峰值
                // 但不富余；6 留双倍余量，上线后以 system_health 的 workers.busy 持续=worker 数为扩容信号。
                intProp("ecat.scheduler.workers", 6, 1, 32, "workers"),
                longProp("ecat.scheduler.tick-millis", 100L, 10L, 60_000L, "tick-millis"),
                intProp("ecat.scheduler.wheel-slots", 512, 16, 1 << 20, "wheel-slots"),
                longProp("ecat.scheduler.slow-threshold-millis", 10_000L, 100L, 86_400_000L, "slow-threshold-millis"),
                longProp("ecat.scheduler.hard-timeout-millis", 30_000L, 1_000L, 86_400_000L, "hard-timeout-millis"),
                longProp("ecat.scheduler.watchdog-interval-millis", 1_000L, 100L, 60_000L, "watchdog-interval-millis"),
                intProp("ecat.scheduler.max-queued", 4096, 1, 1 << 20, "max-queued"),
                intProp("ecat.scheduler.breaker.fail-threshold", 5, 1, 1000, "breaker.fail-threshold"),
                longProp("ecat.scheduler.breaker.cooldown-millis", 300_000L, 1_000L, 86_400_000L, "breaker.cooldown-millis"));
    }

    public SchedulerConfig(int workers, long tickMillis, int wheelSlots,
                           long slowThresholdMillis, long hardTimeoutMillis, long watchdogIntervalMillis,
                           int maxQueuedTasks, int breakerFailThreshold, long breakerCooldownMillis) {
        this.workers = requireRange(workers, 1, 32, "workers");
        this.tickMillis = requireRange(tickMillis, 10L, 60_000L, "tick-millis");
        this.wheelSlots = roundUpToPowerOfTwo(requireRange(wheelSlots, 16, 1 << 20, "wheel-slots"));
        this.slowThresholdMillis = requireRange(slowThresholdMillis, 100L, 86_400_000L, "slow-threshold-millis");
        this.hardTimeoutMillis = requireRange(hardTimeoutMillis, 1_000L, 86_400_000L, "hard-timeout-millis");
        this.watchdogIntervalMillis = requireRange(watchdogIntervalMillis, 100L, 60_000L, "watchdog-interval-millis");
        this.maxQueuedTasks = requireRange(maxQueuedTasks, 1, 1 << 20, "max-queued");
        this.breakerFailThreshold = requireRange(breakerFailThreshold, 1, 1000, "breaker.fail-threshold");
        this.breakerCooldownMillis = requireRange(breakerCooldownMillis, 1_000L, 86_400_000L, "breaker.cooldown-millis");
    }

    private static int intProp(String key, int def, int min, int max, String label) {
        String raw = System.getProperty(key);
        if (raw == null || raw.trim().isEmpty()) {
            return def;
        }
        try {
            return requireRange(Integer.parseInt(raw.trim()), min, max, label);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ecat.scheduler 属性不是整数: " + key + "=" + raw);
        }
    }

    private static long longProp(String key, long def, long min, long max, String label) {
        String raw = System.getProperty(key);
        if (raw == null || raw.trim().isEmpty()) {
            return def;
        }
        try {
            return requireRange(Long.parseLong(raw.trim()), min, max, label);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ecat.scheduler 属性不是整数: " + key + "=" + raw);
        }
    }

    private static int requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("ecat.scheduler." + label + " 超出合法范围 [" + min + "," + max + "]: " + value);
        }
        return value;
    }

    private static long requireRange(long value, long min, long max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("ecat.scheduler." + label + " 超出合法范围 [" + min + "," + max + "]: " + value);
        }
        return value;
    }

    /** 非 2 的幂向上取整到 2 的幂（表轮槽位掩码取模要求；512 默认值天然满足）。 */
    private static int roundUpToPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result <<= 1;
        }
        return result;
    }
}
