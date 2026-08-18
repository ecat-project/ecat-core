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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备通讯失败熔断注册表（D2）：独立于调度车道的第二熔断命名空间，键 = 调用方稳定键
 * （约定 {@code coordinate:deviceId}），值为标准 {@link TaskCircuitBreaker}。
 *
 * <p><b>为什么与车道解耦（而不是把通讯失败并入车道熔断）</b>：车道键是集成坐标
 * （integration.coordinate），同集成的全部设备共享一条车道——把设备级通讯失败计入车道熔断，
 * 单台设备故障会连带冷却整集成的调度（其余健康设备的数据新鲜度被误伤）。通讯失败的粒度天然
 * 是「单设备与单链路」，故按调用方键独立熔断；阈值/冷却参数与车道熔断同源
 * （{@link SchedulerConfig}，TB Gateway 的 5 连败 → 300s 冷却模式）。
 *
 * <p><b>语义边界（严格模式）</b>：什么算「通讯失败」由调用方判定（超时/无有效响应/IO 异常），
 * 本类只数连败。间歇性失败（失败-成功交错）凑不满连败阈值，不会触发熔断——保护数据新鲜度。
 *
 * <p><b>守卫契约</b>：{@link #isCommOpen(String)} 兼任周期轮询的准入——OPEN 冷却期满后的第一次
 * 查询消耗探测名额转入 PROBING（返回 false 放行本轮探测），探测在途期间后续查询返回 true 跳过。
 * 调用方在守卫放行后必须保证对该键恰好一次 {@link #reportSuccess}/{@link #reportFailure}
 * （探测名额悬挂 = 该设备轮询被永久跳过）。
 *
 * <p>线程模型：上报频率 = 每设备每轮询周期的命令级速率，与车道熔断同量级，复用
 * {@link TaskCircuitBreaker} 内一把 ReentrantLock 的串行化即可。
 *
 * @author coffee
 */
final class CommBreakerRegistry {

    private final SchedulerConfig config;
    private final SchedulerClock clock;
    private final ConcurrentHashMap<String, TaskCircuitBreaker> breakers = new ConcurrentHashMap<>();

    CommBreakerRegistry(SchedulerConfig config, SchedulerClock clock) {
        this.config = config;
        this.clock = clock;
    }

    /** 通讯失败上报：与任务异常同权进入连败计数（同一熔断器内混排亦然）。 */
    void reportFailure(String key, String summary) {
        breaker(key).onCommFailure(summary);
    }

    /** 通讯成功上报：清零连败；PROBING/OPEN 期成功直接关闭（真实成功是最强健康信号）。 */
    void reportSuccess(String key) {
        breaker(key).onCommSuccess();
    }

    /**
     * 轮询守卫：true = 本轮轮询应整体跳过（OPEN 冷却期，或 PROBING 探测在途）；
     * false = 正常轮询，含「冷却期满，本轮作为探测放行」（见类注释守卫契约）。
     *
     * <p>从未上报过失败的键不创建条目——纯查询零内存成本，条目随首次失败上报诞生。
     */
    boolean isCommOpen(String key) {
        TaskCircuitBreaker breaker = breakers.get(key);
        if (breaker == null) {
            return false;
        }
        return breaker.admit(true) == TaskCircuitBreaker.Decision.SKIP_PERIODIC;
    }

    /** 各通讯熔断器快照（B2 自观测，system_health 的 commBreakers 表从这里采集）。 */
    Map<String, TaskCircuitBreaker> snapshot() {
        return new LinkedHashMap<>(breakers);
    }

    private TaskCircuitBreaker breaker(String key) {
        return breakers.computeIfAbsent(key,
                k -> new TaskCircuitBreaker(k, config.getBreakerFailThreshold(),
                        config.getBreakerCooldownMillis(), clock));
    }
}
