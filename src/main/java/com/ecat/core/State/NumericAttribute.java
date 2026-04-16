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

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;


/**
 * NumericAttribute class represents a numeric attribute with a specific unit and display format.
 *
 * This class extends the NumberAttribute class and provides methods to handle numeric values,
 * unit conversions, and display formatting.
 *
 * It is suitable for attributes that represent numeric states such as
 * current, voltage, wind speed, wind direction, etc.
 *
 * @apiNote displayName i18n supported, path: state.numeric_attr.{attributeID}
 *
 * @author coffee
 */
public class NumericAttribute extends NumberAttribute<Double> {

    /**
     * 支持I18n的构造函数
     */
	public NumericAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision,
              unitChangeable, valueChangeable);
    }

    /**
     * 仅 attributeID 的构造函数，用于 "先 new 再 init" 模式。
     * 不要滥用，仅在此模式下使用。
     */
    protected NumericAttribute(String attributeID) {
        super(attributeID);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public NumericAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision,
              unitChangeable, valueChangeable);
    }

    /**
     * 支持I18n的构造函数
     */
    public NumericAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable, onChangedCallback);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public NumericAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable, onChangedCallback);
    }

    /**
     * 完整参数构造函数（包含 persistable + defaultValue）
     * 用于支持属性持久化场景
     */
    public NumericAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, boolean persistable, Double defaultValue,
            Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable, persistable, defaultValue, onChangedCallback);
    }

    @Override
    public boolean updateValue(Double value) {
        return super.updateValue(value);
    }

    @Override
    public boolean updateValue(Double value, AttributeStatus newStatus) {
        return super.updateValue(value, newStatus);
    }

    @Override
    protected Double convertToType(double value) {
        return value;
    }

}
