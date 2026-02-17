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

import com.ecat.core.Log.LogEntry;
import com.ecat.core.Log.LogManager;
import com.ecat.core.Utils.Mdc.MdcCoordinateConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * 增强版日志包装器
 *
 * <p>扩展自 SLF4J Logger，增加以下功能：
 * <ul>
 *   <li>自动检测集成坐标</li>
 *   <li>支持 MDC 坐标传递</li>
 *   <li>支持日志广播到 SSE</li>
 *   <li>支持多种坐标模式</li>
 * </ul>
 *
 * <p>坐标模式（通过 ecat.log.coordinate.mode 配置）：
 * <ul>
 *   <li>LOG_FIRST - 优先使用 Log 实例坐标，其次 MDC（默认）</li>
 *   <li>MDC_FIRST - 优先使用 MDC 坐标，其次 Log 实例</li>
 *   <li>LOG_ONLY - 仅使用 Log 实例坐标</li>
 *   <li>MDC_ONLY - 仅使用 MDC 坐标</li>
 * </ul>
 */
public class Log implements Logger {
    private final Logger logger;
    private final String coordinate;

    private static CoordinateMode coordinateMode = CoordinateMode.LOG_FIRST;
    private static final String COORDINATE_MODE_PROPERTY = "ecat.log.coordinate.mode";
    private static final String CONFIG_FILE_PATH = ".ecat-data/core/config.yml";
    private static final String CORE_COORDINATE = com.ecat.core.Const.CORE_COORDINATE;

    static {
        coordinateMode = loadCoordinateMode();
    }

