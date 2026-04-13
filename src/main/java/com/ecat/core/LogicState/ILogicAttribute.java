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

package com.ecat.core.LogicState;

import com.ecat.core.State.AttributeAbility;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.UnitInfo;

import java.util.List;

/**
 * 逻辑属性接口，扩展 AttributeAbility，提供逻辑设备层属性管理能力。
 *
 * <p>逻辑属性（LogicAttribute）是逻辑设备（LogicDevice）中的属性抽象，
 * 与物理设备（DeviceBase）中的属性（AttributeBase）不同，逻辑属性可能：
 * <ul>
 *   <li>由一个或多个物理属性映射计算而来（绑定属性）</li>
 *   <li>直接从 LogicAttributeDefine 定义初始化</li>
 *   <li>作为设备映射（IDeviceMapping）中的业务属性</li>
 * </ul>
 *
 * <p>实现类通常是 L 系列属性类（如 LAQAttribute、LNumericAttribute 等），
 * 它们通过 {@link #initFromDefinition(LogicAttributeDefine)} 从定义对象初始化自身。
 *
 * @param <T> 属性值的类型（如 Double、String 等）
 * @see LogicAttributeDefine
 * @see com.ecat.core.State.AttributeAbility
 * @author coffee
 */
public interface ILogicAttribute<T> extends AttributeAbility<T> {

    /**
     * 当绑定的物理属性值更新时调用，由 LogicDevice 或映射管理器触发。
     * 实现类应在此方法中根据绑定的物理属性更新逻辑属性的值。
     *
     * @param updatedAttr 值已更新的物理属性
     */
    void updateBindAttrValue(AttributeBase<?> updatedAttr);

    /**
     * 获取当前逻辑属性绑定的所有物理属性列表。
     * 返回的列表可能为空（对于非映射类型的逻辑属性）。
     *
     * @return 绑定的物理属性列表，不会为null
     */
    List<AttributeBase<?>> getBindedAttrs();

    /**
     * 初始化逻辑属性的ID。
     * 由 {@link #initFromDefinition(LogicAttributeDefine)} 调用。
     *
     * @param attrID 逻辑属性ID，在逻辑设备内唯一
     */
    void initAttributeID(String attrID);

    /**
     * 初始化逻辑属性的原始信号单位。
     * 由 {@link #initFromDefinition(LogicAttributeDefine)} 调用。
     *
     * @param nativeUnit 原始信号单位，允许为null（如状态、文本类型属性）
     */
    void initNativeUnit(UnitInfo nativeUnit);

    /**
     * 初始化逻辑属性是否允许外部修改值。
     * 由 {@link #initFromDefinition(LogicAttributeDefine)} 调用。
     *
     * @param valueChangeable true表示允许用户/API修改属性值
     */
    void initValueChangeable(boolean valueChangeable);

    /**
     * 初始化逻辑属性的显示单位。
     * 与 {@link #initNativeUnit} 类似，直接赋值，不受 unitChangeable 限制。
     *
     * @param displayUnit 显示单位，允许为null
     */
    void initDisplayUnit(UnitInfo displayUnit);

    /**
     * 初始化逻辑属性的属性类型（如 TEMPERATURE、ALARM_STATUS 等）。
     * 由 {@link #initFromDefinition(LogicAttributeDefine)} 调用。
     *
     * @param attrClass 属性类型，允许为null
     */
    void initAttrClass(AttributeClass attrClass);

    /**
     * 从 LogicAttributeDefine 定义对象初始化逻辑属性。
     * <p>
     * 此 default 方法依次调用各 init 方法完成初始化：
     * <ol>
     *   <li>{@link #initAttributeID(String)} - 设置属性ID</li>
     *   <li>{@link #initNativeUnit(UnitInfo)} - 设置原始单位</li>
     *   <li>{@link #initValueChangeable(boolean)} - 设置是否可修改</li>
     *   <li>{@link #initDisplayUnit(UnitInfo)} - 设置显示单位（仅当displayUnit不为null时）</li>
     *   <li>{@link #changeDisplayPrecision(int)} - 设置显示精度</li>
     *   <li>{@link #initAttrClass(AttributeClass)} - 设置属性类型</li>
     *   <li>persistable/defaultValue - 持久化支持（并列关系，非嵌套）</li>
     * </ol>
     *
     * @param def 逻辑属性定义对象，提供初始化所需的元数据
     */
    default void initFromDefinition(LogicAttributeDefine def) {
        initAttributeID(def.getAttrId());
        initNativeUnit(def.getNativeUnit());
        initValueChangeable(def.isValueChangeable());
        initDisplayUnit(def.getDisplayUnit());
        changeDisplayPrecision(def.getDisplayPrecision());
        initAttrClass(def.getAttrClass());
        // 持久化支持
        // persistable 和 defaultValue 是并列关系
        if (def.isPersistable()) {
            setPersistable(true);
        }
        if (def.getDefaultValue() != null) {
            @SuppressWarnings("unchecked")
            ILogicAttribute<Object> raw = (ILogicAttribute<Object>) this;
            raw.setDefaultValue(def.getDefaultValue());
        }
    }
}
