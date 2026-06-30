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

import java.time.Instant;

/**
 * 属性持久化状态数据（不可变 AttrState 的精简映射，围绕 state 持久化）。
 * 通过 fastjson2 序列化为 JSON 存入 MapDB HTreeMap。
 *
 * <p>不存 AttrState.valueType(Class) 和 context(EventContext)——运行时由 attr 重建，
 * 避免 Class 序列化要求类在 classpath、EventContext 瞬态落盘。
 *
 * <p>version 标识持久化结构版本；围绕 state 重设计前的旧数据（无 version 字段，
 * 反序列化为 0）在 restore 时识别并丢弃，不走迁移。
 */
public class PersistedState {

    /** 持久化结构版本；旧数据（围绕 state 重设计前，JSON 无 version 字段）反序列化为 int 默认 0，
     *  restore 时据此废弃。from() 写新数据时显式设 2。 */
    public int version;

    /** 属性业务值（= state.value），Instant 归一为 epoch 毫秒（Long）保持读写对称 */
    public Object value;

    /** AttributeStatus 的数值 ID */
    public int statusCode;

    /** lastUpdated 的 epoch 毫秒数 */
    public long updateTimeEpochMs;

    /** nativeUnit 的完整字符串 (如 "AirMassUnit.UGM3")，null 表示无单位 */
    public String nativeUnitStr;

    public PersistedState() {}

    /**
     * 从不可变 AttrState 精简映射（saveState 用）。
     * 围绕 state 持久化——从 state 提取业务字段，不戳 attr 内部。
     */
    public static PersistedState from(AttrState<?> s) {
        PersistedState ps = new PersistedState();
        ps.version = 2;  // 围绕 state 重设计后的新结构版本；version<2 的旧数据（含 F 前 version=1）restore 废弃
        Object persistValue = s.getValue();
        if (persistValue instanceof Instant) {
            persistValue = ((Instant) persistValue).toEpochMilli();
        }
        ps.value = persistValue;
        ps.statusCode = s.getStatus() != null ? s.getStatus().getId() : 0;
        ps.updateTimeEpochMs = s.getLastUpdated() != null ? s.getLastUpdated().toEpochMilli() : 0L;
        ps.nativeUnitStr = s.getNativeUnit() != null ? s.getNativeUnit().getFullUnitString() : null;
        return ps;
    }
}
