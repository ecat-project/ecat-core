/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ecat.core.ConfigFlow.ConfigItem;

import org.junit.Test;

import com.ecat.core.ConfigFlow.ConfigItem.DateTimeConfigItem.Precision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * DateTimeConfigItem 单测：聚焦按精度的格式校验与前后端契约，
 * 含内嵌 {@link Precision} 枚举的序列化契约与回退语义。
 * <p>覆盖：默认精度（到秒）兼容现状、四种精度各自的接受/拒绝边界、
 * datetime-local 'T' 分隔符容忍、严格解析（拒绝非法日历日与尾随多余字符）、
 * required 语义与错误信息；精度码/值格式的前后端硬契约锁死与 fromCode 回退。
 *
 * @author dreamfalls
 */
public class DateTimeConfigItemTest {

    // ==================== 元信息 ====================

    @Test
    public void fieldTypeIsDatetimeAndDefaultPrecisionIsSecond() {
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", true);
        assertEquals("前端渲染类型契约", "datetime", item.getFieldType());
        assertEquals("默认精度=到秒（向后兼容）",
                DateTimeConfigItem.Precision.DATETIME_SECOND, item.getPrecision());
        assertEquals("FORMAT 常量=默认精度格式",
                DateTimeConfigItem.Precision.DATETIME_SECOND.getPattern(), DateTimeConfigItem.FORMAT);
    }

    @Test
    public void precisionSetterConfiguresPrecision() {
        DateTimeConfigItem item = new DateTimeConfigItem("start_date", true)
                .precision(DateTimeConfigItem.Precision.DATE);
        assertEquals(DateTimeConfigItem.Precision.DATE, item.getPrecision());
        // null 视为保持默认，不抛异常
        DateTimeConfigItem item2 = new DateTimeConfigItem("t", true).precision(null);
        assertEquals(DateTimeConfigItem.Precision.DATETIME_SECOND, item2.getPrecision());
    }

    // ==================== 默认精度（到秒） ====================

    @Test
    public void defaultPrecisionAcceptsSecondValue() {
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", true);
        assertNull(item.validate("2026-08-24 10:30:45"));
    }

    @Test
    public void defaultPrecisionToleratesTSeparator() {
        // datetime-local 原生控件提交值带 'T'，校验前归一为空格
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", true);
        assertNull(item.validate("2026-08-24T10:30:45"));
    }

    @Test
    public void defaultPrecisionRejectsMinuteValue() {
        // 缺秒不符合到秒精度（全串消费校验）
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", true);
        assertNotNull(item.validate("2026-08-24 10:30"));
    }

    // ==================== 分钟级 ====================

    @Test
    public void minutePrecisionAcceptsMinuteValue() {
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", true)
                .precision(DateTimeConfigItem.Precision.DATETIME_MINUTE);
        assertNull(item.validate("2026-08-24 10:30"));
        assertNull("同样容忍 'T' 分隔", item.validate("2026-08-24T10:30"));
    }

    @Test
    public void minutePrecisionRejectsSecondValue() {
        // 严格全串匹配：尾随的秒不能混过分钟级校验
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", true)
                .precision(DateTimeConfigItem.Precision.DATETIME_MINUTE);
        assertNotNull(item.validate("2026-08-24 10:30:45"));
    }

    // ==================== 仅日期 ====================

    @Test
    public void datePrecisionAcceptsDateOnly() {
        DateTimeConfigItem item = new DateTimeConfigItem("start_date", true)
                .precision(DateTimeConfigItem.Precision.DATE);
        assertNull(item.validate("2026-08-24"));
    }

    @Test
    public void datePrecisionRejectsDatetimeValue() {
        DateTimeConfigItem item = new DateTimeConfigItem("start_date", true)
                .precision(DateTimeConfigItem.Precision.DATE);
        assertNotNull(item.validate("2026-08-24 10:30"));
    }

    // ==================== 仅时间 ====================

    @Test
    public void timePrecisionAcceptsTimeOnly() {
        DateTimeConfigItem item = new DateTimeConfigItem("daily_at", true)
                .precision(DateTimeConfigItem.Precision.TIME);
        assertNull(item.validate("10:30:45"));
    }

