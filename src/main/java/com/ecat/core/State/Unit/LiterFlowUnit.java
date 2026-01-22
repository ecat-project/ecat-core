package com.ecat.core.State.Unit;

/**
 * Liter flow unit class
 *
 * @author coffee
 */
public enum LiterFlowUnit implements InternationalizedUnit {
    L_PER_SECOND("L/s", 1.0), // 升每秒
    ML_PER_MINUTE("ml/min", 1.0 / 1000 * 60), // 毫升每分钟
    L_PER_MINUTE("L/min", 1.0 * 60), // 升每分钟
    L_PER_HOUR("L/h", 1.0 * 60 * 60); // 升每小时

    private final String name;
    private final Double ratio;

    LiterFlowUnit(String name, Double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "literflow";
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
