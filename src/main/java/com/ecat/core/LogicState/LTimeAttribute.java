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

import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.TimeAttribute;
import com.ecat.core.State.UnitInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 逻辑时间属性 - 支持绑定物理 TimeAttribute（1:1 透传）和 standalone 模式。
 *
 * <p>LTimeAttribute 继承 {@link TimeAttribute} 并实现 {@link ILogicAttribute}，
 * 提供逻辑设备层的时间属性管理能力。
 *
 * <p>两种模式：
 * <ul>
 *   <li><b>绑定模式</b>: 包装一个物理 TimeAttribute，值更新时透传</li>
 *   <li><b>Standalone 模式</b>: 自维护，无物理绑定</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 *   // 绑定模式
 *   TimeAttribute phyAttr = ...;
 *   LTimeAttribute logicAttr = new LTimeAttribute(phyAttr);
 *   logicAttr.initFromDefinition(attrDef);
 *
 *   // Standalone 模式
 *   LTimeAttribute standalone = new LTimeAttribute("last_change", AttributeClass.TIME);
 * </pre>
 *
 * @see TimeAttribute
 * @see ILogicAttribute
 * @author coffee
 */
public class LTimeAttribute extends TimeAttribute implements ILogicAttribute<Instant> {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 绑定的物理 TimeAttribute；null 表示 standalone 模式 */
    private final TimeAttribute bindAttr;

    /**
     * 绑定构造函数 - 创建一个绑定到物理 TimeAttribute 的逻辑时间属性。
     *
     * <p>使用 bindAttr 的元数据作为初始值。所有逻辑层元数据
     * （attributeID、nativeUnit、precision 等）将通过
     * {@link #initFromDefinition(LogicAttributeDefine)} 覆盖。
     *
     * @param bindAttr 要绑定的物理时间属性
     */
    public LTimeAttribute(TimeAttribute bindAttr) {
        super(bindAttr.getAttributeID(), bindAttr.getAttrClass(),
              bindAttr.getNativeUnit(), bindAttr.getDisplayUnit(),
              bindAttr.getDisplayPrecision(),
              false, false);
        this.bindAttr = bindAttr;
    }

    /**
     * Master standalone constructor with persistable support.
     *
     * @param attributeID 逻辑属性ID
     * @param attrClass 属性类型
     * @param persistable 是否持久化属性值
     * @param defaultValue 默认值（无持久化记录时使用）
     */
    public LTimeAttribute(String attributeID, AttributeClass attrClass,
                          boolean persistable, java.time.Instant defaultValue) {
        super(attributeID, attrClass, null, null, 0, false, false);
        this.persistable = persistable;
        this.defaultValue = defaultValue;
        this.bindAttr = null;
    }

    /**
     * Standalone 构造函数 - 创建一个独立的逻辑时间属性（无物理绑定）。
     *
     * @param attributeID 逻辑属性ID
     * @param attrClass 属性类型
     */
    public LTimeAttribute(String attributeID, AttributeClass attrClass) {
        this(attributeID, attrClass, false, null);
    }

    /**
     * 当绑定的物理时间属性值更新时，同步更新本逻辑属性的值。
     *
     * @param updatedAttr 值已更新的物理属性
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        if (bindAttr == null) return;

        if (updatedAttr instanceof TimeAttribute) {
            TimeAttribute source = (TimeAttribute) updatedAttr;
            if (source.getValue() != null) {
                updateValue(source.getValue());
            }
        }
    }

    /**
     * 返回包含绑定物理属性的单例列表。
     *
     * @return 包含 bindAttr 的单例列表，或空列表（standalone 模式）
     */
    @Override
    public List<AttributeBase<?>> getBindedAttrs() {
        if (bindAttr == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(bindAttr);
    }

    /**
     * 设置逻辑属性的 attributeID。
     *
     * @param attrID 逻辑属性ID
     */
    @Override
    public void initAttributeID(String attrID) {
        this.attributeID = attrID;
    }

    /**
     * 设置逻辑属性的原始单位。
     *
     * @param nativeUnit 原始单位
     */
    @Override
    public void initNativeUnit(UnitInfo nativeUnit) {
        this.nativeUnit = nativeUnit;
    }

    /**
     * 初始化显示单位（直接赋值，不受 unitChangeable 限制）。
     */
    @Override
    public void initDisplayUnit(UnitInfo displayUnit) {
        this.displayUnit = displayUnit;
    }

    /**
     * 设置是否允许外部修改值。
     *
     * @param valueChangeable true表示允许
     */
    @Override
    public void initValueChangeable(boolean valueChangeable) {
        this.valueChangeable = valueChangeable;
    }

    /**
     * 初始化逻辑属性的属性类型。
     *
     * @param attrClass 属性类型，允许为null
     */
    @Override
    public void initAttrClass(AttributeClass attrClass) {
        this.attrClass = attrClass;
    }

    /**
     * 设置显示值 - 绑定模式透传到物理属性，standalone 模式本地设置。
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        if (bindAttr != null) {
            return bindAttr.setDisplayValue(newDisplayValue, bindAttr.getNativeUnit());
        }
        return super.setDisplayValue(newDisplayValue, fromUnit);
    }

    /**
     * 使用 Instant 值更新绑定的逻辑属性值。
     * 将 Instant 格式化为 "yyyy-MM-dd HH:mm:ss" 后设置显示值。
     *
     * @param value Instant 时间值
     */
    public void updateBindAttrValue(Instant value) {
        if (value != null) {
            updateValue(value);
        }
    }

    /**
     * 返回是否为 standalone 模式。
     *
     * @return true表示standalone模式，false表示绑定模式
     */
    public boolean isStandalone() {
        return bindAttr == null;
    }

    /**
     * 获取绑定的物理 TimeAttribute。
     *
     * @return 绑定的物理属性，或 null（standalone 模式）
     */
    public TimeAttribute getBindAttr() {
        return bindAttr;
    }
}
