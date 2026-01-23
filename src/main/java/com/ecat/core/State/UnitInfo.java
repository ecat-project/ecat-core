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

/**
 * UnitInfo interface for defining unit information and conversion methods.
 * This interface is used to represent units of measurement, such as air mass or air volume,
 * and provides methods for unit conversion.
 * 
 * @author coffee
 */
public interface UnitInfo {
    String getName();
    String getDisplayName();
    Double getRatio();
    /**
     * 计算两个相同的单位之间的转换比率
     * @param desUnitInfo
     * @return
     */
    default Double convertUnit(UnitInfo desUnitInfo) {
        if(!desUnitInfo.getClass().equals(this.getClass())){
            throw new IllegalArgumentException("Cannot convert between different unit classes: " + this.getClass().getName() + " and " + desUnitInfo.getClass().getName());
        }
        if(this.getRatio() != null && desUnitInfo.getRatio() != null){
            return Double.valueOf( this.getRatio() / desUnitInfo.getRatio());
        }
        return null;
    }
    // 103 * AirMassUnit.UGM3.convertUnit(AirMassUnit.MGM3)

    String toString();

    /**
     * 获取完整的单位字符串，格式为：枚举类名.枚举常量名
     * 例如：AirVolumeUnit.PPM
     * @return 完整的单位字符串，如 NoConversionUnit.of("custom_unit")返回"NoConversionUnit.custom_unit"
     */
    default String getFullUnitString() {
        if (this instanceof Enum) {
            Enum<?> enumUnit = (Enum<?>) this;
            return enumUnit.getDeclaringClass().getSimpleName() + "." + enumUnit.name();
        }
        return getName();
    }
}
