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
import com.ecat.core.LogicState.LNumericAttribute;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.NumericAttribute;
import com.ecat.core.State.UnitInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * LNumericBindAttribute 单元测试
 *
 * @author coffee
 */
public class LNumericBindAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("TestAttrClass");
    }

    /**
     * 测试用 LNumericAttribute 子类，暴露 protected updateValue
     */
    private static class TestableLNumericAttr extends LNumericAttribute {
        TestableLNumericAttr(String attributeID, AttributeClass attrClass,
                             UnitInfo nativeUnit, UnitInfo displayUnit, int displayPrecision) {
            super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision);
        }

        public void doUpdateValue(Double value, AttributeStatus status) {
            updateValue(value, status);
        }
    }

    // ========== 构造函数和基本属性测试 ==========

    @Test
    public void testConstructorSetsBasicProperties() {
        LNumericBindAttribute attr = new LNumericBindAttribute(
                "total_power", mockAttrClass, null, null, 2,
                "ups_device_1", "output_power");

        assertEquals("total_power", attr.getAttributeID());
        assertEquals(mockAttrClass, attr.getAttrClass());
        assertEquals("ups_device_1", attr.getSourceDeviceId());
        assertEquals("output_power", attr.getSourceAttrId());
    }

    @Test
    public void testImplementsILogicBindAttribute() {
        LNumericBindAttribute attr = new LNumericBindAttribute(
                "power", mockAttrClass, null, null, 2, "dev1", "attr1");

        assertTrue(attr instanceof ILogicBindAttribute);
        assertTrue(attr instanceof ILogicAttribute);
        assertTrue(attr instanceof LNumericAttribute);
    }

    @Test
    public void testNotStandalone() {
        LNumericBindAttribute attr = new LNumericBindAttribute(
                "power", mockAttrClass, null, null, 2, "dev1", "attr1");

        assertFalse(attr.isStandalone());
    }

    // ========== getBindedAttrs 测试 ==========

    @Test
    public void testGetBindedAttrsEmptyBeforeResolve() {
        LNumericBindAttribute attr = new LNumericBindAttribute(
                "power", mockAttrClass, null, null, 2, "dev1", "attr1");

        List<AttributeBase<?>> binded = attr.getBindedAttrs();
        assertNotNull(binded);
        assertTrue(binded.isEmpty());
    }

    @Test
    public void testGetBindedAttrsAfterResolve() {
        LNumericBindAttribute attr = new LNumericBindAttribute(
                "power", mockAttrClass, null, null, 2, "dev1", "attr1");

        // 创建一个 mock ILogicAttribute 作为源
        ILogicAttribute<?> mockSource = mock(ILogicAttribute.class);
        attr.resolveSource(mockSource);

        // mock 不是 AttributeBase 实例，所以 getBindedAttrs 返回空列表
        List<AttributeBase<?>> binded = attr.getBindedAttrs();
        assertNotNull(binded);
        assertEquals(0, binded.size());
    }

    // ========== updateBindAttrValue 测试 ==========

    @Test
    public void testUpdateBindAttrValueFromNumeric() {
        LNumericBindAttribute attr = new LNumericBindAttribute(
                "total_power", mockAttrClass, null, null, 2,
                "dev1", "source_power");

        // 创建一个 LNumericAttribute 包装物理 NumericAttribute
        NumericAttribute phyAttr = new NumericAttribute(
                "source_power", mockAttrClass, null, null, 2, false, false);
        phyAttr.updateValue(42.5, AttributeStatus.NORMAL);
        LNumericAttribute sourceAttr = new TestableLNumericAttr(
                "source_power", mockAttrClass, null, null, 2);

        // LNumericAttribute 的 updateBindAttrValue 检查 instanceof LNumericAttribute
        // 我们需要用一个真实的 LNumericAttribute（绑定模式）来测试
        LNumericAttribute boundSource = new LNumericAttribute(phyAttr);

        attr.updateBindAttrValue(boundSource);
        assertNotNull(attr.getValue());
        assertEquals(42.5, attr.getValue(), 0.01);
    }

    @Test
    public void testUpdateBindAttrValueNullDisplayIgnored() {
        LNumericBindAttribute attr = new LNumericBindAttribute(
                "power", mockAttrClass, null, null, 2, "dev1", "attr1");

        // LNumericAttribute 绑定未设值的物理属性 - displayValue returns null
        NumericAttribute phyAttr = new NumericAttribute(
                "source", mockAttrClass, null, null, 2, false, false);
        LNumericAttribute sourceAttr = new LNumericAttribute(phyAttr);
        attr.updateBindAttrValue(sourceAttr);
        assertNull(attr.getValue());
    }

    @Test
    public void testUpdateBindAttrValueNonNumericIgnored() {
        LNumericBindAttribute attr = new LNumericBindAttribute(
                "power", mockAttrClass, null, null, 2, "dev1", "attr1");

        // 创建一个非 LNumericAttribute 的 AttributeBase
        AttributeBase<String> nonNumeric = createMockStringAttr("other", AttributeClass.TEXT);
        attr.updateBindAttrValue(nonNumeric);
        assertNull(attr.getValue());
    }

    // ========== setDisplayValue 测试 ==========

    @Test
    public void testSetDisplayValueReturnsFalse() {
        LNumericBindAttribute attr = new LNumericBindAttribute(
                "power", mockAttrClass, null, null, 2, "dev1", "attr1");

        Boolean result = attr.setDisplayValue("100.0", null).join();
        assertFalse(result);
    }

    // ========== init 方法测试 ==========

    @Test
    public void testInitMethods() {
        LNumericBindAttribute attr = new LNumericBindAttribute(
                "power", mockAttrClass, null, null, 2, "dev1", "attr1");

        attr.initAttributeID("new_power_id");
        assertEquals("new_power_id", attr.getAttributeID());

        attr.initValueChangeable(true);
        assertTrue(attr.canValueChange());

        attr.initNativeUnit(null);
        assertNull(attr.getNativeUnit());

        attr.initDisplayUnit(null);
        assertNull(attr.getDisplayUnit());
    }

    // ========== Test helper ==========

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
