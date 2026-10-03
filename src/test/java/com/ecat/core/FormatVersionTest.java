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

package com.ecat.core;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * FormatVersion 单元测试
 * <p>
 * 表驱动锁定 "major.minor" 格式判定与数值比较语义：
 * 按数值比较而非字典序（4.10 &gt; 4.9）；拒绝前导零/单段/三段等一切非两段形态。
 */
public class FormatVersionTest {

    // ==================== isWellFormed：合法/非法形态表 ====================

    @Test
    public void isWellFormed_acceptsTwoSegmentNonNegative() {
        assertTrue(FormatVersion.isWellFormed("4.0"));
        assertTrue(FormatVersion.isWellFormed("0.0"));
        assertTrue(FormatVersion.isWellFormed("10.20"));
        assertTrue(FormatVersion.isWellFormed("0.1"));
    }

    @Test
    public void isWellFormed_rejectsMalformedForms() {
        assertFalse("缺 minor 应拒绝", FormatVersion.isWellFormed("4"));
        assertFalse("前导零应拒绝", FormatVersion.isWellFormed("04.0"));
        assertFalse("三段应拒绝", FormatVersion.isWellFormed("4.0.0"));
        assertFalse("负数应拒绝", FormatVersion.isWellFormed("-1.0"));
        assertFalse("minor 负数应拒绝", FormatVersion.isWellFormed("4.-1"));
        assertFalse("尾随点应拒绝", FormatVersion.isWellFormed("4.0."));
        assertFalse("前导点应拒绝", FormatVersion.isWellFormed(".4.0"));
        assertFalse("非数字应拒绝", FormatVersion.isWellFormed("a.b"));
        assertFalse("空串应拒绝", FormatVersion.isWellFormed(""));
        assertFalse("null 应拒绝", FormatVersion.isWellFormed(null));
        assertFalse("含空格应拒绝", FormatVersion.isWellFormed("4 .0"));
    }

    // ==================== requireWellFormed：非法值抛 ConfigFormatException ====================

    @Test
    public void requireWellFormed_passesForLegalValue() {
        // 合法值静默通过（无副作用返回）
        FormatVersion.requireWellFormed("4.0", "com.ecat:test(entryId=x)");
    }

    @Test
    public void requireWellFormed_throwsWithPayloadForIllegalValue() {
        try {
            FormatVersion.requireWellFormed("4", "com.ecat:test(entryId=x)");
            fail("非法格式应抛 ConfigFormatException");
        } catch (ConfigFormatException e) {
            assertEquals("location 应透传调用方给定的定位", "com.ecat:test(entryId=x)", e.getLocation());
            assertEquals("expected 应为格式说明", "major.minor 字符串", e.getExpected());
            assertEquals("actual 应携带实际值", "4", e.getActual());
            String message = e.getMessage();
            assertTrue("消息应含位置字段", message.contains("位置=com.ecat:test(entryId=x)"));
            assertTrue("消息应含期望字段", message.contains("期望=major.minor 字符串"));
            assertTrue("消息应含实际字段", message.contains("实际=4"));
        }
    }

    // ==================== compare：数值比较表（不按字典序） ====================

    @Test
    public void compare_byNumericValueNotLexicographic() {
        // {a, b, 期望符号}: compare(a,b) 与 signum(期望) 同号
        String[][] cases = {
                {"3.9", "4.0", "-1"},
                {"4.0", "4.0", "0"},
                {"4.10", "4.9", "1"},   // 字典序会判 4.10 < 4.9，数值比较必须 > 0
                {"10.0", "9.1", "1"},   // 字典序会判 10 < 9，数值比较必须 > 0
                {"0.1", "0.2", "-1"},
                {"4.0", "3.9", "1"},    // 反向对称
        };
        for (String[] c : cases) {
            int result = FormatVersion.compare(c[0], c[1]);
            int expected = Integer.parseInt(c[2]);
            assertEquals("compare(" + c[0] + "," + c[1] + ") 符号不符",
                    Math.signum(expected), Math.signum(result), 0.0);
        }
    }

    @Test
    public void compare_rejectsNonWellFormedInputAsProgrammerError() {
        try {
            FormatVersion.compare("4", "4.0");
            fail("非 well-formed 入参应抛 IllegalArgumentException（程序员错误）");
        } catch (IllegalArgumentException e) {
            assertTrue("异常应携带两参原值", e.getMessage().contains("4") && e.getMessage().contains("4.0"));
        }
        try {
            FormatVersion.compare(null, "4.0");
            fail("null 入参应抛 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // 期望
        }
    }
}
