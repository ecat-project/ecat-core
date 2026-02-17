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

package com.ecat.core.Log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.ecat.core.Const;
import com.ecat.core.Utils.Mdc.TraceContext;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Logback 广播 Appender
 *
 * <p>将日志事件广播到 LogManager，支持实时 SSE 推送。
 *
 * <p>配置示例：
 * <pre>
 * &lt;appender name="broadcast" class="com.ecat.core.Log.LogBroadcastAppender"/&gt;
 * </pre>
 *
 * @author coffee
 */
public class LogBroadcastAppender extends AppenderBase<ILoggingEvent> {
    private static final String MDC_COORDINATE_KEY = "integration.coordinate";
    // Debug flag for troubleshooting log routing
    private static final boolean DEBUG_BROADCAST = Boolean.getBoolean("ecat.log.broadcast.debug");
    // Debug specific logger names (comma-separated)
    private static final String DEBUG_LOGGER_NAMES = System.getProperty("ecat.log.broadcast.debug.loggers", "");

    @Override
    protected void append(ILoggingEvent event) {
        try {
            String coordinate = getCoordinate(event);
            String loggerName = event.getLoggerName();

            // Debug output for specific loggers
            boolean shouldDebug = DEBUG_BROADCAST &&
                (DEBUG_LOGGER_NAMES.isEmpty() || loggerName.contains(DEBUG_LOGGER_NAMES) || DEBUG_LOGGER_NAMES.contains(loggerName));
            if (shouldDebug) {
                String threadMdc = MDC.get(MDC_COORDINATE_KEY);
                String eventMdc = event.getMDCPropertyMap() != null ? event.getMDCPropertyMap().get(MDC_COORDINATE_KEY) : null;
                System.out.println("[LogBroadcastAppender] logger=" + loggerName
                    + " | threadMdc=" + threadMdc
                    + " | eventMdc=" + eventMdc
                    + " | resolved=" + coordinate
                    + " | message=" + event.getFormattedMessage());
            }

            LogManager logManager = LogManager.getInstance();
            if (!logManager.hasBuffer(coordinate)) {
                return;
            }
            String traceId = getTraceId(event);
            String level = event.getLevel().toString();
            String threadName = event.getThreadName();
            String message = event.getFormattedMessage();
            String throwable = null;
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null) {
                throwable = ThrowableProxyUtil.asString(throwableProxy);
            }
            LogEntry entry = new LogEntry(
                    event.getTimeStamp(),
                    traceId,
                    coordinate,
                    level,
                    loggerName,
                    threadName,
                    message,
                    throwable
            );
            logManager.broadcast(entry);
        } catch (Exception e) {
            addError("Failed to broadcast log entry", e);
        }
    }

    /**
     * 获取集成坐标
     *
     * <p>优先从 MDC 获取，其次从事件 MDC 获取，最后使用默认值。
     *
     * @param event 日志事件
     * @return 集成坐标
     */
    private String getCoordinate(ILoggingEvent event) {
        String coordinate = MDC.get(MDC_COORDINATE_KEY);
        if (coordinate != null && !coordinate.isEmpty()) {
            return coordinate;
        }
        Map<String, String> mdcMap = event.getMDCPropertyMap();
        if (mdcMap != null) {
            coordinate = mdcMap.get(MDC_COORDINATE_KEY);
            if (coordinate != null && !coordinate.isEmpty()) {
                return coordinate;
            }
        }
        return Const.CORE_COORDINATE;
    }

    /**
     * 获取 Trace ID
     *
     * <p>优先从 MDC 获取，其次从事件 MDC 获取。
     *
     * @param event 日志事件
     * @return Trace ID，如果不存在返回 null
     */
    private String getTraceId(ILoggingEvent event) {
        String traceId = MDC.get(TraceContext.TRACE_ID_KEY);
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }
        Map<String, String> mdcMap = event.getMDCPropertyMap();
        if (mdcMap != null) {
            traceId = mdcMap.get(TraceContext.TRACE_ID_KEY);
            if (traceId != null && !traceId.isEmpty()) {
                return traceId;
            }
        }
        return null;
    }
}