    @Test
    public void timePrecisionRejectsDateValue() {
        DateTimeConfigItem item = new DateTimeConfigItem("daily_at", true)
                .precision(DateTimeConfigItem.Precision.TIME);
        assertNotNull(item.validate("2026-08-24"));
    }

    // ==================== 严格解析 ====================

    @Test
    public void rejectsInvalidCalendarDate() {
        // lenient=false：2 月 30 日非法
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", true);
        assertNotNull(item.validate("2026-02-30 10:30:45"));
    }

    @Test
    public void rejectsWrongFormat() {
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", true);
        assertNotNull(item.validate("24/08/2026 10:30:45"));
        assertNotNull("非文本类型报类型错", item.validate(20260824));
    }

    // ==================== required 与错误信息 ====================

    @Test
    public void requiredRejectsNullAndBlank() {
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", true)
                .displayName("开始时间");
        Object nullErr = item.validate(null);
        assertNotNull(nullErr);
        assertTrue("required 错误信息应含显示名", String.valueOf(nullErr).contains("开始时间"));
        assertNotNull("空白串同样视为缺失", item.validate("   "));
    }

    @Test
    public void optionalAcceptsNull() {
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", false);
        assertNull(item.validate(null));
    }

    @Test
    public void formatErrorContainsDisplayNameAndPattern() {
        DateTimeConfigItem item = new DateTimeConfigItem("start_time", true)
                .displayName("开始时间")
                .precision(DateTimeConfigItem.Precision.DATETIME_MINUTE);
        Object err = item.validate("非法值");
        assertNotNull(err);
        String msg = String.valueOf(err);
        assertTrue("错误信息应含显示名", msg.contains("开始时间"));
        assertTrue("错误信息应含期望格式", msg.contains("yyyy-MM-dd HH:mm"));
    }

    // ==================== Precision 枚举契约 ====================

    @Test
    public void precisionCodesAndPatternsAreContract() {
        // 四种精度的序列化码与值格式为前后端契约，锁死防回归
        assertEquals("date", Precision.DATE.getCode());
        assertEquals("yyyy-MM-dd", Precision.DATE.getPattern());

        assertEquals("datetime_minute", Precision.DATETIME_MINUTE.getCode());
        assertEquals("yyyy-MM-dd HH:mm", Precision.DATETIME_MINUTE.getPattern());

        assertEquals("datetime_second", Precision.DATETIME_SECOND.getCode());
        assertEquals("yyyy-MM-dd HH:mm:ss", Precision.DATETIME_SECOND.getPattern());

        assertEquals("time", Precision.TIME.getCode());
        assertEquals("HH:mm:ss", Precision.TIME.getPattern());
    }

    @Test
    public void precisionFromCodeResolvesAllCodes() {
        assertEquals(Precision.DATE, Precision.fromCode("date"));
        assertEquals(Precision.DATETIME_MINUTE, Precision.fromCode("datetime_minute"));
        assertEquals(Precision.DATETIME_SECOND, Precision.fromCode("datetime_second"));
        assertEquals(Precision.TIME, Precision.fromCode("time"));
    }

    @Test
    public void precisionFromCodeToleratesUppercaseEnumName() {
        // 容忍误传枚举 name()（大写），与前端 resolvePrecision 的小写归一同语义
        assertEquals(Precision.DATETIME_MINUTE, Precision.fromCode("DATETIME_MINUTE"));
        assertEquals(Precision.TIME, Precision.fromCode("TIME"));
    }

    @Test
    public void precisionFromCodeFallsBackForNullOrUnknown() {
        // null/未知码回退默认精度（到秒），兼容旧 schema 与旧副本，永不返回 null
        assertEquals(Precision.DATETIME_SECOND, Precision.fromCode(null));
        assertEquals(Precision.DATETIME_SECOND, Precision.fromCode(""));
        assertEquals(Precision.DATETIME_SECOND, Precision.fromCode("not_a_precision"));
    }

    @Test
    public void precisionHasDatePartOnlyFalseForTime() {
        assertTrue(Precision.DATE.hasDatePart());
        assertTrue(Precision.DATETIME_MINUTE.hasDatePart());
        assertTrue(Precision.DATETIME_SECOND.hasDatePart());
        assertFalse("TIME 仅时间，无日期部分（校验不做 'T' 归一）", Precision.TIME.hasDatePart());
    }
}
