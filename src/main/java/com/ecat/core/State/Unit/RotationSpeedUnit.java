package com.ecat.core.State.Unit;

/**
 * 转速单位类
 *
 * @author coffee
 */
public enum RotationSpeedUnit implements InternationalizedUnit {
    RPM("rpm", 1.0),    // 转/分钟
    KRPM("krpm", 1000.0); // 千转/分钟

    private final String name;
    private final double ratio;

    RotationSpeedUnit(String name, double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "rotation_speed";
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
