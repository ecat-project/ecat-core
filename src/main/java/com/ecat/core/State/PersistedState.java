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

package com.ecat.core.State;

/**
 * 属性持久化状态数据
 * 通过 fastjson2 序列化为 JSON 存入 MapDB HTreeMap
 */
public class PersistedState {
    /** 属性原始值，保持 Java 类型 (Double/Float/Integer/Boolean/String 等) */
    public Object value;

    /** AttributeStatus 的数值 ID */
    public int statusCode;

    /** updateTime 的 epoch 毫秒数 */
    public long updateTimeEpochMs;

    /** nativeUnit 的完整字符串 (如 "AirVolumeUnit.PPM")，null 表示无单位 */
    public String nativeUnitStr;

    public PersistedState() {}
}
