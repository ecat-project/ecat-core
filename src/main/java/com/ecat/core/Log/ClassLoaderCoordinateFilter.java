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
 * ClassLoader 坐标 TurboFilter
 *
 * <p>根据调用者的 ClassLoader 自动设置 MDC 坐标，实现日志按集成分离。
 *
 * <p>工作原理：
 * <ol>
 *   <li>获取调用栈中第一个非日志框架类的 ClassLoader</li>
 *   <li>查找该 ClassLoader 注册的坐标</li>
 *   <li>将坐标设置到 MDC 中</li>
 * </ol>
 *
 * <p>配置示例：
 * <pre>
 * &lt;turboFilter class="com.ecat.core.Log.ClassLoaderCoordinateFilter"/&gt;
 * </pre>
 *
 * <p>JVM 参数：
 * <ul>
 *   <li>ecat.log.turbo.stats - 是否启用统计（默认 false）</li>
 *   <li>ecat.log.turbo.stats.interval - 统计间隔（默认 10000）</li>
 * </ul>
 * 
 * @author coffee
 */
public class ClassLoaderCoordinateFilter extends TurboFilter {
    private static final String MDC_COORDINATE_KEY = "integration.coordinate";
    private static final Map<Integer, String> classLoaderCoordinateMap = new ConcurrentHashMap<>();
    private static final Map<Integer, ClassLoader> classLoaderRefs = new ConcurrentHashMap<>();
    private final AtomicLong processedCount = new AtomicLong(0L);
    private final AtomicLong mdcSetCount = new AtomicLong(0L);
    private boolean enableStats = false;
    private long statsInterval = 10000L;

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
        if (classLoaderCoordinateMap.isEmpty()) {
            return FilterReply.NEUTRAL;
        }
        ClassLoader callerClassLoader = getCallerClassLoader();
        if (callerClassLoader == null) {
            return FilterReply.NEUTRAL;
        }
        int loaderId = System.identityHashCode(callerClassLoader);
        String coordinate = classLoaderCoordinateMap.get(loaderId);
        if (coordinate != null) {
            MDC.put(MDC_COORDINATE_KEY, coordinate);
            mdcSetCount.incrementAndGet();
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
     * 获取调用者的 ClassLoader
     *
     * <p>遍历调用栈，找到第一个非日志框架类的 ClassLoader。
     *
     * @return ClassLoader
     */
    private ClassLoader getCallerClassLoader() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            String className = element.getClassName();
            if (isLogFrameworkClass(className)) {
                continue;
            }
            try {
                ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
                Class<?> clazz = Class.forName(className, false, contextLoader);
                return clazz.getClassLoader();
            } catch (ClassNotFoundException e) {
                return Thread.currentThread().getContextClassLoader();
            }
        }
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * 检查是否为日志框架类
     *
     * @param className 类名
     * @return 是否为日志框架类
     */
    private boolean isLogFrameworkClass(String className) {
        return className.startsWith("ch.qos.logback.")
                || className.startsWith("org.slf4j.")
                || className.startsWith("com.ecat.core.Log.")
                || className.startsWith("com.ecat.core.Utils.Log")
                || className.startsWith("com.ecat.core.Utils.CallerDataConverter")
                || className.startsWith("com.ecat.core.Utils.CoordinateConverter")
                || className.equals("java.lang.Thread");
    }

    /**
     * 输出统计信息
     */
    private void logStats() {
        System.out.println("[ClassLoaderCoordinateFilter Stats] processed=" + processedCount.get()
                + ", mdcSet=" + mdcSetCount.get()
                + ", registeredLoaders=" + classLoaderCoordinateMap.size());
    }

    @Override
    public void stop() {
        classLoaderCoordinateMap.clear();
        classLoaderRefs.clear();
        super.stop();
    }

    /**
     * 注册 ClassLoader 坐标映射
     *
     * @param classLoader ClassLoader
     * @param coordinate 坐标
     */
    public static void registerClassLoader(ClassLoader classLoader, String coordinate) {
        if (classLoader == null || coordinate == null || coordinate.isEmpty()) {
            return;
        }
        int loaderId = System.identityHashCode(classLoader);
        classLoaderCoordinateMap.put(loaderId, coordinate);
        classLoaderRefs.put(loaderId, classLoader);
        System.out.println("[ClassLoaderCoordinateFilter] Registered ClassLoader: "
                + classLoader.getClass().getName() + "@" + loaderId + " -> " + coordinate);
    }

    /**
     * 注销 ClassLoader 坐标映射
     *
     * @param classLoader ClassLoader
     */
    public static void unregisterClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        int loaderId = System.identityHashCode(classLoader);
        String removed = classLoaderCoordinateMap.remove(loaderId);
        classLoaderRefs.remove(loaderId);
        if (removed != null) {
            System.out.println("[ClassLoaderCoordinateFilter] Unregistered ClassLoader: "
                    + classLoader.getClass().getName() + "@" + loaderId + " (was " + removed + ")");
        }
    }

    /**
     * 获取已注册的 ClassLoader 数量
     *
     * @return 数量
     */
    public static int getRegisteredLoaderCount() {
        return classLoaderCoordinateMap.size();
    }

    /**
     * 检查 ClassLoader 是否已注册
     *
     * @param classLoader ClassLoader
     * @return 是否已注册
     */
    public static boolean isRegistered(ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        int loaderId = System.identityHashCode(classLoader);
        return classLoaderCoordinateMap.containsKey(loaderId);
    }

    /**
     * 获取 ClassLoader 的坐标
     *
     * @param classLoader ClassLoader
     * @return 坐标
     */
    public static String getCoordinate(ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        int loaderId = System.identityHashCode(classLoader);
        return classLoaderCoordinateMap.get(loaderId);
    }

    /**
     * 清除所有注册
     */
    public static void clearAll() {
        classLoaderCoordinateMap.clear();
        classLoaderRefs.clear();
        System.out.println("[ClassLoaderCoordinateFilter] Cleared all registered ClassLoaders");
    }
}
