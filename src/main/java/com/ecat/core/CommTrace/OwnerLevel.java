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

package com.ecat.core.CommTrace;

/**
 * IO 资源使用者的身份层级（显式枚举标注，不靠字段有无猜——构造校验与枚举值一一对应）。
 *
 * <p>层级与使用方式（{@link Usage}）正交：层级回答"使用者是谁"，用法回答"路径是否经他库转手"。
 * 包归属 com.ecat.core.CommTrace：归属数据的第一消费方是通讯追踪归因，与
 * CommTraceEvent/CommTraceBuffer 同包，最小暴露面。
 *
 * @author coffee
 */
public enum OwnerLevel {
    /** 集成本体（如 env-push 推送连接、桥、api 服务口的宿主集成）——旗下设备不算本层。 */
    INTEGRATION,
    /** 配置条目（entry 级消费方；deviceId 维度不可知或不适用的场景）。 */
    ENTRY,
    /** 设备（最常见：轮询/命令的直接使用者；entryId/deviceId 双必填，网关子设备折叠到网关 entry）。 */
    DEVICE,
    /** 旧签名兼容占位（register(info, String identity) 的自由字符串原样保留，如实标注不伪造结构身份）。 */
    LEGACY
}
