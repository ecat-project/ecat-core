package com.ecat.core.Device;

import com.ecat.core.I18n.I18nHelper;

public enum DeviceStatus {
    
    STANDBY(0, "Standby"), // 待机
    NORMAL(1, "Normal"), // 正常工作，通用

    ZERO_CALIBRATION(11, "ReferenceZero"), // 零气校准
    ZERO(12, "Zero"), // 通零气体
    SPAN_CALIBRATION(13, "AutoCalibration"), // 标气校准
    SPAN(14, "Span"), // 通校准气体

    MEASURE(21, "Measure"), //测量
    CALIBRATION(22, "Calibration"), // 校准
    DIAGNOSTIC(23, "Diagnostic"), // 诊断
    RECOVERY(24, "Recovery"), // 恢复
    WARM_UP(25, "WarmUp"), // 热机

    ALARM(31, "Alarm"), // 报警
    MAINTENANCE(32, "Maintenance"), // 维护
    
    
    UNKNOWN(-1, "Unknown");

    private final int id;
    private final String statusName;

    DeviceStatus(int id, String statusName) {
        this.id = id;
        this.statusName = statusName;
    }

    public int getId() {
        return id;
    }

    public String getStatusName() {
        return I18nHelper.t("device.status." + statusName.toLowerCase());
    }
}
