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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TextConfigItem 单测：聚焦 required 字段对空白串的校验，及非必填空串放行语义。
 * <p>历史背景一：{@code AbstractConfigItem.validate} 的 required 原只查 {@code null}，
 * 空串 {@code ""} 能绕过（API/脚本 POST 空 sn 可成）——见 bug-record-20260724-084832。
 * 收紧后 required 字段 null 与空白串均视为缺失。
 * <p>历史背景二：非必填文本字段原只对 null 跳过校验，前端空文本框提交的 "" 会撞上
 * minLength 校验，「可不填」承诺失效（bug-record-20261001-230645）。
 * 修复后非必填 + 完全空串与 null 同等放行。
 *
 * @author coffee
 */
public class TextConfigItemTest {

    @Test
    public void requiredRejectsEmptyString() {
        TextConfigItem sn = new TextConfigItem("sn", true).displayName("序列号");
        Object result = sn.validate("");
        assertNotNull("required 字段空串应报必需（不再放行）", result);
        assertTrue("错误信息应含'必需'", String.valueOf(result).contains("必需"));
    }

    @Test
    public void requiredRejectsBlankString() {
        TextConfigItem sn = new TextConfigItem("sn", true).displayName("序列号");
        Object result = sn.validate("   ");
        assertNotNull("required 字段纯空白串应报必需", result);
    }

    @Test
    public void requiredAcceptsNonEmpty() {
        TextConfigItem sn = new TextConfigItem("sn", true).displayName("序列号");
        assertNull("required 字段非空应通过", sn.validate("SN001"));
    }

    @Test
    public void optionalAcceptsEmptyString() {
        // 非必填 + 空串：不报 required 错（行为保留，空串=未提供）
        TextConfigItem note = new TextConfigItem("note", false).displayName("备注");
        assertNull("非必填字段空串应放行", note.validate(""));
    }

    @Test
    public void optionalWithMinLengthAcceptsEmptyString() {
        // bug-record-20261001-230645：前端对空文本框提交 "" 而非省略键/null，
        // 非必填 + length(1,50) 的字段（如 sailhero-air station_name「可不填」）留空提交
        // 不应撞 minLength——空串语义=未填，与 null 同等放行。
        TextConfigItem stationName = new TextConfigItem("station_name", false, "")
                .displayName("点位名称")
                .length(1, 50);
        assertNull("非必填 + 空串应放行（空串=未填）", stationName.validate(""));
    }

    @Test
    public void optionalWithMinLengthAcceptsNull() {
        TextConfigItem stationName = new TextConfigItem("station_name", false, "")
                .displayName("点位名称")
                .length(1, 50);
        assertNull("非必填 + null 应放行", stationName.validate(null));
    }

    @Test
    public void optionalWithMinLengthStillValidatesNonEmpty() {
        // 放行仅针对完全空串（长度 0，不 trim）：非空值仍受 length 约束。
        TextConfigItem stationName = new TextConfigItem("station_name", false, "")
                .displayName("点位名称")
                .length(1, 5);
        assertNull("长度合法的非空值应通过", stationName.validate("abc"));
        Object tooLong = stationName.validate("123456");
        assertNotNull("超长非空值仍应被 length 拒绝", tooLong);
        assertTrue("错误信息应为 length 校验错误", String.valueOf(tooLong).contains("文本长度"));
    }

    @Test
    public void requiredWithMinLengthStillRejectsEmptyString() {
        // 锁 required 侧：required + 空串不得借「空串=未填」放行绕过校验
        // （bug-record-20260724-084832 的 POST 空 sn 口子不回潮）。
        TextConfigItem sn = new TextConfigItem("sn", true)
                .displayName("序列号")
                .length(1, 50);
        Object result = sn.validate("");
        assertNotNull("required 字段空串仍应报必需", result);
        assertTrue("错误信息应为必需错误而非 length 错误", String.valueOf(result).contains("必需"));
    }
}
