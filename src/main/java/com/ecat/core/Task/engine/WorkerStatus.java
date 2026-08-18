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
 * 单 worker 忙闲观测快照（B2 system_health）：线程名 + 当前执行车道。
 *
 * <p>由 LaneDispatcher 的 workerLanes 印记数组读出——纯观测值，不参与调度决策。
 */
@Value
public class WorkerStatus {

    /** worker 线程名（ecat-sched-worker-N）。 */
    String threadName;

    /** 当前正在执行的车道键；null = 空闲。 */
    String runningLane;
}
