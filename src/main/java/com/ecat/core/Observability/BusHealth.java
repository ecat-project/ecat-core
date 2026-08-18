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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.consumer.BusConsumerBase;

/**
 * system_health 总线节：发布计数 + 各消费者队列深度/丢批/处理量（14 号架构 §5.4-2）。
 *
 * <p>发布总数来自 BusRegistry 的 LongAdder（热路径一次原子自增）；消费者表来自
 * BusConsumerBase 的弱引用注册表（队列深度/丢最旧计数是消费者地基既有埋点，只补了枚举）。
 * 速率（publishPerSecond）由 {@link SystemHealthService} 读时差分注入。
 */
final class BusHealth {

    private BusHealth() {
    }

    /** 总线快照；busRegistry 为 null（组装缺失）时如实返回 null。 */
    static Map<String, Object> collect(BusRegistry busRegistry) {
        if (busRegistry == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publishedTotal", busRegistry.getPublishedCount());

        List<Map<String, Object>> consumers = new ArrayList<>();
        for (BusConsumerBase<?> consumer : BusConsumerBase.liveConsumers()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("name", consumer.getName());
            cm.put("queueSize", consumer.getQueueSize());
            cm.put("queueCapacity", consumer.getQueueCapacity());
            cm.put("dropped", consumer.getDroppedCount());
            cm.put("processed", consumer.getProcessedCount());
            consumers.add(cm);
        }
        out.put("consumerCount", consumers.size());
        out.put("consumers", consumers);
        return out;
    }
}
