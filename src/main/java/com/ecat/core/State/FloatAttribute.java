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

import com.ecat.core.I18n.I18nKeyPath;

/**
 * Float 数值属性类
 *
 * 提供Float类型的数值属性实现，适用于需要使用float而不是double的场景。
 *
 * @implNote displayName i18n supported, path: state.float_attr.{attributeID}
 * @author coffee
 */
public class FloatAttribute extends NumberAttribute<Float> {

    /**
     * 支持I18n的构造函数
     */
    public FloatAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision,
              unitChangeable, valueChangeable);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public FloatAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision,
              unitChangeable, valueChangeable);
    }

    /**
     * 支持I18n的构造函数
     */
    public FloatAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Function<AttrChangedCallbackParams<Float>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable, onChangedCallback);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public FloatAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Function<AttrChangedCallbackParams<Float>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable, onChangedCallback);
    }

    @Override
    public boolean updateValue(Float value) {
        return super.updateValue(value);
    }

    @Override
    public boolean updateValue(Float value, AttributeStatus newStatus) {
        return super.updateValue(value, newStatus);
    }

    @Override
    protected Float convertToType(double value) {
        return (float) value;
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.float_attr.", "");
    }
}
