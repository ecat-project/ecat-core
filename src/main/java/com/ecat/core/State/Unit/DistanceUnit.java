package com.ecat.core.State.Unit;

public enum DistanceUnit implements InternationalizedUnit {
    MM("mm", 1.0), // 毫米
    CM("cm", 100.0), // 厘米
    M("m", 1000.0), // 米
    KM("km", 1000000.0); // 千米

    private final String name;
    private final Double ratio;

    DistanceUnit(String name, Double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "distance";
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
