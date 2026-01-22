package com.ecat.core.State.Unit;

/**
 * 功率单位类
 *
 * @author coffee
 */
public enum PowerUnit implements InternationalizedUnit {
    MILLIWATT("mW", 0.001), // 毫瓦
    WATT("W", 1.0), // 瓦
    KILOWATT("kW", 1000.0), // 千瓦
    MEGAWATT("MW", 1000000.0), // 兆瓦
    GIGAWATT("GW", 1000000000.0), // 吉瓦
    VOLT_AMPERE("VA", 1.0); // 伏特安

    private final String name;
    private final double ratio;

    PowerUnit(String name, double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "power";
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