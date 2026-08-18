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

import com.ecat.core.Task.engine.SchedulerClock;

/**
 * 停机编排的单个阶段（C2 优雅停机）。机制/策略分离：{@link ShutdownOrchestrator} 只管
 * 「按序执行 + 预算扣减 + 超时 WARN + 不硬等」，阶段本体实现「这段收尾做什么」。
 *
 * <p>契约：
 * <ul>
 * <li>{@link #execute} 返回 true = 阶段目标已在截止时刻前达成；false = 超时或部分单元未完成
 *     （编排器记 WARN 后进入下一阶段，绝不硬等——停机必须最终能退）。</li>
 * <li>{@link #execute} 内部所有有界等待以 {@code deadlineNanos}（{@code clock} 单调基准）为界，
 *     不自定固定 sleep。</li>
 * <li>抛异常不会中断后续阶段：编排器捕获后记 FAILED 并继续（见 {@link ShutdownOrchestrator}）。</li>
 * <li>时钟经参数注入：单测用虚拟时钟手动推进（测试纪律：禁 sleep 同步），生产传
 *     {@link SchedulerClock#SYSTEM}。</li>
 * </ul>
 *
 * @author coffee
 */
public interface ShutdownStage {

    /** 阶段名（日志与报告标识，如 bus-drain）。 */
    String name();

    /** 本阶段预算（毫秒）；编排器再与总预算剩余值取小。 */
    long budgetMillis();

    /**
     * 执行阶段动作。
     *
     * @param deadlineNanos 截止时刻（clock.nanoTime 基准，已按阶段预算与总预算剩余取小折算）
     * @param clock         单调时钟（与 deadlineNanos 同基准）
     * @return true = 阶段目标在截止前达成；false = 超时/部分未完成（编排器记 WARN）
     */
    boolean execute(long deadlineNanos, SchedulerClock clock);
}
