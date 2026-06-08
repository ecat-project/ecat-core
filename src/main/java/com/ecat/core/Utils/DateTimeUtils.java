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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 时区时间工具类
 * <p>
 * 统一处理时区时间，使用 {@link ZonedDateTime} 替代 {@link java.util.Date}。
 * 提供 singleton 时区管理，所有时间格式化通过 {@link TimeFormat} 枚举统一管理。
 *
 * @author coffee
 */
public class DateTimeUtils {

    // ==================== 时间格式枚举 ====================

    /**
     * 时间格式枚举。
     *
     * <p>统一管理系统中所有时间输出格式，禁止在业务代码中硬编码格式字符串。
     * 新增格式只需在此枚举中添加。
     */
    public enum TimeFormat {
        /** ISO 8601 带时区 (如 2026-06-04T16:30:00+08:00) — API 响应默认格式 */
        ISO("yyyy-MM-dd'T'HH:mm:ssXXX"),
        /** 日期时间 (如 2026-06-04 16:30:00) — 日志、UI 展示 */
        DATETIME("yyyy-MM-dd HH:mm:ss"),
        /** 仅日期 (如 2026-06-04) */
        DATE("yyyy-MM-dd"),
        /** 紧凑时间戳 (如 20260604163000) — 文件名、ID */
        COMPACT("yyyyMMddHHmmss");

        private final String pattern;
        TimeFormat(String pattern) { this.pattern = pattern; }
        /** 获取格式模式字符串 */
        public String getPattern() { return pattern; }
    }

    // ==================== 旧格式常量（保留向后兼容） ====================

    /** ISO 8601 格式 (带时区) */
    public static final String ISO_DATE_TIME = TimeFormat.ISO.getPattern();

    /** 常规格式 */
    public static final String YYYY_MM_DD = TimeFormat.DATE.getPattern();
    public static final String YYYY_MM_DD_HH_MM_SS = TimeFormat.DATETIME.getPattern();
    public static final String YYYYMMDDHHMMSS = TimeFormat.COMPACT.getPattern();

    // ==================== Singleton 时区 ====================

    /** ecat 系统时区，默认为 JVM 时区，启动时可通过 {@link #setZone(ZoneId)} 配置 */
    private static volatile ZoneId ecatZone = ZoneId.systemDefault();

    /**
     * 获取 ecat 系统时区。
     *
     * @return 当前系统时区
     */
    public static ZoneId getZone() {
        return ecatZone;
    }

    /**
     * 设置 ecat 系统时区（启动时可从配置加载）。
     *
     * @param zone 目标时区，null 则忽略
     */
    public static void setZone(ZoneId zone) {
        if (zone != null) {
            ecatZone = zone;
        }
    }

    // ==================== 当前时间 ====================

    /**
     * 获取当前时间 (ecat 系统时区)
     *
     * @return 当前时间
     */
    public static ZonedDateTime now() {
        return ZonedDateTime.now(ecatZone);
    }

    /**
     * 获取当前时间 (指定时区)
     *
     * @param zone 时区
     * @return 当前时间
     */
    public static ZonedDateTime now(ZoneId zone) {
        return ZonedDateTime.now(zone);
    }

    /**
     * 获取当前时间 (UTC)
     *
     * @return 当前 UTC 时间
     */
    public static ZonedDateTime nowUtc() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    // ==================== Instant 格式化 ====================

    /**
     * Instant → ecat 系统时区 ISO 字符串。
     * 等价于 {@code formatInstant(instant, TimeFormat.ISO)}。
     *
     * @param instant 时间对象，null 返回 null
     * @return ISO 格式字符串，如 "2026-06-04T16:30:00+08:00"
     */
    public static String formatInstant(Instant instant) {
        return formatInstant(instant, TimeFormat.ISO);
    }

    /**
     * Instant → ecat 系统时区 + 指定格式的字符串。
     *
     * @param instant 时间对象，null 返回 null
     * @param format  时间格式枚举，不能为 null
     * @return 格式化字符串，如 "2026-06-04T16:30:00+08:00"
     * @throws IllegalArgumentException 如果 format 为 null
     */
    public static String formatInstant(Instant instant, TimeFormat format) {
        if (instant == null) {
            return null;
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        return DateTimeFormatter.ofPattern(format.getPattern())
            .format(instant.atZone(ecatZone));
    }

    // ==================== ZonedDateTime 格式化 ====================

    /**
     * 格式化为 ISO 8601 字符串
     *
     * @param dateTime 时间对象
     * @return ISO 8601 格式字符串
     */
    public static String formatIso(ZonedDateTime dateTime) {
        return format(dateTime, TimeFormat.ISO.getPattern());
    }

    /**
     * 格式化为指定格式
     *
     * @param dateTime 时间对象
     * @param pattern  格式模式
     * @return 格式化后的字符串
     */
    public static String format(ZonedDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    // ==================== 解析 ====================

    /**
     * 解析 ISO 8601 字符串
     *
     * @param text ISO 8601 格式字符串
     * @return 时间对象
     */
    public static ZonedDateTime parseIso(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return ZonedDateTime.parse(text);
    }

    /**
     * 解析指定格式字符串
     *
     * @param text    格式化字符串
     * @param pattern 格式模式
     * @return 时间对象
     */
    public static ZonedDateTime parse(String text, String pattern) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return ZonedDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
    }

    // ==================== 时间计算 ====================

    /**
     * 计算两个时间之间的持续时间
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 持续时间
     */
    public static Duration between(ZonedDateTime start, ZonedDateTime end) {
        return Duration.between(start, end);
    }

    /**
     * 计算两个时间之间的天数
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 天数差
     */
    public static long daysBetween(ZonedDateTime start, ZonedDateTime end) {
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * 添加天数
     *
     * @param dateTime 时间对象
     * @param days     天数
     * @return 新的时间对象
     */
    public static ZonedDateTime plusDays(ZonedDateTime dateTime, long days) {
        return dateTime != null ? dateTime.plusDays(days) : null;
    }

    /**
     * 减少天数
     *
     * @param dateTime 时间对象
     * @param days     天数
     * @return 新的时间对象
     */
    public static ZonedDateTime minusDays(ZonedDateTime dateTime, long days) {
        return dateTime != null ? dateTime.minusDays(days) : null;
    }

    // ==================== 时区转换 ====================

    /**
     * 转换时区
     *
     * @param dateTime 时间对象
     * @param zone     目标时区
     * @return 新时区的时间对象
     */
    public static ZonedDateTime withZone(ZonedDateTime dateTime, ZoneId zone) {
        return dateTime != null ? dateTime.withZoneSameInstant(zone) : null;
    }

    /**
     * 转换为 UTC
     *
     * @param dateTime 时间对象
     * @return UTC 时间对象
     */
    public static ZonedDateTime toUtc(ZonedDateTime dateTime) {
        return withZone(dateTime, ZoneOffset.UTC);
    }

    // 私有构造函数，防止实例化
    private DateTimeUtils() {
    }
}
