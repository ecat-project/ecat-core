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

package com.ecat.core.Observability;

import java.util.Collections;
import java.util.Map;

import lombok.Getter;

/**
 * 最近一次启动加载快照的留存点（arch-review 25 号 A 项）。
 *
 * <p>动机：日志查看器只读内存环 200 条/集成，startup-report 一行 INFO 会被流量冲掉，
 * 事后看不到本次启动耗时。{@code StartupLoadTracker} 在 ALL_LOADED 点存入本 holder
 * （仅最近一代，覆盖式），core-api 的 boot / integration-load-times 两个端点从这里读。
 *
 * <p>快照按 SRP 只留不可再算的事实：启动标识与总耗时 + 每集成分项耗时；集成数/entry 数
 * 可从 registry 列表自算、失败与超时名单在日志行（startup-report 前缀可 grep），不重复留存。
 *
 * @author coffee
 */
public final class StartupReportHolder {

    /** 最近一次启动的加载快照；null = 本 JVM 尚未完成过一轮集成加载。 */
    private static volatile Snapshot latest;

    private StartupReportHolder() {
    }

    /** 留存一代快照（后写覆盖先写——只关心最近一次 boot）。 */
    public static void record(Snapshot snapshot) {
        latest = snapshot;
    }

    /** 最近一次启动快照；未完成过启动时为 null，端点如实输出。 */
    public static Snapshot getLatest() {
        return latest;
    }

    /**
     * 启动加载快照：boot 标识、加载阶段起点（快照生成时刻减总耗时的推导值）、总耗时、
     * 每集成加载耗时（生命周期+entry 恢复两段合计，按总耗时降序）。
     * getter 由 lombok 生成；构造器手写（含 null→空 Map 防护，属有逻辑构造器例外）。
     */
    @Getter
    public static final class Snapshot {
        private final String bootId;
        private final long startedAtMillis;
        private final long totalMs;
        private final Map<String, Long> perIntegrationMs;

        public Snapshot(String bootId, long startedAtMillis, long totalMs,
                Map<String, Long> perIntegrationMs) {
            this.bootId = bootId;
            this.startedAtMillis = startedAtMillis;
            this.totalMs = totalMs;
            this.perIntegrationMs = perIntegrationMs == null
                    ? Collections.emptyMap() : Collections.unmodifiableMap(perIntegrationMs);
        }
    }
}
