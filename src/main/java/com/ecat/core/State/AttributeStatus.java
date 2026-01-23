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
    NORMAL(1, "Normal", "数据有效"),
    
    ALARM(102, "Alarm", "传感器报警"),
    INSUFFICIENT(103, "Insufficient", "统计数据不足"),
    MAINTENANCE(104, "Maintenance", "维护"),
    MALFUNCTION(105, "Malfunction", "运行不良"),
    WAITING(106, "Waiting", "等待数据恢复"),
    CALIBRATION(107, "Calibration", "校准 (质控)"),
    ABNORMAL_CHANGE(108, "AbnormalChange", "数据突变"),
    NO_CHANGE(109, "NoChange", "数据不变"),
    OVER_UPPER_LIMIT(110, "OverUpperLimit", "超上限"),
    UNDER_LOWER_LIMIT(111, "UnderLowerLimit", "超下限"),
    ZERO_CHECK(112, "ZeroCheck", "零点检查"),
    SPAN_CHECK(113, "SpanCheck", "跨度检查"),
    ACCURACY_CHECK(114, "AccuracyCheck", "准确度检查"),
    ZERO_CALIBRATION(115, "ZeroCalibration", "零点校准"),
    SPAN_CALIBRATION(116, "SpanCalibration", "跨度校准"),
    FLOW_CHECK(117, "FlowCheck", "流量检查"),
    QUALITY_CHECK(118, "QualityCheck", "质量检查"),
    ZERO_DRIFT(119, "ZeroDrift", "检定零点漂移"),
    SPAN_DRIFT(120, "SpanDrift", "检定跨度漂移"),
    SPAN_REPRODUCIBILITY(121, "SpanReproducibility", "检定跨度重现性"),
    MULTI_POINT_SPAN(122, "MultiPointSpan", "检定多点跨度(线性)"),
    PRECISION_CHECK(123, "PrecisionCheck", "精密度检查"),

    OTHER(9999, "Other", "其他状态"), 
    EMPTY(-1, "Empty", "未设置");

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
