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
 * 当前运行超 blocking 阈值（1s）的任务观测快照（103000 观测补盲，B2 system_health）：
 * 车道键 + 任务类 + 已运行毫秒——「worker 被单任务钉死」从 jstack 偶然抓取变成端点直读。
 *
 * <p>由 TaskWatchdog 的在跑任务集读出，纯观测值，不参与调度决策。
 */
@Value
public class BlockingTaskStatus {

    /** 任务所在车道键（集成坐标 / 显式车道键）。 */
    String laneKey;

    /** 提交方任务类名（lambda 后缀已剥）。 */
    String taskLabel;

    /** 本次执行已运行毫秒数（快照时刻）。 */
    long elapsedMillis;
}
