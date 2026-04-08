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
import com.ecat.core.LogicState.LNumericAttribute;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.UnitInfo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 单源数值绑定（逻辑属性到逻辑属性）。
 *
 * <p>LNumericBindAttribute 继承 {@link LNumericAttribute} 并实现 {@link ILogicBindAttribute}，
 * 表示该逻辑数值属性的值来源于另一个逻辑设备的属性。
 *
 * <p>构造时只存字符串定位器（sourceDeviceId, sourceAttrId），不存对象引用。
 * 运行时通过 {@link #resolveSource(ILogicAttribute)} 注入源属性引用，
 * 之后 {@link #getBindedAttrs()} 返回已解析的源。
 *
 * <p>使用示例：
 * <pre>
 *   LNumericBindAttribute bindAttr = new LNumericBindAttribute(
 *       "total_power", AttributeClass.POWER, null, null, 2,
 *       "ups_device_id", "output_power");
 *   // 运行时解析后注入源属性
 *   bindAttr.resolveSource(sourceLogicAttr);
 * </pre>
 *
 * @see ILogicBindAttribute
 * @see LNumericAttribute
 * @author coffee
 */
public class LNumericBindAttribute extends LNumericAttribute
        implements ILogicBindAttribute<Double> {

    /** 源逻辑设备ID */
    private final String sourceDeviceId;
    /** 源逻辑属性ID */
    private final String sourceAttrId;
    /** 运行时解析后的源属性引用 */
    private ILogicAttribute<?> resolvedSource;

    /**
     * 构造函数 - 创建一个绑定到源逻辑属性的数值属性。
     *
     * @param attrId 本逻辑属性ID
     * @param attrClass 属性类型
     * @param nativeUnit 原始信号单位
     * @param displayUnit 显示单位
     * @param precision 显示精度
     * @param sourceDeviceId 源逻辑设备ID
     * @param sourceAttrId 源逻辑属性ID
     */
    public LNumericBindAttribute(String attrId, AttributeClass attrClass,
                                 UnitInfo nativeUnit, UnitInfo displayUnit,
                                 int precision,
                                 String sourceDeviceId, String sourceAttrId) {
        super(attrId, attrClass, nativeUnit, displayUnit, precision);
        this.sourceDeviceId = sourceDeviceId;
        this.sourceAttrId = sourceAttrId;
    }

    /**
     * 返回已解析的源逻辑属性列表。
     *
     * @return 包含源属性的单例列表，或空列表
     */
    @Override
    public List<AttributeBase<?>> getBindedAttrs() {
        if (resolvedSource instanceof AttributeBase) {
            return Collections.singletonList((AttributeBase<?>) resolvedSource);
        }
        return Collections.emptyList();
    }

    /**
     * 解析并注入源逻辑属性引用。
     * 由 LogicDeviceManager 在两阶段初始化的第二阶段调用。
     *
     * @param source 源逻辑属性
     */
    public void resolveSource(ILogicAttribute<?> source) {
        this.resolvedSource = source;
    }

    /**
     * 默认实现：从源逻辑属性读取显示值并更新。
     * 子类可覆写以实现特定业务计算（如单位转换、缩放等）。
     *
     * @param updatedAttr 值已更新的源属性
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        if (updatedAttr instanceof LNumericAttribute) {
            LNumericAttribute source = (LNumericAttribute) updatedAttr;
            // LNumericAttribute 绑定模式下 value 在 bindAttr 中，
            // 优先从 getValue() 读取，如果为 null 则从 bindAttr 获取
            Object rawValue = source.getValue();
            if (rawValue != null) {
                updateValue(((Number) rawValue).doubleValue());
            } else {
                // 尝试从绑定的物理属性读取
                java.util.List<AttributeBase<?>> binded = source.getBindedAttrs();
                if (!binded.isEmpty() && binded.get(0).getValue() != null) {
                    Object phyValue = binded.get(0).getValue();
                    updateValue(((Number) phyValue).doubleValue());
                }
            }
        }
    }

    /**
     * 设置显示值 - 绑定属性默认不支持外部设置，返回 false。
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * 绑定模式始终返回 false。
     *
     * @return false
     */
    public boolean isStandalone() {
        return false;
    }

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
}
