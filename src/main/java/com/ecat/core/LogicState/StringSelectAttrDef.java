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

import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.UnitInfo;

import java.util.List;

/**
 * 字符串选择型逻辑属性定义。
 *
 * <p>用于 standalone 字符串选择属性（如 manual_status: Normal/Maintenance/Calibration），
 * 由 {@code mapping.getAttr()} 创建 {@link LStringSelectAttribute} 实例时使用。
 * 在基类 {@link LogicAttributeDefine} 的基础上增加了 options 字段，
 * 用于描述可选值列表。
 *
 * @see LogicAttributeDefine
 * @see LStringSelectAttribute
 * @see LStringSelectAttribute
 * @author coffee
 */
public class StringSelectAttrDef extends LogicAttributeDefine {

    /** 可选值列表（如 "Normal", "Maintenance", "Calibration"） */
    private final List<String> options;

    /**
     * 11 参数构造函数，包含 persistable 和 defaultValue。
     *
     * @param attrId          逻辑属性ID
     * @param attrClass       属性类型
     * @param nativeUnit      原始信号单位，允许为null
     * @param displayUnit     显示单位，允许为null
     * @param precision       显示精度
     * @param changeable      是否允许用户/API修改属性值
     * @param attrType        属性值的具体类型（如 LStringSelectAttribute.class）
     * @param mapable         是否在ConfigFlow中让用户配置物理设备映射
     * @param options         可选值列表
     * @param persistable     是否持久化属性状态
     * @param defaultValue    默认值，无历史记录时使用
     */
    public StringSelectAttrDef(String attrId, AttributeClass attrClass,
            UnitInfo nativeUnit, UnitInfo displayUnit, int precision,
            boolean changeable, Class<?> attrType, boolean mapable,
            List<String> options, boolean persistable, Object defaultValue) {
        super(attrId, attrClass, nativeUnit, displayUnit, precision, changeable, attrType, persistable, defaultValue);
        setMapable(mapable);
        this.options = options;
    }

    /**
     * 9 参数构造函数，保持向后兼容。委托到 11 参数构造函数，persistable=false, defaultValue=null。
     */
    public StringSelectAttrDef(String attrId, AttributeClass attrClass,
            UnitInfo nativeUnit, UnitInfo displayUnit, int precision,
            boolean changeable, Class<?> attrType, boolean mapable,
            List<String> options) {
        this(attrId, attrClass, nativeUnit, displayUnit, precision, changeable, attrType, mapable, options, false, null);
    }

    /**
     * 获取可选值列表。
     *
     * @return 可选值列表
     */
    public List<String> getOptions() {
        return options;
    }
}
