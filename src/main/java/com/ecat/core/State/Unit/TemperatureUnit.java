package com.ecat.core.State.Unit;

/**
 * Temperature unit class
 *
 * @author coffee
 */
public enum TemperatureUnit implements InternationalizedUnit {
    CELSIUS("°C", 1.0); // 摄氏度

    private final String name;
    private final Double ratio;

    TemperatureUnit(String name, Double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "temperature";
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
