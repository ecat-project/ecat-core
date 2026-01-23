/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.Device;

import com.ecat.core.I18n.I18nHelper;

/**
 * Enum for device status management
 * 
 * @author coffee
 */
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
