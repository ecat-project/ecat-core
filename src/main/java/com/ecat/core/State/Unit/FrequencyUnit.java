package com.ecat.core.State.Unit;

/**
 * 频率单位类
 *
 * @author coffee
 */
public enum FrequencyUnit implements InternationalizedUnit {
    HERTZ("Hz", 1.0), // 赫兹
    KILOHERTZ("kHz", 1000.0), // 千赫兹
    MEGAHERTZ("MHz", 1000000.0), // 兆赫兹
    GIGAHERTZ("GHz", 1000000000.0); // 吉赫兹

    private final String name;
    private final double ratio;

    FrequencyUnit(String name, double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "frequency";
    }

    @Override
    public String getEnumName() {
        return name().toLowerCase();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Double getRatio() {
        return ratio;
    }

    @Override
    public String toString() {
        return getName();
    }
}
