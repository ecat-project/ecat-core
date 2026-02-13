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

package com.ecat.core.Utils;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.ecat.core.Utils.Mdc.MdcCoordinateConverter;

/**
 * Logback 坐标转换器
 *
 * <p>在日志格式中使用 %coordinate 输出集成坐标。
 *
 * <p>配置示例：
 * <pre>
 * &lt;conversionRule conversionWord="coordinate" converterClass="com.ecat.core.Utils.CoordinateConverter"/&gt;
 * &lt;property name="log.pattern" value="%d{HH:mm:ss} [%coordinate] %msg%n" /&gt;
 * </pre>
 * 
 * @author coffee
 */
public class CoordinateConverter extends ClassicConverter {

    private static final String DEFAULT_COORDINATE = "core";

    @Override
    public String convert(ILoggingEvent event) {
        String coordinate = MdcCoordinateConverter.getCoordinate();
        if (coordinate == null || coordinate.isEmpty()) {
            return DEFAULT_COORDINATE;
        }
        return coordinate;
    }
}
