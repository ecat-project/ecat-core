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
 * 命令型逻辑属性定义。
 *
 * <p>用于 standalone 命令属性（如 dispatch_command: ZERO_START/SPAN_START），
 * 由 {@code mapping.getAttr()} 创建 {@link LCommandAttribute} 实例时使用。
 * 在基类 {@link LogicAttributeDefine} 的基础上增加了 commands 字段，
 * 用于描述支持的命令列表。
 *
 * @see LogicAttributeDefine
 * @see LCommandAttribute
 * @see LCommandAttribute
 * @author coffee
 */
public class CommandAttrDef extends LogicAttributeDefine {

    /** 支持的命令列表（如 "ZERO_START", "SPAN_START"） */
    private final List<String> commands;

    /**
     * 11 参数构造函数，包含 persistable 和 defaultValue。
     *
     * @param attrId          逻辑属性ID
     * @param attrClass       属性类型
     * @param nativeUnit      原始信号单位，允许为null
     * @param displayUnit     显示单位，允许为null
     * @param precision       显示精度
     * @param changeable      是否允许用户/API修改属性值
     * @param attrType        属性值的具体类型（如 LCommandAttribute.class）
     * @param mapable         是否在ConfigFlow中让用户配置物理设备映射
     * @param commands        支持的命令列表
     * @param persistable     是否持久化属性状态
     * @param defaultValue    默认值，无历史记录时使用
     */
    public CommandAttrDef(String attrId, AttributeClass attrClass,
            UnitInfo nativeUnit, UnitInfo displayUnit, int precision,
            boolean changeable, Class<?> attrType, boolean mapable,
            List<String> commands, boolean persistable, Object defaultValue) {
        super(attrId, attrClass, nativeUnit, displayUnit, precision, changeable, attrType, persistable, defaultValue);
        setMapable(mapable);
        this.commands = commands;
    }

    /**
     * 9 参数构造函数，保持向后兼容。委托到 11 参数构造函数，persistable=false, defaultValue=null。
     */
    public CommandAttrDef(String attrId, AttributeClass attrClass,
            UnitInfo nativeUnit, UnitInfo displayUnit, int precision,
            boolean changeable, Class<?> attrType, boolean mapable,
            List<String> commands) {
        this(attrId, attrClass, nativeUnit, displayUnit, precision, changeable, attrType, mapable, commands, false, null);
    }

    /**
     * 获取支持的命令列表。
     *
     * @return 命令列表
     */
    public List<String> getCommands() {
        return commands;
    }
}
