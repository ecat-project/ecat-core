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
    INSUFFICIENT(103, "Insufficient", "有效数据不足"), // 统计状态
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
    CONVERSION_CHECK(126, "ConversionCheck", "转换效率检查"), // 仪器或人工设置或系统状态

    OFFLINE(0, "Offline", "离线"), // 系统状态
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
        // i18n 键 = 枚举常量名小写(如 over_upper_limit),对齐 strings.json state.status.* 键。
        // 必须用 name()(Enum 常量名 OVER_UPPER_LIMIT→over_upper_limit),不能用字段 name(camelCase
        // OverUpperLimit→overupperlimit)——后者多词状态全 miss 返原始键(修复 bug:告警列表类型列显示
        // state.status.overupperlimit 而非 超上限)。name().toLowerCase() 命中 strings.json 全 28 键。
        return I18nHelper.t("state.status." + name().toLowerCase());
    }

    /**
     * 多异常状态并存时的聚合优先级——数值越大越优先胜出作单 status（供卡片主标记 + 老 getStatus 下游）。
     * <p>方向（人干预优先）：P1 人/仪器判定需处理 > P2 受控测试态 > P3 仪器报警 > P4 自动超限 > P5 自动统计异常 > P6 数据态不佳 > P7 正常。
     * <p>同档（同 getPriority 值）并存时的二级仲裁由消费方在读时进行（如 ADM 监控读时合并按来源 MANUAL>POLL>LIMIT>STEADY），不在本方法。
     * <p>OFFLINE 是系统状态（设备离线由 online_status attr 单独管），故不列；落 default(30) 不抢主标记。
     */
    public int getPriority() {
        switch (this) {
            case MAINTENANCE:
            case DEVICE_REPLACEMENT:
            case MALFUNCTION:
                return 100;                                                                        // P1
            case CALIBRATION:
            case ZERO_CHECK:
            case SPAN_CHECK:
            case ACCURACY_CHECK:
            case ZERO_CALIBRATION:
            case SPAN_CALIBRATION:
            case FLOW_CHECK:
            case QUALITY_CHECK:
            case CONVERSION_CHECK:
            case ZERO_DRIFT:
            case SPAN_DRIFT:
            case SPAN_REPRODUCIBILITY:
            case MULTI_POINT_SPAN:
            case PRECISION_CHECK:
            case TEMP_PRESSURE_CALIBRATION:
                return 90;                                                                         // P2
            case ALARM:
                return 80;                                                                         // P3
            case OVER_UPPER_LIMIT:
            case UNDER_LOWER_LIMIT:
                return 70;                                                                         // P4
            case NO_CHANGE:
            case ABNORMAL_CHANGE:
                return 60;                                                                         // P5
            case INSUFFICIENT:
            case WAITING:
                return 50;                                                                         // P6
            case NORMAL:
                return 10;                                                                         // P7
            default:
                return 30; // OFFLINE/EMPTY 及未列出态：低于真实异常档，不抢主标记
        }
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

    /**
     * 根据数值 ID 获取枚举
     * @param id 状态 ID
     * @return 对应的 AttributeStatus，未找到返回 EMPTY
     */
    public static AttributeStatus fromId(int id) {
        for (AttributeStatus status : AttributeStatus.values()) {
            if (status.id == id) {
                return status;
            }
        }
        return EMPTY;
    }
}
