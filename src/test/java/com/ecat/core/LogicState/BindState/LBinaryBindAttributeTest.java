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

package com.ecat.core.LogicState.BindState;

import com.ecat.core.LogicState.ILogicAttribute;
import com.ecat.core.LogicState.LBinaryAttribute;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.UnitInfo;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * LBinaryBindAttribute 单元测试
 *
 * @author coffee
 */
public class LBinaryBindAttributeTest {

    // ========== 构造函数和基本属性测试 ==========

    @Test
    public void testConstructorSetsBasicProperties() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "device_alarm", AttributeClass.GENERAL_ALARM,
                "source_device_1", "online_status");

        assertEquals("device_alarm", attr.getAttributeID());
        assertEquals(AttributeClass.GENERAL_ALARM, attr.getAttrClass());
        assertEquals("source_device_1", attr.getSourceDeviceId());
        assertEquals("online_status", attr.getSourceAttrId());
    }

    @Test
    public void testImplementsILogicBindAttribute() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "alarm", AttributeClass.ALARM_STATUS, "dev1", "attr1");

        assertTrue(attr instanceof ILogicBindAttribute);
        assertTrue(attr instanceof ILogicAttribute);
        assertTrue(attr instanceof LBinaryAttribute);
    }

    @Test
    public void testNotStandalone() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "alarm", AttributeClass.ALARM_STATUS, "dev1", "attr1");

        assertFalse(attr.isStandalone());
    }

    // ========== getBindedAttrs 测试 ==========

    @Test
    public void testGetBindedAttrsEmptyBeforeResolve() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "alarm", AttributeClass.ALARM_STATUS, "dev1", "attr1");

        List<AttributeBase<?>> binded = attr.getBindedAttrs();
        assertNotNull(binded);
        assertTrue(binded.isEmpty());
    }

    @Test
    public void testGetBindedAttrsAfterResolve() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "alarm", AttributeClass.ALARM_STATUS, "dev1", "attr1");

        // 创建一个 mock LBinaryAttribute 作为源
        LBinaryAttribute sourceAttr = new TestLBinaryAttr("source_alarm", AttributeClass.ALARM_STATUS);
        attr.resolveSource(sourceAttr);

        List<AttributeBase<?>> binded = attr.getBindedAttrs();
        assertNotNull(binded);
        assertEquals(1, binded.size());
        assertSame(sourceAttr, binded.get(0));
    }

    // ========== updateBindAttrValue 测试 ==========

    @Test
    public void testUpdateBindAttrValueFromBinary() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "alarm", AttributeClass.ALARM_STATUS, "dev1", "attr1");

        LBinaryAttribute sourceAttr = new TestLBinaryAttr("source_alarm", AttributeClass.ALARM_STATUS);
        sourceAttr.turnOn();

        attr.updateBindAttrValue(sourceAttr);
        assertTrue(attr.getValue());
    }

    @Test
    public void testUpdateBindAttrValueFromBinaryOff() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "alarm", AttributeClass.ALARM_STATUS, "dev1", "attr1");

        LBinaryAttribute sourceAttr = new TestLBinaryAttr("source_alarm", AttributeClass.ALARM_STATUS);
        sourceAttr.turnOff();

        attr.updateBindAttrValue(sourceAttr);
        assertFalse(attr.getValue());
    }

    @Test
    public void testUpdateBindAttrValueNullIgnored() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "alarm", AttributeClass.ALARM_STATUS, "dev1", "attr1");

        LBinaryAttribute sourceAttr = new TestLBinaryAttr("source_alarm", AttributeClass.ALARM_STATUS);
        // value is null
        attr.updateBindAttrValue(sourceAttr);
        assertNull(attr.getValue());
    }

    @Test
    public void testUpdateBindAttrValueNonBinaryIgnored() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "alarm", AttributeClass.ALARM_STATUS, "dev1", "attr1");

        // 创建一个非 LBinaryAttribute 的 AttributeBase
        AttributeBase<?> nonBinary = createMockStringAttr("other", AttributeClass.TEXT);
        attr.updateBindAttrValue(nonBinary);
        assertNull(attr.getValue());
    }

    // ========== setDisplayValue 测试 ==========

    @Test
    public void testSetDisplayValueReturnsFalse() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "alarm", AttributeClass.ALARM_STATUS, "dev1", "attr1");

        Boolean result = attr.setDisplayValue("on", null).join();
        assertFalse(result);
    }

    // ========== init 方法测试 ==========

    @Test
    public void testInitMethods() {
        LBinaryBindAttribute attr = new LBinaryBindAttribute(
                "alarm", AttributeClass.ALARM_STATUS, "dev1", "attr1");

        attr.initAttributeID("new_alarm_id");
        assertEquals("new_alarm_id", attr.getAttributeID());

        attr.initValueChangeable(true);
        assertTrue(attr.canValueChange());

        attr.initNativeUnit(null);
        assertNull(attr.getNativeUnit());

        attr.initDisplayUnit(null);
        assertNull(attr.getDisplayUnit());
    }

    // ========== Test helper ==========

    private static class TestLBinaryAttr extends LBinaryAttribute {
        TestLBinaryAttr(String attributeID, AttributeClass attrClass) {
            super(attributeID, attrClass);
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
