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

package com.ecat.core.LogicState;

import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.TimeAttribute;
import com.ecat.core.State.UnitInfo;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.Assert.*;

/**
 * LTimeAttribute 单元测试
 *
 * @author coffee
 */
public class LTimeAttributeTest {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 测试用 TimeAttribute 子类，暴露 protected updateValue 方法
     */
    private static class TestableTimeAttribute extends TimeAttribute {
        TestableTimeAttribute(String attributeID, AttributeClass attrClass,
                              boolean valueChangeable) {
            super(attributeID, attrClass, null, null, 0, false, valueChangeable);
        }

        public void doUpdateValue(Instant value) {
            updateValue(value);
        }

        public void doUpdateValue(Instant value, AttributeStatus status) {
            updateValue(value, status);
        }
    }

    // ========== 绑定模式测试 ==========

    @Test
    public void boundModeHasBindedAttr() {
        TimeAttribute phyAttr = new TestableTimeAttribute("last_change", AttributeClass.TIME, false);
        LTimeAttribute logicAttr = new LTimeAttribute(phyAttr);

        List<AttributeBase<?>> binded = logicAttr.getBindedAttrs();
        assertEquals(1, binded.size());
        assertSame(phyAttr, binded.get(0));
        assertFalse(logicAttr.isStandalone());
        assertNotNull(logicAttr.getBindAttr());
    }

    @Test
    public void boundModeImplementsILogicAttribute() {
        TimeAttribute phyAttr = new TestableTimeAttribute("last_change", AttributeClass.TIME, false);
        LTimeAttribute logicAttr = new LTimeAttribute(phyAttr);

        assertTrue(logicAttr instanceof ILogicAttribute);
    }

    @Test
    public void boundModeUpdateBindAttrValueFromTimeAttribute() {
        TestableTimeAttribute phyAttr = new TestableTimeAttribute("last_change", AttributeClass.TIME, false);
        Instant testInstant = Instant.parse("2026-04-05T10:30:00Z");
        phyAttr.doUpdateValue(testInstant, AttributeStatus.NORMAL);

        LTimeAttribute logicAttr = new LTimeAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertNotNull(logicAttr.getValue());
        assertEquals(testInstant, logicAttr.getValue());
    }

    @Test
    public void boundModeUpdateBindAttrValueWithInstant() {
        TimeAttribute phyAttr = new TestableTimeAttribute("last_change", AttributeClass.TIME, false);
        LTimeAttribute logicAttr = new LTimeAttribute(phyAttr);

        Instant testInstant = Instant.parse("2026-04-05T14:00:00Z");
        logicAttr.updateBindAttrValue(testInstant);

        assertNotNull(logicAttr.getValue());
        assertEquals(testInstant, logicAttr.getValue());
    }

    @Test
    public void boundModeUpdateBindAttrValueNullIgnored() {
        TestableTimeAttribute phyAttr = new TestableTimeAttribute("last_change", AttributeClass.TIME, false);
        LTimeAttribute logicAttr = new LTimeAttribute(phyAttr);

        // phyAttr value is null
        logicAttr.updateBindAttrValue(phyAttr);
        assertNull(logicAttr.getValue());

        // null Instant
        logicAttr.updateBindAttrValue((Instant) null);
        assertNull(logicAttr.getValue());
    }

    @Test
    public void boundModeUpdateBindAttrValueNonTimeIgnored() {
        TimeAttribute phyAttr = new TestableTimeAttribute("last_change", AttributeClass.TIME, false);
        LTimeAttribute logicAttr = new LTimeAttribute(phyAttr);

        AttributeBase<?> nonTime = createMockStringAttr("other", AttributeClass.TEXT);
        logicAttr.updateBindAttrValue(nonTime);
        assertNull(logicAttr.getValue());
    }

    @Test
    public void boundModeSetDisplayValueDelegates() {
        TestableTimeAttribute phyAttr = new TestableTimeAttribute("last_change", AttributeClass.TIME, true);
        LTimeAttribute logicAttr = new LTimeAttribute(phyAttr);

        Instant testInstant = Instant.parse("2026-04-05T10:30:00Z");
        ZonedDateTime zdt = testInstant.atZone(ZoneId.systemDefault());
        String timeStr = DISPLAY_FORMATTER.format(zdt);

        Boolean result = logicAttr.setDisplayValue(timeStr).join();
        assertTrue(result);
        assertNotNull(phyAttr.getValue());
    }

