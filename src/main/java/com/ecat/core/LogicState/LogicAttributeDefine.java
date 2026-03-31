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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 业务（逻辑）属性定义类，描述一个逻辑属性的 schema。
 *
 * <p>由 IDeviceMapping.getAttrDefs() 返回，用于 LogicDevice 创建逻辑属性时提供初始化信息。
 * LogicAttributeDefine 是不可变的配置元数据，描述逻辑属性的"蓝图"，
 * 实际的逻辑属性实例由 L系列类（如 LAQAttribute、LNumericAttribute）创建。
 *
 * <p>示例用法（在 IDeviceMapping 实现中）：
 * <pre>
 *   List&lt;LogicAttributeDefine&gt; getAttrDefs() {
 *       return Arrays.asList(
 *           new LogicAttributeDefine("so2", AttributeClass.SO2,
 *               AirMassUnit.UGM3, AirMassUnit.MGM3, 2, false, AQAttribute.class),
 *           new LogicAttributeDefine("status", AttributeClass.STATUS,
 *               null, null, 0, false, TextAttribute.class)
 *       );
 *   }
 * </pre>
 *
 * @see com.ecat.core.LogicState.ILogicAttribute#initFromDefinition(LogicAttributeDefine)
 * @author coffee
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LogicAttributeDefine {
    /** 逻辑属性ID，在逻辑设备内唯一 */
    private String attrId;
    /** 属性类型，对应 AttributeClass 枚举 */
    private AttributeClass attrClass;
    /** 原始信号单位，允许为null（如状态、文本类型属性） */
    private UnitInfo nativeUnit;
    /** 显示单位，允许为null，显示使用，不存储数据库 */
    private UnitInfo displayUnit;
    /** 显示精度，小数位数，显示使用不存储数据库 */
    private int displayPrecision;
    /** 是否允许用户/API修改属性值 */
    private boolean valueChangeable;
    /** 属性值的具体类型（如 AQAttribute.class、NumericAttribute.class、TextAttribute.class） */
    private Class<?> attrClassType;
    /** 显示名称，由 IDeviceMapping.getAttrDefs() 实现通过 i18n 设置 */
    private String displayName;

    /**
     * 7 参数构造函数，保持向后兼容。
     * 新代码请使用 8 参数构造函数或通过 setDisplayName() 设置显示名称。
     */
    public LogicAttributeDefine(String attrId, AttributeClass attrClass,
            UnitInfo nativeUnit, UnitInfo displayUnit, int displayPrecision,
            boolean valueChangeable, Class<?> attrClassType) {
        this.attrId = attrId;
        this.attrClass = attrClass;
        this.nativeUnit = nativeUnit;
        this.displayUnit = displayUnit;
        this.displayPrecision = displayPrecision;
        this.valueChangeable = valueChangeable;
        this.attrClassType = attrClassType;
    }

    /**
     * 获取显示名称，如果未设置则返回 attrId。
     */
    public String getDisplayName() {
        return displayName != null ? displayName : attrId;
    }
}
