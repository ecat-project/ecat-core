package com.ecat.core.State.Unit;

import com.ecat.core.State.UnitInfo;

/**
 * Unit class for units that do not require conversion.
 * 
 * @example
 * <pre>{@code
 * NoConversionUnit unit = NoConversionUnit.of("unitless", "Unitless");
 * }</pre>
 * 
 * @author coffee
 */
public class NoConversionUnit implements UnitInfo {
    
    private String name;   // 参数名称，英文
    private String displayName; // 支持markdown标记如上下标

    NoConversionUnit(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * 获取单位的显示名称，非必要不要使用此方法，会影响后面i18n
     * 数据存储持久化都使用name作为key更稳定
     */
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Double getRatio() {
        return 1.0; // No conversion ratio, always returns 1.0
    }

    @Override
    public String toString(){
        return getName();
    }

    @Override
    public String getFullUnitString() {
        return "NoConversionUnit." + getName(); // NoConversionUnit 要与类名保持一致，会影响数据持久化，不要轻易修改
    }

    /**
     * Creates a NoConversionUnit instance with the specified name and display name.
     * @param name
     * @param displayName
     * @return
     */
    public static NoConversionUnit of(String name, String displayName) {
        return new NoConversionUnit(name, displayName);
    }

    /**
     * Creates a NoConversionUnit instance with the same name and display name.
     * @param nameAndDisplay
     * @return
     */
    public static NoConversionUnit of(String nameAndDisplay) {
        return new NoConversionUnit(nameAndDisplay, nameAndDisplay);
    }
}
