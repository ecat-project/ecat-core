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
 * TextConfigItem 单测：聚焦 required 字段对空白串的校验。
 * <p>历史背景：{@code AbstractConfigItem.validate} 的 required 原只查 {@code null}，
 * 空串 {@code ""} 能绕过（API/脚本 POST 空 sn 可成）——见 bug-record-20260724-084832。
 * 收紧后 required 字段 null 与空白串均视为缺失。
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
}
