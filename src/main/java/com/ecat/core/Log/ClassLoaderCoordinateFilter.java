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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.MDC;
import org.slf4j.Marker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logger 名称坐标 TurboFilter
 *
 * <p>根据 Logger 名称自动设置 MDC 坐标，实现日志按集成分离。
 *
 * <p>核心原理：业务类通过 LogFactory.getLogger(this.getClass()) 获取 logger，
 * 所以 logger.getName() 就是业务类的全限定名。通过匹配已注册的包名前缀来确定坐标。
 *
 * <p>工作原理：
 * <ol>
 *   <li>获取 logger.getName() 作为业务类名</li>
 *   <li>在已注册的包名前缀中查找最长匹配</li>
 *   <li>将坐标设置到 MDC 中</li>
 *   <li>如果找不到匹配，使用默认 core</li>
 * </ol>
 *
 * <p>注册方式：{@link #registerPackagePrefix(String, String)}
 *
 * <p>配置示例：
 * <pre>
 * &lt;turboFilter class="com.ecat.core.Log.ClassLoaderCoordinateFilter"/&gt;
 * </pre>
 *
 * <p>新集成开发原则：
 * <ul>
 *   <li>业务集成：在 onLoad() 中注册业务包名前缀</li>
 *   <li>嵌入式框架集成（如 ruoyi）：同时注册框架包名前缀</li>
 * </ul>
 *
 * @author coffee
 */
public class ClassLoaderCoordinateFilter extends TurboFilter {
    private static final String MDC_COORDINATE_KEY = "integration.coordinate";

    // 包名前缀到坐标的映射
    private static final Map<String, String> packagePrefixCoordinateMap = new ConcurrentHashMap<>();

    private final AtomicLong processedCount = new AtomicLong(0L);
    private final AtomicLong mdcSetCount = new AtomicLong(0L);
    private boolean enableStats = false;
    private long statsInterval = 10000L;

    // Debug flag for troubleshooting log routing
    private static final boolean DEBUG_STACK_TRACE = Boolean.getBoolean("ecat.log.turbo.debug");
    private static final String DEBUG_LOGGER_NAMES = System.getProperty("ecat.log.turbo.debug.loggers", "");

    @Override
    public void start() {
        String statsProp = System.getProperty("ecat.log.turbo.stats", "false");
        enableStats = Boolean.parseBoolean(statsProp);
        String intervalProp = System.getProperty("ecat.log.turbo.stats.interval", "10000");
        try {
            statsInterval = Long.parseLong(intervalProp);
        } catch (NumberFormatException e) {
            statsInterval = 10000L;
        }
        super.start();
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }

        // 直接使用 logger 名称进行匹配
        // 原理：业务类通过 LogFactory.getLogger(this.getClass()) 获取 logger
        // 所以 logger.getName() 就是业务类的全限定名
        String loggerName = logger.getName();

        boolean shouldDebug = DEBUG_STACK_TRACE &&
            (DEBUG_LOGGER_NAMES.isEmpty() || loggerName.contains(DEBUG_LOGGER_NAMES) || DEBUG_LOGGER_NAMES.contains(loggerName));

        if (shouldDebug) {
            System.out.println("[ClassLoaderCoordinateFilter] CHECK logger: " + loggerName);
        }

        // 通过 logger 名称查找坐标（基于注册的包名前缀，最长匹配）
        String coordinate = lookupCoordinateByLoggerName(loggerName);

        if (coordinate != null) {
            MDC.put(MDC_COORDINATE_KEY, coordinate);
            mdcSetCount.incrementAndGet();
            if (shouldDebug) {
                System.out.println("[ClassLoaderCoordinateFilter] MATCH: " + loggerName + " -> " + coordinate);
            }
        } else {
            // 找不到匹配时，清除 MDC coordinate，使用默认 core
            MDC.remove(MDC_COORDINATE_KEY);
            if (shouldDebug) {
                System.out.println("[ClassLoaderCoordinateFilter] NO MATCH for logger: " + loggerName);
            }
        }

        if (enableStats) {
            long count = processedCount.incrementAndGet();
            if (count % statsInterval == 0L) {
                logStats();
            }
        }
        return FilterReply.NEUTRAL;
    }

    /**
     * 通过 Logger 名称查找坐标
     *
     * <p>直接基于已注册的前缀列表进行匹配，不限制包名格式。
     *
     * <p>使用最长匹配原则：在所有注册的前缀中，找到最长的匹配前缀。
     *
     * @param loggerName Logger 名称（通常是业务类的全限定名）
     * @return 坐标，如果未找到返回 null
     */
    private String lookupCoordinateByLoggerName(String loggerName) {
        String bestMatch = null;
        String bestPrefix = null;

        for (Map.Entry<String, String> entry : packagePrefixCoordinateMap.entrySet()) {
            String prefix = entry.getKey();
            if (loggerName.startsWith(prefix)) {
                if (bestPrefix == null || prefix.length() > bestPrefix.length()) {
                    bestPrefix = prefix;
                    bestMatch = entry.getValue();
                }
            }
        }
        return bestMatch;
    }

    /**
     * 输出统计信息
     */
    private void logStats() {
        System.out.println("[ClassLoaderCoordinateFilter Stats] processed=" + processedCount.get()
                + ", mdcSet=" + mdcSetCount.get()
                + ", registeredPackages=" + packagePrefixCoordinateMap.size());
    }

    @Override
    public void stop() {
        packagePrefixCoordinateMap.clear();
        super.stop();
    }

    /**
     * 注册包名前缀到坐标的映射
     *
     * <p>示例：
     * <pre>
     * // 业务集成
     * registerPackagePrefix("com.ecat.integration.SailheroIntegration", "com.ecat:integration-sailhero");
     *
     * // 嵌入式框架集成（如 ruoyi）
     * registerPackagePrefix("com.ruoyi", "com.ecat:integration-ecat-core-ruoyi");
     * registerPackagePrefix("org.springframework", "com.ecat:integration-ecat-core-ruoyi");
     * </pre>
     *
     * @param packagePrefix 包名前缀（如 "com.ecat.integration.SailheroIntegration"）
     * @param coordinate 坐标
     */
    public static void registerPackagePrefix(String packagePrefix, String coordinate) {
        if (packagePrefix == null || packagePrefix.isEmpty() || coordinate == null || coordinate.isEmpty()) {
            return;
        }
        packagePrefixCoordinateMap.put(packagePrefix, coordinate);
        System.out.println("[ClassLoaderCoordinateFilter] Registered PackagePrefix: "
                + packagePrefix + " -> " + coordinate);
    }

    /**
     * 注销包名前缀映射
     *
     * @param packagePrefix 包名前缀
     */
    public static void unregisterPackagePrefix(String packagePrefix) {
        if (packagePrefix == null || packagePrefix.isEmpty()) {
            return;
        }
        String removed = packagePrefixCoordinateMap.remove(packagePrefix);
        if (removed != null) {
            System.out.println("[ClassLoaderCoordinateFilter] Unregistered PackagePrefix: "
                    + packagePrefix + " (was " + removed + ")");
        }
    }

    /**
     * 获取已注册的包名前缀数量
     *
     * @return 数量
     */
    public static int getRegisteredPackagePrefixCount() {
        return packagePrefixCoordinateMap.size();
    }

    /**
     * 清除所有注册
     */
    public static void clearAll() {
        packagePrefixCoordinateMap.clear();
        System.out.println("[ClassLoaderCoordinateFilter] Cleared all registered PackagePrefixes");
    }
}
