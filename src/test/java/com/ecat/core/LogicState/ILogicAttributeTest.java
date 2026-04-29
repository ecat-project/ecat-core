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

import com.ecat.core.State.AttributeAbility;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.UnitInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * 测试 ILogicAttribute 接口的功能
 * <p>
 * 由于接口无法直接实例化，使用具体的内部匿名实现类进行测试。
 * 重点验证：
 * - 接口定义正确，可被实现
 * - default 方法 initFromDefinition 正确调用各 init 方法
 * - 绑定属性相关方法工作正常
 *
 * @author coffee
 */
public class ILogicAttributeTest {

    /**
     * 简单的 ILogicAttribute 具体实现类，用于测试接口方法。
     * 仅记录调用状态，不实现完整的 AttributeAbility 语义。
     */
    private static class TestLogicAttribute implements ILogicAttribute<Double> {
        private String attributeID;
        private UnitInfo nativeUnit;
        private boolean valueChangeable;
        private int displayPrecision;
        private UnitInfo displayUnit;
        private final List<AttributeBase<?>> bindedAttrs = new ArrayList<>();
        private AttributeBase<?> lastUpdatedAttr;

        // --- ILogicAttribute 方法 ---

        @Override
        public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
            this.lastUpdatedAttr = updatedAttr;
        }

        @Override
        public List<AttributeBase<?>> getBindedAttrs() {
            return bindedAttrs;
        }

        @Override
        public void initAttributeID(String attrID) {
            this.attributeID = attrID;
        }

        @Override
        public void initNativeUnit(UnitInfo nativeUnit) {
            this.nativeUnit = nativeUnit;
        }

        @Override
        public void initValueChangeable(boolean valueChangeable) {
            this.valueChangeable = valueChangeable;
        }

        @Override
        public void initDisplayUnit(UnitInfo displayUnit) {
            this.displayUnit = displayUnit;
        }

        @Override
        public void initAttrClass(AttributeClass attrClass) {
            // no-op for test mock
        }

        @Override
        public boolean publicState() {
            return true;
        }

        @Override
        public boolean updateValue(Double newValue, AttributeStatus newStatus) {
            return true;
        }

        // --- AttributeAbility 方法（最小化实现） ---

        @Override
        public String getAttributeID() {
            return attributeID;
        }

        @Override
        public boolean canUnitChange() {
            return false;
        }

        @Override
        public boolean changeDisplayUnit(UnitInfo newDisplayUnit) {
            this.displayUnit = newDisplayUnit;
            return true;
        }

        @Override
        public boolean changeDisplayPrecision(int newPrecision) {
            this.displayPrecision = newPrecision;
            return true;
        }

        @Override
        public int getDisplayPrecision() {
            return displayPrecision;
        }

        @Override
        public boolean canValueChange() {
            return valueChangeable;
        }

        @Override
        public boolean setStatus(AttributeStatus newStatus) {
            return false;
        }

        @Override
        public AttributeStatus getStatus() {
            return AttributeStatus.EMPTY;
        }

        @Override
        public Double getValue() {
            return null;
        }

        @Override
        public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public String getDisplayValue() {
            return null;
        }

        @Override
        public String getDisplayValue(UnitInfo toUnit) {
            return null;
        }

        @Override
        public String getI18nValue(UnitInfo toUnit) {
            return null;
        }

        @Override
        public String getDisplayUnitStr() {
            return "";
        }

        @Override
        public UnitInfo getDisplayUnit() {
            return displayUnit;
        }

        @Override
        public UnitInfo getNativeUnit() {
            return nativeUnit;
        }

        @Override
        public AttributeType getAttributeType() {
            return AttributeType.NUMERIC;
        }

        @Override
        public boolean isPersistable() { return false; }
        @Override
        public void setPersistable(boolean persistable) {}
        @Override
        public Double getDefaultValue() { return null; }
        @Override
        public void setDefaultValue(Double defaultValue) {}

        // --- 辅助方法供测试访问 ---

        public UnitInfo getStoredDisplayUnit() {
            return displayUnit;
        }

