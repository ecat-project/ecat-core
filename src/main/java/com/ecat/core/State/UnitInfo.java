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
