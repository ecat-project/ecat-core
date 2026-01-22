package com.ecat.core.State.Unit;

/**
 * Voltage unit class
 *
 * @author coffee
 */
public enum VoltageUnit implements InternationalizedUnit {
    MILLIVOLT("mV", 1.0), // 毫伏
    VOLT("V", 1.0 * 1000), // 伏特
    HERTZ("Hz", 1.0); // 赫兹

    private final String name;
    private final Double ratio;

    VoltageUnit(String name, Double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "voltage";
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