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

package com.ecat.core.Utils.Mdc;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.MDC;

/**
 * Logback TraceId 转换器
 *
 * <p>在日志格式中使用 %traceId 输出追踪 ID。
 *
 * <p>配置示例：
 * <pre>
 * &lt;conversionRule conversionWord="traceId" converterClass="com.ecat.core.Utils.Mdc.TraceIdConverter"/&gt;
 * &lt;property name="log.pattern" value="%d{HH:mm:ss} [%traceId] [%coordinate] %msg%n" /&gt;
 * </pre>
 *
 * @author coffee
 */
public class TraceIdConverter extends ClassicConverter {

    /**
     * 当没有 Trace ID 时显示的默认值
     */
    private static final String DEFAULT_TRACE_ID = "-";

    @Override
    public String convert(ILoggingEvent event) {
        // 首先从 MDC 获取
        String traceId = MDC.get(TraceContext.TRACE_ID_KEY);
        if (traceId == null || traceId.isEmpty()) {
            return DEFAULT_TRACE_ID;
        }
        // 返回 UUID 前 8 位
        return traceId.length() >= 8 ? traceId.substring(0, 8) : traceId;
    }
}