        public AttributeBase<?> getLastUpdatedAttr() {
            return lastUpdatedAttr;
        }
    }

    @Test
    public void testInterfaceCanBeImplemented() {
        // 验证 ILogicAttribute 接口可以被正常实现
        ILogicAttribute<Double> attr = new TestLogicAttribute();
        assertNotNull(attr);
        // 验证它也是 AttributeAbility 类型
        assertTrue(attr instanceof AttributeAbility);
    }

    @Test
    public void testInitAttributeID() {
        TestLogicAttribute attr = new TestLogicAttribute();
        attr.initAttributeID("so2");
        assertEquals("so2", attr.getAttributeID());
    }

    @Test
    public void testInitNativeUnit() {
        TestLogicAttribute attr = new TestLogicAttribute();
        attr.initNativeUnit(AirMassUnit.UGM3);
        assertEquals(AirMassUnit.UGM3, attr.getNativeUnit());
    }

    @Test
    public void testInitNativeUnitNull() {
        TestLogicAttribute attr = new TestLogicAttribute();
        attr.initNativeUnit(null);
        assertNull(attr.getNativeUnit());
    }

    @Test
    public void testInitValueChangeable() {
        TestLogicAttribute attr = new TestLogicAttribute();
        attr.initValueChangeable(true);
        assertTrue(attr.canValueChange());

        attr.initValueChangeable(false);
        assertFalse(attr.canValueChange());
    }

    @Test
    public void testUpdateBindAttrValue() {
        TestLogicAttribute attr = new TestLogicAttribute();
        // 由于 AttributeBase 是抽象类，使用匿名内部类创建一个最小化实现
        AttributeBase<Double> mockBase = new AttributeBase<Double>(
                "test", AttributeClass.VALUE, null, null, 0, false, false) {
            @Override
            public String getDisplayValue(UnitInfo toUnit) {
                return null;
            }
            @Override
            protected Double convertFromUnitImp(Double value, UnitInfo fromUnit) {
                return value;
            }
            @Override
            public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) {
                return value;
            }
            @Override
            public com.ecat.core.Utils.DynamicConfig.ConfigDefinition getValueDefinition() {
                return null;
            }
            @Override
            protected com.ecat.core.I18n.I18nKeyPath getI18nPrefixPath() {
                return new com.ecat.core.I18n.I18nKeyPath("test.", "test");
            }
            @Override
            public AttributeType getAttributeType() {
                return AttributeType.NUMERIC;
            }
        };

        attr.updateBindAttrValue(mockBase);
        assertSame(mockBase, attr.getLastUpdatedAttr());
    }

    @Test
    public void testGetBindedAttrsReturnsMutableList() {
        TestLogicAttribute attr = new TestLogicAttribute();
        List<AttributeBase<?>> binded = attr.getBindedAttrs();
        assertNotNull(binded);
        assertTrue(binded.isEmpty());
        // 验证返回的是可修改的列表
        binded.add(null); // 添加null仅用于验证可变性
        assertEquals(1, binded.size());
    }

    @Test
    public void testInitFromDefinitionWithAllFields() {
        TestLogicAttribute attr = new TestLogicAttribute();

        // 构造一个包含所有非null字段的 LogicAttributeDefine
        LogicAttributeDefine def = new LogicAttributeDefine(
                "so2",
                AttributeClass.SO2,
                AirMassUnit.UGM3,
                AirMassUnit.MGM3,
                2,
                false,
                com.ecat.core.State.AQAttribute.class
        );

        // 调用 default 方法
        attr.initFromDefinition(def);

        // 验证各 init 方法被正确调用
        assertEquals("so2", attr.getAttributeID());
        assertEquals(AirMassUnit.UGM3, attr.getNativeUnit());
        assertFalse(attr.canValueChange());
        assertEquals(AirMassUnit.MGM3, attr.getStoredDisplayUnit());
        assertEquals(2, attr.getDisplayPrecision());
    }

    @Test
    public void testInitFromDefinitionWithNullDisplayUnit() {
        TestLogicAttribute attr = new TestLogicAttribute();

        // displayUnit 为 null 的场景
        LogicAttributeDefine def = new LogicAttributeDefine(
                "system_state",
                AttributeClass.SYSTEM_STATE,
                null,
                null,
                0,
                false,
                com.ecat.core.State.TextAttribute.class
        );

        attr.initFromDefinition(def);

        assertEquals("system_state", attr.getAttributeID());
        assertNull(attr.getNativeUnit());
        assertFalse(attr.canValueChange());
        assertNull(attr.getStoredDisplayUnit());
        assertEquals(0, attr.getDisplayPrecision());
    }

    @Test
    public void testInitFromDefinitionWithNullDisplayUnitDoesNotCallChangeDisplayUnit() {
        TestLogicAttribute attr = new TestLogicAttribute();

        // displayUnit 为 null，不应调用 changeDisplayUnit
        LogicAttributeDefine def = new LogicAttributeDefine(
                "temp",
                AttributeClass.TEMPERATURE,
                null,
                null,
                3,
                true,
                com.ecat.core.State.NumericAttribute.class
        );

        attr.initFromDefinition(def);

        // changeDisplayUnit 未被调用，displayUnit 应仍为 null
        assertNull(attr.getStoredDisplayUnit());
        // 但 displayPrecision 仍然应该被设置
        assertEquals(3, attr.getDisplayPrecision());
    }

    @Test
    public void testInitFromDefinitionWithChangeableValue() {
        TestLogicAttribute attr = new TestLogicAttribute();

        LogicAttributeDefine def = new LogicAttributeDefine(
                "control_setpoint",
                AttributeClass.VALUE,
                null,
                null,
                1,
                true,
                com.ecat.core.State.NumericAttribute.class
        );

        attr.initFromDefinition(def);

        assertEquals("control_setpoint", attr.getAttributeID());
        assertTrue(attr.canValueChange());
        assertEquals(1, attr.getDisplayPrecision());
    }
}
