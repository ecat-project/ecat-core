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

import java.util.Map;

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
        // 必须读事件自带 MDC 快照而非线程 ThreadLocal：文件通道全包 AsyncAppender，
        // 格式化发生在异步投递线程，该线程的 ThreadLocal MDC 恒为空（曾致生产 95k+ 行 [-] 空槽）。
        Map<String, String> mdc = event.getMDCPropertyMap();
        String traceId = mdc == null ? null : mdc.get(TraceContext.TRACE_ID_KEY);
        if (traceId == null || traceId.isEmpty()) {
            return DEFAULT_TRACE_ID;
        }
        // 26 字符 ULID 全量透传（不截断）：ULID 字典序即时间序，grep 完整 id 才能串起全链路
        return traceId;
    }
}
