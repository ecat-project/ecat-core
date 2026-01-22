package com.ecat.core.State.Unit;

/**
 * 速度单位类
 *
 * @author coffee
 */
public enum NoiseUnit implements InternationalizedUnit {
    DB("dB", 1.0); // 分贝


    private final String name;
    private final double ratio;

    NoiseUnit(String name, double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "noise";
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
