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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.Utils.NumberFormatter;

/**
 * 适用于TVOC需要符合计算的参数
 * NOx = NO + NO2，但是转换ppm和ug/m3可以根据公式计算（分子量按NO2=46计算），因此NOx不算组合属性：https://www.doc88.com/p-1436999602154.html
 * 组合属性一般要求是多个子属性值的求和，并且要符合统一的单位
 * 组合属性原始单位一定一致
 * 更改组合属性展示单位时会连带更改子属性的单位，确保displayValue是符合展示单位的数据
 *
 * @implNote displayName i18n supported, path: state.aq_combine_attr.{attributeID}
 *
 * @author coffee
 */
public class AQCombineAttribute extends AQAttribute {

    public List<AQAttribute> speAttrs; //组合式属性的子属性

    /**
     * 辅助方法：从子属性获取分子量（取第一个子属性的分子量）
     * @param speAttrs 子属性列表
     * @return 第一个子属性的分子量
     * @throws IllegalArgumentException 如果子属性列表为空或第一个子属性的分子量为null
     */
    private static Double calculateMolecularWeight(List<AQAttribute> speAttrs) {
        if (speAttrs.isEmpty()) {
            throw new IllegalArgumentException("speAttrs cannot be empty for AQCombineAttribute");
        }
        Double weight = speAttrs.get(0).molecularWeight;
        if (weight == null) {
            throw new IllegalArgumentException("molecularWeight cannot be null for AQCombineAttribute sub-attributes");
        }
        return weight;
    }

    /**
     * 支持I18n的构造函数
     *
     * @param attributeID
     * @param attrClass
     * @param nativeUnit
     * @param displayUnit
     * @param displayPrecision
     * @param unitChangeable
     * @param speAttrs
     */
	public AQCombineAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            List<AQAttribute> speAttrs) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision,
                unitChangeable, false, calculateMolecularWeight(speAttrs));
        this.speAttrs = speAttrs;
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     *
     * @param attributeID
     * @param displayName
     * @param attrClass
     * @param nativeUnit
     * @param displayUnit
     * @param displayPrecision
     * @param unitChangeable
     * @param speAttrs
     */
    public AQCombineAttribute(String attributeID, String displayName, AttributeClass attrClass,
            UnitInfo nativeUnit, UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            List<AQAttribute> speAttrs) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision,
                unitChangeable, false, calculateMolecularWeight(speAttrs));
        this.speAttrs = speAttrs;
    }

    @Override
	protected CompletableFuture<Boolean> setValue(Double value) {
		throw new RuntimeException(I18nHelper.t("error.combine_attribute_value_not_changeable"));
	}

    @Override
    public boolean updateValue(Double value) {
        throw new RuntimeException(I18nHelper.t("error.combine_attribute_value_not_changeable"));
    }

    @Override
    public boolean updateValue(Double value, AttributeStatus newStatus) {
        throw new RuntimeException(I18nHelper.t("error.combine_attribute_value_not_changeable"));
    }

    @Override
    protected Double convertFromUnitImp(Double value, UnitInfo fromUnit) {
        throw new RuntimeException(I18nHelper.t("error.combine_attribute_value_not_changeable"));
    }

	@Override
	public Double getValue() {
        Double sum = 0.0;
        for(AQAttribute attr : speAttrs){
            sum += attr.getValue();
        }
        return sum;
	}

    @Override
    public boolean changeDisplayUnit(UnitInfo newDisplayUnit){
        // 根据 nativeUnit 和 newUnit 的 unitClasses 判断是否要同class转换还是跨class转换
        // 判断是否允许设置的单位
        if(!unitChangeable || !attrClass.isValidUnit(newDisplayUnit)){
            return false;
        }
        displayUnit = newDisplayUnit;
        for(AQAttribute attr : speAttrs){
            attr.changeDisplayUnit(newDisplayUnit);
        }
        return true;
    }

    @Override
    protected CompletableFuture<Boolean> setDisplayValueImp(Double newDisplayValue, UnitInfo fromUnit){
        if(!valueChangeable){
            return CompletableFuture.completedFuture(false);
        }
        //  combine attribute value is not changeable
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public String getDisplayValue(UnitInfo toUnit){
        Double displayValue = 0.0;
        for(AQAttribute attr : speAttrs){
            if (attr.getDisplayValue(toUnit) == null) return null;
            displayValue += Double.parseDouble(attr.getDisplayValue(toUnit));
        }
        return NumberFormatter.formatValue(displayValue, displayPrecision);
    }

    @Override
    public ConfigDefinition getValueDefinition() {
        // AQ combine attributes typically don't need validation by default
        // Subclasses can override this to add specific validation rules
        return null;
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.aq_combine_attr.", "");
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.NUMERIC;
    }

    /**
     * 发布属性状态到数据总线
     *
     * 组合属性的值是通过子属性动态计算的，不会直接调用 updateValue()，因此 isValueUpdated 标志永远不会被设置。
     * 为了让组合属性能够正确推送到数据总线，此方法检查子属性是否有更新，如果有则先标记自己为已更新。
     * 
     * @apiNote 目前存在缺陷，需要先判断和推送此参数才生效，如果是先推送它关联的子参数，则因为子参数updated被还原为false导致本参数updated为false
     *
     * @return true if publishing succeeded, false otherwise
     */
    @Override
    public boolean publicState() {
        // 检查子属性是否有更新，如果有则标记组合属性需要推送
        for (AQAttribute attr : speAttrs) {
            if (attr.isValueUpdated()) {
                setValueUpdated(true);
                break;
            }
        }
        // 调用父类方法进行实际推送
        return super.publicState();
    }

}
