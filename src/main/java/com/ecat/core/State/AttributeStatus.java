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

package com.ecat.core.State;

import com.ecat.core.I18n.I18nHelper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum AttributeStatus {
    NORMAL(1, "Normal", "数据有效"), // 仪器或人工设置状态
    
    ALARM(102, "Alarm", "传感器报警"), // 仪器状态
    INSUFFICIENT(103, "Insufficient", "统计数据不足"), // 统计状态
    MAINTENANCE(104, "Maintenance", "维护"), // 仪器或人工设置状态
    MALFUNCTION(105, "Malfunction", "运行不良"), // 仪器或人工设置状态
    WAITING(106, "Waiting", "等待数据恢复"), // 仪器状态
    CALIBRATION(107, "Calibration", "校准 (质控)"), // 仪器状态
    ABNORMAL_CHANGE(108, "AbnormalChange", "数据突变"),  // 统计状态
    NO_CHANGE(109, "NoChange", "数据不变"), // 统计状态
    OVER_UPPER_LIMIT(110, "OverUpperLimit", "超上限"), // 统计状态
    UNDER_LOWER_LIMIT(111, "UnderLowerLimit", "超下限"), // 统计状态
    ZERO_CHECK(112, "ZeroCheck", "零点检查"), // 仪器或人工设置或系统状态
    SPAN_CHECK(113, "SpanCheck", "跨度检查"), // 仪器或人工设置或系统状态
    ACCURACY_CHECK(114, "AccuracyCheck", "准确度检查"), // 仪器或人工设置或系统状态
    ZERO_CALIBRATION(115, "ZeroCalibration", "零点校准"), // 仪器或人工设置或系统状态
    SPAN_CALIBRATION(116, "SpanCalibration", "跨度校准"), // 仪器或人工设置或系统状态
    FLOW_CHECK(117, "FlowCheck", "流量检查"), // 仪器或人工设置或系统状态
    QUALITY_CHECK(118, "QualityCheck", "质量检查"), // 仪器或人工设置或系统状态
    ZERO_DRIFT(119, "ZeroDrift", "检定零点漂移"), // 人工设置状态
    SPAN_DRIFT(120, "SpanDrift", "检定跨度漂移"), // 人工设置状态
    SPAN_REPRODUCIBILITY(121, "SpanReproducibility", "检定跨度重现性"), // 人工设置状态
    MULTI_POINT_SPAN(122, "MultiPointSpan", "检定多点跨度(线性)"), // 多点线性检查 仪器或人工设置或系统状态
    PRECISION_CHECK(123, "PrecisionCheck", "精密度检查"), // 仪器或人工设置或系统状态
    TEMP_PRESSURE_CALIBRATION(124, "TempPressureCalibration", "温度压力校准"),  // 人工设置状态
    DEVICE_REPLACEMENT(125, "DeviceReplacement", "维修更换设备"),  // 人工设置状态

    @Deprecated
    OTHER(9999, "Other", "其他状态"),  // 不要用此属性，已废弃（2026-03-01后将删除）
    EMPTY(-1, "Empty", "未设置"); // 系统初始

    private final int id;
    private final String name;
    
    AttributeStatus(int id, String name, String description) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return I18nHelper.t("state.status." + name.toLowerCase());
    }

    public static List<String> getNames() {
        return Arrays.stream(AttributeStatus.values())
                .map(AttributeStatus::getName)
                .collect(Collectors.toList());
    }
    // 根据输入的name获取enum
    public static AttributeStatus getEnum(String className) {
        if (className == null) {
            return EMPTY;
        }
        for (AttributeStatus attributeStatus : AttributeStatus.values()) {
            if (attributeStatus.getName().equals(className)) {
                return attributeStatus;
            }
        }
        throw new IllegalArgumentException("AttributeStatus not found: " + className);
    }
}