    /**
     * 加载坐标模式配置
     *
     * @return 坐标模式
     */
    private static CoordinateMode loadCoordinateMode() {
        // 1. 从系统属性读取
        String modeStr = System.getProperty(COORDINATE_MODE_PROPERTY);
        if (modeStr != null && !modeStr.isEmpty()) {
            try {
                return CoordinateMode.valueOf(modeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }

        // 2. 从配置文件读取
        try {
            Path configPath = Paths.get(System.getProperty("user.dir", "."), CONFIG_FILE_PATH);
            if (Files.exists(configPath)) {
                Map<String, Object> config = loadYamlConfig(configPath);
                if (config != null) {
                    Object loggingConfig = config.get("logging");
                    if (loggingConfig instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> logging = (Map<String, Object>) loggingConfig;
                        Object modeObj = logging.get("coordinate-mode");
                        if (modeObj != null) {
                            return CoordinateMode.valueOf(modeObj.toString().toUpperCase());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return CoordinateMode.LOG_FIRST;
    }

    /**
     * 加载 YAML 配置文件
     *
     * @param configPath 配置文件路径
     * @return 配置 Map
     */
    private static Map<String, Object> loadYamlConfig(Path configPath) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(configPath)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = yaml.load(in);
            return map;
        }
    }

    /**
     * 构造函数（仅名称）
     *
     * @param name 日志器名称
     */
    public Log(String name) {
        this.logger = LoggerFactory.getLogger(name);
        this.coordinate = null;
    }

    /**
     * 构造函数（按类）
     *
     * @param clazz 类
     */
    public Log(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.coordinate = detectCoordinate(clazz);
    }

    /**
     * 构造函数（名称+类）
     *
     * @param name 日志器名称
     * @param clazz 类（用于检测坐标）
     */
    public Log(String name, Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(name);
        this.coordinate = detectCoordinate(clazz);
    }

    /**
     * 检测坐标
     *
     * @param clazz 类
     * @return 坐标
     */
    private String detectCoordinate(Class<?> clazz) {
        if (clazz == null) {
            return CORE_COORDINATE;
        }
        String coord = IntegrationCoordinateHelper.getCoordinate(clazz);
        if (coord != null && !coord.isEmpty()) {
            return coord;
        }
        return CORE_COORDINATE;
    }

    /**
     * 获取有效坐标
     *
     * <p>根据坐标模式确定使用哪个坐标。
     *
     * @return 有效坐标
     */
    public String getEffectiveCoordinate() {
        String mdcCoordinate = MdcCoordinateConverter.getCoordinate();
        switch (coordinateMode) {
            case LOG_FIRST:
                return coordinate != null && !coordinate.isEmpty() ? coordinate
                        : (mdcCoordinate != null ? mdcCoordinate : CORE_COORDINATE);
            case MDC_FIRST:
                return mdcCoordinate != null && !mdcCoordinate.isEmpty() ? mdcCoordinate
                        : (coordinate != null ? coordinate : CORE_COORDINATE);
            case LOG_ONLY:
                return coordinate != null ? coordinate : CORE_COORDINATE;
            case MDC_ONLY:
                return mdcCoordinate != null ? mdcCoordinate : CORE_COORDINATE;
            default:
                return CORE_COORDINATE;
        }
    }

    /**
     * 获取 Log 实例坐标
     *
     * @return 坐标
     */
    public String getCoordinate() {
        return coordinate;
    }

    /**
     * 设置集成上下文（MDC 坐标）
     *
     * @param coordinate 坐标
     */
    public static void setIntegrationContext(String coordinate) {
        MdcCoordinateConverter.setCoordinate(coordinate);
    }

    /**
     * 清除集成上下文
     */
    public static void clearIntegrationContext() {
        MdcCoordinateConverter.clearCoordinate();
    }

    /**
     * 获取当前集成上下文
     *
     * @return 坐标
     */
    public static String getCurrentIntegrationContext() {
        return MdcCoordinateConverter.getCoordinate();
    }

    /**
     * 获取坐标模式
     *
     * @return 坐标模式
     */
    public static CoordinateMode getCoordinateMode() {
        return coordinateMode;
    }

    /**
     * 设置坐标模式
     *
     * @param mode 坐标模式
     */
    public static void setCoordinateMode(CoordinateMode mode) {
        if (mode != null) {
            coordinateMode = mode;
        }
    }

    /**
     * 广播日志到 SSE
     *
     * @param level 级别
     * @param message 消息
     * @param t 异常
     */
    private void broadcast(String level, String message, Throwable t) {
        try {
            String effectiveCoordinate = getEffectiveCoordinate();
            LogManager logManager = LogManager.getInstance();
            if (logManager.hasBuffer(effectiveCoordinate)) {
                String traceId = org.slf4j.MDC.get(com.ecat.core.Utils.Mdc.TraceContext.TRACE_ID_KEY);
                LogEntry entry = new LogEntry(
                        System.currentTimeMillis(),
                        traceId,
                        effectiveCoordinate,
                        level,
                        logger.getName(),
                        Thread.currentThread().getName(),
                        message,
                        t != null ? toString(t) : null
                );
                logManager.broadcast(entry);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 将异常转换为字符串
     *
     * @param t 异常
     * @return 字符串
     */
    private String toString(Throwable t) {
        if (t == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    // ========== 格式化方法 ==========

    private String format(String format, Object arg) {
        if (format == null) {
            return String.valueOf(arg);
        }
        if (arg == null) {
            return format.replaceFirst("\\{\\}", "null");
        }
        return format.replaceFirst("\\{\\}", Matcher.quoteReplacement(String.valueOf(arg)));
    }

    private String format(String format, Object arg1, Object arg2) {
        if (format == null) {
            return String.valueOf(arg1) + ", " + String.valueOf(arg2);
        }
        String result = format;
        result = result.replaceFirst("\\{\\}", Matcher.quoteReplacement(String.valueOf(arg1)));
        result = result.replaceFirst("\\{\\}", Matcher.quoteReplacement(String.valueOf(arg2)));
        return result;
    }

    private String format(String format, Object... arguments) {
        if (format == null) {
            return String.valueOf(Arrays.toString(arguments));
        }
        if (arguments == null || arguments.length == 0) {
            return format;
        }
        String result = format;
        for (Object arg : arguments) {
            result = result.replaceFirst("\\{\\}", Matcher.quoteReplacement(String.valueOf(arg)));
        }
        return result;
    }

    // ========== Logger 接口实现 ==========

    @Override
    public String getName() {
        return logger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return logger.isTraceEnabled(marker);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return logger.isDebugEnabled(marker);
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return logger.isInfoEnabled(marker);
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return logger.isWarnEnabled(marker);
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return logger.isErrorEnabled(marker);
    }

    // ========== TRACE 方法 ==========
    // broadcast 由 Logback LogBroadcastAppender 处理，避免重复

    @Override
    public void trace(String msg) {
        logger.trace(msg);
    }

    @Override
    public void trace(String format, Object arg) {
        logger.trace(format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        logger.trace(format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        logger.trace(format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        logger.trace(msg, t);
    }

    @Override
    public void trace(Marker marker, String msg) {
        logger.trace(marker, msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        logger.trace(marker, format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        logger.trace(marker, format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        logger.trace(marker, format, argArray);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        logger.trace(marker, msg, t);
    }

    // ========== DEBUG 方法 ==========
    // broadcast 由 Logback LogBroadcastAppender 处理，避免重复

    @Override
    public void debug(String msg) {
        logger.debug(msg);
    }

    @Override
    public void debug(String format, Object arg) {
        logger.debug(format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        logger.debug(format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        logger.debug(format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        logger.debug(msg, t);
    }

    @Override
    public void debug(Marker marker, String msg) {
        logger.debug(marker, msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        logger.debug(marker, format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        logger.debug(marker, format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... argArray) {
        logger.debug(marker, format, argArray);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        logger.debug(marker, msg, t);
    }

    // ========== INFO 方法 ==========
    // broadcast 由 Logback LogBroadcastAppender 处理，避免重复

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void info(String format, Object arg) {
        logger.info(format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        logger.info(format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        logger.info(format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        logger.info(msg, t);
    }

    @Override
    public void info(Marker marker, String msg) {
        logger.info(marker, msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        logger.info(marker, format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        logger.info(marker, format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        logger.info(marker, format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        logger.info(marker, msg, t);
    }

    // ========== WARN 方法 ==========
    // broadcast 由 Logback LogBroadcastAppender 处理，避免重复

    @Override
    public void warn(String msg) {
        logger.warn(msg);
    }

    @Override
    public void warn(String format, Object arg) {
        logger.warn(format, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        logger.warn(format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arguments) {
        logger.warn(format, arguments);
    }

    @Override
    public void warn(String msg, Throwable t) {
        logger.warn(msg, t);
    }

    @Override
    public void warn(Marker marker, String msg) {
        logger.warn(marker, msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        logger.warn(marker, format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        logger.warn(marker, format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... argArray) {
        logger.warn(marker, format, argArray);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        logger.warn(marker, msg, t);
    }

    // ========== ERROR 方法 ==========
    // broadcast 由 Logback LogBroadcastAppender 处理，避免重复

    @Override
    public void error(String msg) {
        logger.error(msg);
    }

    @Override
    public void error(String format, Object arg) {
        logger.error(format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        logger.error(format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        logger.error(format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        logger.error(msg, t);
    }

    @Override
    public void error(Marker marker, String msg) {
        logger.error(marker, msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        logger.error(marker, format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        logger.error(marker, format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        logger.error(marker, format, arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        logger.error(marker, msg, t);
    }

    /**
     * 坐标模式枚举
     */
    public enum CoordinateMode {
        /** 优先使用 Log 实例坐标，其次 MDC */
        LOG_FIRST,
        /** 优先使用 MDC 坐标，其次 Log 实例 */
        MDC_FIRST,
        /** 仅使用 Log 实例坐标 */
        LOG_ONLY,
        /** 仅使用 MDC 坐标 */
        MDC_ONLY
    }
}
