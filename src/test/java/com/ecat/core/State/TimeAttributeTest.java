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

package com.ecat.core.State;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.*;

/**
 * TimeAttribute 单元测试
 *
 * @author coffee
 */
public class TimeAttributeTest {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeAttribute attr;

    @Before
    public void setUp() {
        attr = new TimeAttribute("last_change_time", AttributeClass.TIME,
                null, null, 0, false, false);
    }

    // ========== 基本属性测试 ==========

    @Test
    public void testBasicProperties() {
        assertEquals("last_change_time", attr.getAttributeID());
        assertEquals(AttributeClass.TIME, attr.getAttrClass());
        assertFalse(attr.canUnitChange());
        assertFalse(attr.canValueChange());
        assertNull(attr.getNativeUnit());
        assertNull(attr.getDisplayUnit());
    }

    @Test
    public void testAttributeType() {
        assertEquals(AttributeType.TEXT, attr.getAttributeType());
    }

    // ========== getDisplayValue 测试 ==========

    @Test
    public void testGetDisplayValueNull() {
        assertNull(attr.getDisplayValue());
    }

    @Test
    public void testGetDisplayValueWithInstant() {
        // 使用固定时间: 2026-04-05 10:30:00 UTC
        Instant instant = Instant.parse("2026-04-05T10:30:00Z");
        attr.updateValue(instant);

        String display = attr.getDisplayValue();
        assertNotNull(display);

        // 验证格式为 yyyy-MM-dd HH:mm:ss
        assertTrue(display.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    public void testGetDisplayValueWithUnit() {
        Instant instant = Instant.parse("2026-04-05T10:30:00Z");
        attr.updateValue(instant);

        String display = attr.getDisplayValue(null);
        assertNotNull(display);
        assertTrue(display.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    // ========== setDisplayValue 测试 ==========

    @Test
    public void testSetDisplayValueWithFormattedString() {
        // 创建一个 valueChangeable=true 的属性
        TimeAttribute changeableAttr = new TimeAttribute("test", AttributeClass.TIME,
                null, null, 0, false, true);

        // 使用系统时区构造时间字符串
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        String timeStr = DISPLAY_FORMATTER.format(now);

        Boolean result = changeableAttr.setDisplayValue(timeStr).join();
        assertTrue(result);
        assertNotNull(changeableAttr.getValue());
    }

    @Test
    public void testSetDisplayValueWithISO8601() {
        TimeAttribute changeableAttr = new TimeAttribute("test", AttributeClass.TIME,
                null, null, 0, false, true);

        Boolean result = changeableAttr.setDisplayValue("2026-04-05T10:30:00Z").join();
        assertTrue(result);
        assertNotNull(changeableAttr.getValue());
        assertEquals(Instant.parse("2026-04-05T10:30:00Z"), changeableAttr.getValue());
    }

    @Test
    public void testSetDisplayValueInvalidFormat() {
        TimeAttribute changeableAttr = new TimeAttribute("test", AttributeClass.TIME,
                null, null, 0, false, true);

        try {
            changeableAttr.setDisplayValue("not-a-date").join();
            fail("Should throw CompletionException");
        } catch (Exception e) {
            // Expected: invalid format causes CompletionException
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testSetDisplayValueNotChangeable() {
        // valueChangeable is false by default
        Boolean result = attr.setDisplayValue("2026-04-05T10:30:00Z").join();
        assertFalse(result);
    }

    // ========== 往返测试 ==========

    @Test
    public void testRoundTrip() {
        TimeAttribute changeableAttr = new TimeAttribute("test1", AttributeClass.TIME,
                null, null, 0, false, true);

        Instant original = Instant.parse("2026-04-05T10:30:00Z");
        changeableAttr.updateValue(original);

        String display = changeableAttr.getDisplayValue();
        assertNotNull(display);

        // 设置新的属性来解析显示值
        TimeAttribute attr2 = new TimeAttribute("test2", AttributeClass.TIME,
                null, null, 0, false, true);
        attr2.setDisplayValue(display).join();

        assertNotNull(attr2.getValue());
        // 往返后值应该相同（在秒精度内）
        assertEquals(original.getEpochSecond(), attr2.getValue().getEpochSecond());
    }

    // ========== getValueType 测试 ==========

    @Test
    public void testGetValueType() {
        assertEquals(AttrValueType.INSTANT, attr.getValueType());
    }

    @Test
    public void testGetValueTypeName() {
        assertEquals("instant", attr.getValueTypeName());
    }

    // ========== AttrValueType.fromJDKClass 测试 ==========

    @Test
    public void testAttrValueTypeFromInstant() {
        assertEquals(AttrValueType.INSTANT, AttrValueType.fromJDKClass(Instant.class));
    }

    // ========== convertValueToUnit 测试 ==========

    @Test(expected = UnsupportedOperationException.class)
    public void testConvertValueToUnitThrows() {
        attr.convertValueToUnit(1.0, null, null);
    }
}
