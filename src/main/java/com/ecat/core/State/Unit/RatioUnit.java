package com.ecat.core.State.Unit;

/**
 * 比例单位类
 *
 * @author coffee
 */
public enum RatioUnit implements InternationalizedUnit {
    PERCENT("%", 1.0),         // 百分比
    PER_MILLE("‰", 10.0);      // 千分比

    private final String name;
    private final double ratio;

    RatioUnit(String name, double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "ratio";
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