    @Test
    public void boundModeInitMethods() {
        TimeAttribute phyAttr = new TestableTimeAttribute("last_change", AttributeClass.TIME, false);
        LTimeAttribute logicAttr = new LTimeAttribute(phyAttr);

        logicAttr.initAttributeID("new_last_change");
        assertEquals("new_last_change", logicAttr.getAttributeID());

        logicAttr.initValueChangeable(true);
        assertTrue(logicAttr.canValueChange());

        logicAttr.initNativeUnit(null);
        assertNull(logicAttr.getNativeUnit());

        logicAttr.initDisplayUnit(null);
        assertNull(logicAttr.getDisplayUnit());
    }

    // ========== Standalone 模式测试 ==========

    @Test
    public void standaloneModeNoBindedAttrs() {
        LTimeAttribute logicAttr = new LTimeAttribute("last_change", AttributeClass.TIME);

        assertTrue(logicAttr.getBindedAttrs().isEmpty());
        assertTrue(logicAttr.isStandalone());
        assertNull(logicAttr.getBindAttr());
    }

    @Test
    public void standaloneModeBasicProperties() {
        LTimeAttribute logicAttr = new LTimeAttribute("last_change", AttributeClass.TIME);

        assertEquals("last_change", logicAttr.getAttributeID());
        assertEquals(AttributeClass.TIME, logicAttr.getAttrClass());
        assertFalse(logicAttr.canUnitChange());
        assertFalse(logicAttr.canValueChange());
    }

    @Test
    public void standaloneModeGetDisplayValueNull() {
        LTimeAttribute logicAttr = new LTimeAttribute("last_change", AttributeClass.TIME);
        assertNull(logicAttr.getDisplayValue());
    }

    @Test
    public void standaloneModeGetDisplayValueWithInstant() {
        TestLTimeAttr logicAttr = new TestLTimeAttr("last_change", AttributeClass.TIME);

        Instant instant = Instant.parse("2026-04-05T10:30:00Z");
        logicAttr.doUpdateValue(instant);

        String display = logicAttr.getDisplayValue();
        assertNotNull(display);
        assertTrue(display.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    public void standaloneModeSetDisplayValueWithISO8601() {
        LTimeAttribute logicAttr = new LTimeAttribute("last_change", AttributeClass.TIME);
        logicAttr.initValueChangeable(true);

        Boolean result = logicAttr.setDisplayValue("2026-04-05T10:30:00Z").join();
        assertTrue(result);
        assertNotNull(logicAttr.getValue());
        assertEquals(Instant.parse("2026-04-05T10:30:00Z"), logicAttr.getValue());
    }

    @Test
    public void standaloneModeRoundTrip() {
        TestLTimeAttr logicAttr = new TestLTimeAttr("last_change", AttributeClass.TIME);

        Instant original = Instant.parse("2026-04-05T10:30:00Z");
        logicAttr.doUpdateValue(original);

        String display = logicAttr.getDisplayValue();
        assertNotNull(display);

        // 使用显示值创建新属性
        LTimeAttribute attr2 = new LTimeAttribute("test2", AttributeClass.TIME);
        attr2.initValueChangeable(true);
        attr2.setDisplayValue(display).join();

        assertNotNull(attr2.getValue());
        assertEquals(original.getEpochSecond(), attr2.getValue().getEpochSecond());
    }

    // ========== 通用测试 ==========

    @Test
    public void testGetValueType() {
        TimeAttribute phyAttr = new TestableTimeAttribute("last_change", AttributeClass.TIME, false);
        LTimeAttribute logicAttr = new LTimeAttribute(phyAttr);

        assertEquals(com.ecat.core.State.AttrValueType.INSTANT, logicAttr.getValueType());
    }

    @Test
    public void testAttributeType() {
        LTimeAttribute logicAttr = new LTimeAttribute("last_change", AttributeClass.TIME);
        assertEquals(AttributeType.TEXT, logicAttr.getAttributeType());
    }

    // ========== Test helpers ==========

    private static class TestLTimeAttr extends LTimeAttribute {
        TestLTimeAttr(String attributeID, AttributeClass attrClass) {
            super(attributeID, attrClass);
        }

        public void doUpdateValue(Instant value) {
            updateValue(value);
        }
    }

    private static AttributeBase<String> createMockStringAttr(String attrId, AttributeClass attrClass) {
        return new AttributeBase<String>(attrId, attrClass, null, null, 0, false, false) {
            @Override public String getDisplayValue(UnitInfo toUnit) { return null; }
            @Override protected String convertFromUnitImp(String value, UnitInfo fromUnit) { return value; }
            @Override public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) { return value; }
            @Override public com.ecat.core.Utils.DynamicConfig.ConfigDefinition getValueDefinition() { return null; }
            @Override protected com.ecat.core.I18n.I18nKeyPath getI18nPrefixPath() {
                return new com.ecat.core.I18n.I18nKeyPath("test.", "test");
            }
            @Override public AttributeType getAttributeType() { return AttributeType.TEXT; }
        };
    }
}
