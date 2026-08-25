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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * DateTimePrecision 单测：聚焦前后端序列化契约与回退语义。
 * <p>序列化码（code）与值格式（pattern）是前后端硬契约
 * （前端 config-flow lib 的 datetime 渲染器按同名小写码选控件），改动即破坏兼容，
 * 故用断言锁死四种精度的码与格式。
 *
 * @author dreamfalls
 */
public class DateTimePrecisionTest {

    @Test
    public void codesAndPatternsAreContract() {
        // 四种精度的序列化码与值格式为前后端契约，锁死防回归
        assertEquals("date", DateTimePrecision.DATE.getCode());
        assertEquals("yyyy-MM-dd", DateTimePrecision.DATE.getPattern());

        assertEquals("datetime_minute", DateTimePrecision.DATETIME_MINUTE.getCode());
        assertEquals("yyyy-MM-dd HH:mm", DateTimePrecision.DATETIME_MINUTE.getPattern());

        assertEquals("datetime_second", DateTimePrecision.DATETIME_SECOND.getCode());
        assertEquals("yyyy-MM-dd HH:mm:ss", DateTimePrecision.DATETIME_SECOND.getPattern());

        assertEquals("time", DateTimePrecision.TIME.getCode());
        assertEquals("HH:mm:ss", DateTimePrecision.TIME.getPattern());
    }

    @Test
    public void fromCodeResolvesAllCodes() {
        assertEquals(DateTimePrecision.DATE, DateTimePrecision.fromCode("date"));
        assertEquals(DateTimePrecision.DATETIME_MINUTE, DateTimePrecision.fromCode("datetime_minute"));
        assertEquals(DateTimePrecision.DATETIME_SECOND, DateTimePrecision.fromCode("datetime_second"));
        assertEquals(DateTimePrecision.TIME, DateTimePrecision.fromCode("time"));
    }

    @Test
    public void fromCodeToleratesUppercaseEnumName() {
        // 容忍误传枚举 name()（大写），与前端 resolvePrecision 的小写归一同语义
        assertEquals(DateTimePrecision.DATETIME_MINUTE, DateTimePrecision.fromCode("DATETIME_MINUTE"));
        assertEquals(DateTimePrecision.TIME, DateTimePrecision.fromCode("TIME"));
    }

    @Test
    public void fromCodeFallsBackForNullOrUnknown() {
        // null/未知码回退默认精度（到秒），兼容旧 schema 与旧副本，永不返回 null
        assertEquals(DateTimePrecision.DATETIME_SECOND, DateTimePrecision.fromCode(null));
        assertEquals(DateTimePrecision.DATETIME_SECOND, DateTimePrecision.fromCode(""));
        assertEquals(DateTimePrecision.DATETIME_SECOND, DateTimePrecision.fromCode("not_a_precision"));
    }

    @Test
    public void hasDatePartOnlyFalseForTime() {
        assertTrue(DateTimePrecision.DATE.hasDatePart());
        assertTrue(DateTimePrecision.DATETIME_MINUTE.hasDatePart());
        assertTrue(DateTimePrecision.DATETIME_SECOND.hasDatePart());
        assertFalse("TIME 仅时间，无日期部分（校验不做 'T' 归一）", DateTimePrecision.TIME.hasDatePart());
    }
}
