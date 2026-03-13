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

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.*;

/**
 * DateTimeUtils 单元测试
 * <p>
 * 测试时区时间工具类的各种方法。
 */
public class DateTimeUtilsTest {

    // ==================== now() 测试 ====================

    @Test
    public void testNow_SystemDefaultZone() {
        ZonedDateTime now = DateTimeUtils.now();
        assertNotNull("当前时间不应为 null", now);
        assertTrue("当前时间应该接近系统时间",
            Math.abs(System.currentTimeMillis() - now.toInstant().toEpochMilli()) < 1000);
    }

    @Test
    public void testNow_WithZone() {
        ZonedDateTime utc = DateTimeUtils.now(ZoneOffset.UTC);
        assertNotNull("UTC 时间不应为 null", utc);
        assertEquals("时区应该是 UTC", ZoneOffset.UTC, utc.getZone());
    }

    @Test
    public void testNowUtc() {
        ZonedDateTime utc = DateTimeUtils.nowUtc();
        assertNotNull("UTC 时间不应为 null", utc);
        assertEquals("时区应该是 UTC", ZoneOffset.UTC, utc.getZone());
    }

    // ==================== format() 测试 ====================

    @Test
    public void testFormatIso() {
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 11, 12, 30, 45, 0, ZoneOffset.UTC);
        String formatted = DateTimeUtils.formatIso(time);
        assertNotNull("格式化结果不应为 null", formatted);
        assertTrue("格式化结果应该包含时间信息", formatted.contains("2026-03-11"));
        assertTrue("格式化结果应该是 ISO 格式", formatted.contains("T"));
    }

    @Test
    public void testFormat_WithPattern() {
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 11, 12, 30, 45, 0, ZoneOffset.UTC);
        String formatted = DateTimeUtils.format(time, DateTimeUtils.YYYY_MM_DD);
        assertEquals("2026-03-11", formatted);

        formatted = DateTimeUtils.format(time, DateTimeUtils.YYYY_MM_DD_HH_MM_SS);
        assertEquals("2026-03-11 12:30:45", formatted);

        formatted = DateTimeUtils.format(time, DateTimeUtils.YYYYMMDDHHMMSS);
        assertEquals("20260311123045", formatted);
    }

    @Test
    public void testFormat_NullInput() {
        String formatted = DateTimeUtils.formatIso(null);
        assertNull("null 输入应返回 null", formatted);

        formatted = DateTimeUtils.format(null, DateTimeUtils.YYYY_MM_DD);
        assertNull("null 输入应返回 null", formatted);
    }

    // ==================== parse() 测试 ====================

    @Test
    public void testParseIso() {
        String isoString = "2026-03-11T12:30:45Z";
        ZonedDateTime parsed = DateTimeUtils.parseIso(isoString);
        assertNotNull("解析结果不应为 null", parsed);
        assertEquals(2026, parsed.getYear());
        assertEquals(3, parsed.getMonthValue());
        assertEquals(11, parsed.getDayOfMonth());
        assertEquals(12, parsed.getHour());
        assertEquals(30, parsed.getMinute());
        assertEquals(45, parsed.getSecond());
    }

    @Test
    public void testParse_NullInput() {
        ZonedDateTime parsed = DateTimeUtils.parseIso(null);
        assertNull("null 输入应返回 null", parsed);

        parsed = DateTimeUtils.parse(null, DateTimeUtils.YYYY_MM_DD);
        assertNull("null 输入应返回 null", parsed);
    }

    @Test
    public void testParse_EmptyInput() {
        ZonedDateTime parsed = DateTimeUtils.parseIso("");
        assertNull("空字符串应返回 null", parsed);

        parsed = DateTimeUtils.parse("", DateTimeUtils.YYYY_MM_DD);
        assertNull("空字符串应返回 null", parsed);
    }

    // ==================== 时间计算测试 ====================

    @Test
    public void testBetween() {
        ZonedDateTime start = ZonedDateTime.of(2026, 3, 11, 12, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime end = ZonedDateTime.of(2026, 3, 11, 13, 30, 0, 0, ZoneOffset.UTC);

        Duration duration = DateTimeUtils.between(start, end);
        assertNotNull("持续时间不应为 null", duration);
        assertEquals(5400, duration.getSeconds()); // 1.5 小时 = 5400 秒
    }

    @Test
    public void testDaysBetween() {
        ZonedDateTime start = ZonedDateTime.of(2026, 3, 11, 12, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime end = ZonedDateTime.of(2026, 3, 14, 12, 0, 0, 0, ZoneOffset.UTC);

        long days = DateTimeUtils.daysBetween(start, end);
        assertEquals(3, days);
    }

    @Test
    public void testPlusDays() {
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 11, 12, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime result = DateTimeUtils.plusDays(time, 5);

        assertEquals(16, result.getDayOfMonth());
        assertEquals(3, result.getMonthValue());
    }

    @Test
    public void testPlusDays_NullInput() {
        ZonedDateTime result = DateTimeUtils.plusDays(null, 5);
        assertNull("null 输入应返回 null", result);
    }

    @Test
    public void testMinusDays() {
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 11, 12, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime result = DateTimeUtils.minusDays(time, 5);

        assertEquals(6, result.getDayOfMonth());
        assertEquals(3, result.getMonthValue());
    }

    @Test
    public void testMinusDays_NullInput() {
        ZonedDateTime result = DateTimeUtils.minusDays(null, 5);
        assertNull("null 输入应返回 null", result);
    }

    // ==================== 时区转换测试 ====================

    @Test
    public void testWithZone() {
        ZonedDateTime utc = ZonedDateTime.of(2026, 3, 11, 12, 0, 0, 0, ZoneOffset.UTC);
        ZoneId tokyoZone = ZoneId.of("Asia/Tokyo");

        ZonedDateTime tokyo = DateTimeUtils.withZone(utc, tokyoZone);

        assertNotNull("转换结果不应为 null", tokyo);
        assertEquals("时区应该是东京", tokyoZone, tokyo.getZone());
        assertEquals("小时应该是 21 (东京时间 UTC+9)", 21, tokyo.getHour());
    }

    @Test
    public void testWithZone_NullInput() {
        ZonedDateTime result = DateTimeUtils.withZone(null, ZoneOffset.UTC);
        assertNull("null 输入应返回 null", result);
    }

    @Test
    public void testToUtc() {
        ZoneId tokyoZone = ZoneId.of("Asia/Tokyo");
        ZonedDateTime tokyo = ZonedDateTime.of(2026, 3, 11, 21, 0, 0, 0, tokyoZone);

        ZonedDateTime utc = DateTimeUtils.toUtc(tokyo);

        assertNotNull("转换结果不应为 null", utc);
        assertEquals("时区应该是 UTC", ZoneOffset.UTC, utc.getZone());
        assertEquals("小时应该是 12 (UTC 时间)", 12, utc.getHour());
    }

    @Test
    public void testToUtc_NullInput() {
        ZonedDateTime result = DateTimeUtils.toUtc(null);
        assertNull("null 输入应返回 null", result);
    }

    // ==================== ISO 格式往返测试 ====================

    @Test
    public void testIsoRoundTrip() {
        ZonedDateTime original = ZonedDateTime.of(2026, 3, 11, 12, 30, 45, 0, java.time.ZoneOffset.UTC);
        String formatted = DateTimeUtils.formatIso(original);
        ZonedDateTime parsed = DateTimeUtils.parseIso(formatted);

        assertNotNull("解析结果不应为 null", parsed);
        assertEquals("往返后的时间应该等于原始时间", original.toInstant(), parsed.toInstant());
    }

    // ==================== 边界情况测试 ====================

    @Test
    public void testFormatWithDifferentZones() {
        ZonedDateTime utc = ZonedDateTime.of(2026, 3, 11, 12, 0, 0, 0, ZoneOffset.UTC);
        String utcFormatted = DateTimeUtils.formatIso(utc);

        ZonedDateTime tokyo = utc.withZoneSameInstant(ZoneId.of("Asia/Tokyo"));
        String tokyoFormatted = DateTimeUtils.formatIso(tokyo);

        assertNotNull("UTC 格式化不应为 null", utcFormatted);
        assertNotNull("东京格式化不应为 null", tokyoFormatted);
        assertNotEquals("不同时区的格式化结果应该不同", utcFormatted, tokyoFormatted);
    }
}
