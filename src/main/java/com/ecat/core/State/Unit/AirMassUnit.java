package com.ecat.core.State.Unit;

/**
 * Air mass unit class
 *
 * @author coffee
 */
public enum AirMassUnit implements InternationalizedUnit {
    UGM3("ug/m3", 1000.0), // 微克每立方米
    MGM3("mg/m3", 1000.0 * 1000.0); // 毫克每立方米

    private final String name;
    private final Double ratio;

    AirMassUnit(String name, Double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "airmass";
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
