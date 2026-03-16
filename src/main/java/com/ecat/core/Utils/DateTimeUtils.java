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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 时区时间工具类
 * <p>
 * 统一处理时区时间，使用 {@link ZonedDateTime} 替代 {@link java.util.Date}。
 *
 * @author coffee
 */
public class DateTimeUtils {

    // ==================== 格式常量 ====================

    /** ISO 8601 格式 (带时区) */
    public static final String ISO_DATE_TIME = "yyyy-MM-dd'T'HH:mm:ssXXX";

    /** 常规格式 */
    public static final String YYYY_MM_DD = "yyyy-MM-dd";
    public static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    public static final String YYYYMMDDHHMMSS = "yyyyMMddHHmmss";

    // ==================== 当前时间 ====================

    /**
     * 获取当前时间 (系统默认时区)
     *
     * @return 当前时间
     */
    public static ZonedDateTime now() {
        return ZonedDateTime.now();
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

    // ==================== 格式化 ====================

    /**
     * 格式化为 ISO 8601 字符串
     *
     * @param dateTime 时间对象
     * @return ISO 8601 格式字符串
     */
    public static String formatIso(ZonedDateTime dateTime) {
        return format(dateTime, ISO_DATE_TIME);
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
