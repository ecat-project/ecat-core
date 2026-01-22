package com.ecat.core.State.Unit;

/**
 * 时间差值单位类
 *
 * @author coffee
 */
public enum TimeDeltaUnit implements InternationalizedUnit {
    MILLISECOND("ms", 1.0),        // 毫秒
    SECOND("s", 1000.0),          // 秒
    MINUTE("min", 60000.0),       // 分
    HOUR("h", 3600000.0);         // 小时

    private final String name;
    private final double ratio;

    TimeDeltaUnit(String name, double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "time_delta";
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
