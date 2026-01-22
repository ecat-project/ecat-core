package com.ecat.core.State.Unit;

/**
 * Weight unit class
 *
 * @author coffee
 */
public enum WeightUnit implements InternationalizedUnit {
    UG("ug", 1.0), // 微克
    MG("mg", 1000.0), // 毫克
    G("g", 1000.0 * 1000.0), // 克
    KG("kg", 1000.0 * 1000.0 * 1000.0), // 千克
    T("t", 1000.0 * 1000.0 * 1000.0 * 1000.0); // 吨

    private final String name;
    private final Double ratio;

    WeightUnit(String name, Double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "weight";
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