package com.ecat.core.State.Unit;

/**
 * Air volume unit class
 *
 * @author coffee
 */
public enum AirVolumeUnit implements InternationalizedUnit {
    PPB("ppb", 1000.0), // 十亿分之一
    PPM("ppm", 1000.0 * 1000.0), // 百万分之一
    UMOL_PER_MOL("μmol/mol", 1000.0), // 微摩尔每摩尔
    NMOL_PER_MOL("nmol/mol", 1000.0 * 1000.0); // 纳摩尔每摩尔

    private final String name;
    private final Double ratio;

    AirVolumeUnit(String name, Double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "airvolume";
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
