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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 逻辑车道：每车道键一条 FIFO，公平进就绪环，由共享 worker 池轮转消费。
 *
 * <p>车道是逻辑队列不是物理线程——同键任务串行（防设备轮询重入、保持旧 2 线程池下
 * 「同设备轮询天然错开」的行为），异键并行；单车道阻塞只占用正在执行它的那一个 worker，
 * 其余车道照常出队。这是受限资源下的逻辑 bulkhead（否决物理 per-device 池：100 设备 × N 线程
 * 在 512MB 堆预算下不可行）。
 *
 * <p>{@code idle} 的语义是「当前没有 worker 携带本车道」：入队时 CAS true→false 成功者负责把
 * 车道挂上就绪环；worker 完成任务后若队列非空则重新挂环（串行保证：车道在任务完成前不回环），
 * 为空则复位 idle 并复查（防「复位与入队」交叠丢唤醒，见 LaneDispatcher 注释）。
 *
 * @author coffee
 */
final class Lane {

    final String key;
    final Queue<EngineTask<?>> queue = new ConcurrentLinkedQueue<>();
    /** 无 worker 携带时为 true；就绪环去重标志（至多在环一个实例）。 */
    final AtomicBoolean idle = new AtomicBoolean(true);
    final TaskCircuitBreaker breaker;

    Lane(String key, SchedulerConfig config, SchedulerClock clock) {
        this.key = key;
        this.breaker = new TaskCircuitBreaker(key, config.getBreakerFailThreshold(),
                config.getBreakerCooldownMillis(), clock);
    }
}
