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

package com.ecat.core.Config;

/**
 * core 自有配置（{@code ecat.*} 系统属性）的集中读取点——业务/组件代码禁止随地
 * {@code System.getProperty} 散读（散读点不可盘点、默认值漂移无治理面）。
 *
 * <p>本类是集中点的前两项收编（历史散读 17 处为独立治理任务，逐项迁入不改行为）：
 * 新增配置一律落此处，不再新增散读点。
 *
 * @author coffee
 */
public final class EcatConfig {

    private EcatConfig() {
    }

    /**
     * GuardedExecutor 硬超时默认值：{@code ecat.guarded.timeout-ms}，未配置 60s。
     * 严格模式：属性配置了但不是合法正整数 → 抛 IllegalArgumentException（不静默回退默认值）。
     */
    public static long guardedTimeoutMs() {
        String raw = System.getProperty("ecat.guarded.timeout-ms");
        if (raw == null) {
            return 60_000L;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) {
                throw new IllegalArgumentException("必须为正整数, 实际: " + raw);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ecat.guarded.timeout-ms 配置非法: " + raw, e);
        }
    }

    /**
     * 通讯追踪 RX 设备归属回填的新鲜度窗口：{@code ecat.commtrace.rx-attrib-window-ms}，
     * 未配置 30s。窗口外的陈旧 TX 上下文不回填（归属不可靠，如实 null）。
     * 严格模式同 {@link #guardedTimeoutMs()}：0 = 关闭回填（合法边界，显式关闭），负数非法。
     */
    public static long commTraceRxAttributionWindowMs() {
        String raw = System.getProperty("ecat.commtrace.rx-attrib-window-ms");
        if (raw == null) {
            return 30_000L;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0) {
                throw new IllegalArgumentException("必须为非负整数(0=关闭回填), 实际: " + raw);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ecat.commtrace.rx-attrib-window-ms 配置非法: " + raw, e);
        }
    }
}
