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

package com.ecat.core.LogicState.BindState;

import com.ecat.core.LogicState.ILogicAttribute;

/**
 * 多源绑定数据类（对应 BindedPhyAttrData）。
 *
 * <p>BindedLogicAttrData 持有逻辑设备间属性绑定的元数据，
 * 用于 {@link LNumericBindMixAttribute} 管理多个逻辑源。
 *
 * <p>每个条目跟踪：
 * <ul>
 *   <li>{@code sourceDeviceId} - 源逻辑设备ID</li>
 *   <li>{@code sourceAttrId} - 源逻辑属性ID</li>
 *   <li>{@code resolvedSource} - 运行时解析后的源逻辑属性引用</li>
 *   <li>{@code updated} - 源属性是否在当前时间窗口内已更新</li>
 *   <li>{@code updateTime} - 最后一次更新的时间戳（毫秒）</li>
 * </ul>
 *
 * @see LNumericBindMixAttribute
 * @see com.ecat.core.LogicState.BindedPhyAttrData
 * @author coffee
 */
public class BindedLogicAttrData {

    /** 源逻辑设备ID */
    private final String sourceDeviceId;
    /** 源逻辑属性ID */
    private final String sourceAttrId;
    /** 运行时解析后的源逻辑属性引用 */
    private ILogicAttribute<?> resolvedSource;
    /** 源属性是否在当前时间窗口内已更新 */
    private boolean updated;
    /** 最后一次更新的时间戳（毫秒） */
    private long updateTime;

    /**
     * 构造函数 - 创建一个绑定数据条目。
     *
     * @param sourceDeviceId 源逻辑设备ID
     * @param sourceAttrId 源逻辑属性ID
     */
    public BindedLogicAttrData(String sourceDeviceId, String sourceAttrId) {
        this.sourceDeviceId = sourceDeviceId;
        this.sourceAttrId = sourceAttrId;
    }

    // ========== Getters ==========

    /**
     * 获取源逻辑设备ID。
     *
     * @return 源逻辑设备ID
     */
    public String getSourceDeviceId() {
        return sourceDeviceId;
    }

    /**
     * 获取源逻辑属性ID。
     *
     * @return 源逻辑属性ID
     */
    public String getSourceAttrId() {
        return sourceAttrId;
    }

    /**
     * 获取运行时解析后的源逻辑属性引用。
     *
     * @return 源逻辑属性，可能为null（未解析时）
     */
    public ILogicAttribute<?> getResolvedSource() {
        return resolvedSource;
    }

    /**
     * 源属性是否在当前时间窗口内已更新。
     *
     * @return true表示已更新
     */
    public boolean isUpdated() {
        return updated;
    }

    /**
     * 获取最后一次更新的时间戳。
     *
     * @return 时间戳（毫秒），0表示从未更新
     */
    public long getUpdateTime() {
        return updateTime;
    }

    // ========== Setters ==========

    /**
     * 设置运行时解析后的源逻辑属性引用。
     *
     * @param resolvedSource 源逻辑属性
     */
    public void setResolvedSource(ILogicAttribute<?> resolvedSource) {
        this.resolvedSource = resolvedSource;
    }

    /**
     * 设置源属性是否已更新。
     *
     * @param updated true表示已更新
     */
    public void setUpdated(boolean updated) {
        this.updated = updated;
    }

    /**
     * 设置最后一次更新的时间戳。
     *
     * @param updateTime 时间戳（毫秒）
     */
    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }
}
