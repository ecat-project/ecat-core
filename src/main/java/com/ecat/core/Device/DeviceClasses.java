package com.ecat.core.Device;

import com.ecat.core.I18n.I18nHelper;

public enum DeviceClasses {
    
    AIR_MONITOR_PM("air.monitor.pm"),
    AIR_MONITOR_PM_WEIGHT("air.monitor.pm.weight"), // 重量采集器
    AIR_MONITOR_PM_QC("air.monitor.pm.qc"), // 颗粒物质控
    AIR_MONITOR_SO2("air.monitor.so2"),
    AIR_MONITOR_NO2("air.monitor.no2"),
    AIR_MONITOR_CO("air.monitor.co"),
    AIR_MONITOR_O3("air.monitor.o3"),
    AIR_MONITOR_CALIBRATOR("air.monitor.calibrator"), // 校准仪
    AIR_MONITOR_QC("air.monitor.qc"), // 质控仪
    AIR_CONDITIONER("air.conditioner"), // 空调控制器
    WEATHER_SENSOR("weather.sensor"), // 气象传感器

    POWER_SUPPLY_STABILIZER("power.supply.stabilizer"), // 稳压电源
    POWER_SENSOR("power.sensor"), // 电表
    POWER_UPS("power.ups"), // UPS

    SAMPLE_TUBE("sample.tube"), // 采样管

    IO_DAM("io.dam"), // IO通信-采集单元
    AIR_FILTER_MEMBRANE("air.filter.membrane"),  // 换膜器
    AIR_QUALITY_DETECTOR("air.quality.detector"), // （复合型）空气质量检测仪

    DEFAULT_SENSOR("default.sensor"); // 默认的传感器类，可作为仅显示值没有控制功能的设备使用



    
    private final String className;   // 参数名称，英文
    DeviceClasses(String className) {
        this.className = className;
    }
    public String getClassName() {
        return className;
    }

    // 获取显示名称
    public String getDisplayName() {
        return I18nHelper.t("device.type." + className);
    }

    // 根据输入的name获取enum
    public static DeviceClasses getEnum(String className) {
        if (className == null) {
            return DEFAULT_SENSOR;
        }
        for (DeviceClasses deviceClass : DeviceClasses.values()) {
            if (deviceClass.getClassName().equals(className)) {
                return deviceClass;
            }
        }
        throw new IllegalArgumentException("DeviceClasses not found: " + className);
    }

}
