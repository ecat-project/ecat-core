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

import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.Unit.AirMassUnit;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 测试 LogicAttributeDefine 类的功能
 *
 * @author coffee
 */
public class LogicAttributeDefineTest {

    @Test
    public void testCreateWithAllFields() {
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

        // 验证所有字段正确赋值
        assertEquals("so2", def.getAttrId());
        assertEquals(AttributeClass.SO2, def.getAttrClass());
        assertEquals(AirMassUnit.UGM3, def.getNativeUnit());
        assertEquals(AirMassUnit.MGM3, def.getDisplayUnit());
        assertEquals(2, def.getDisplayPrecision());
        assertFalse(def.isValueChangeable());
        assertEquals(com.ecat.core.State.AQAttribute.class, def.getAttrClassType());
    }

    @Test
    public void testCreateNullableFields() {
        // 构造一个 nativeUnit 和 displayUnit 为 null 的 LogicAttributeDefine
        // 适用于没有单位概念的业务属性（如状态、文本类型）
        LogicAttributeDefine def = new LogicAttributeDefine(
                "system_state",
                AttributeClass.SYSTEM_STATE,
                null,
                null,
                0,
                false,
                com.ecat.core.State.TextAttribute.class
        );

        assertEquals("system_state", def.getAttrId());
        assertEquals(AttributeClass.SYSTEM_STATE, def.getAttrClass());
        assertNull(def.getNativeUnit());
        assertNull(def.getDisplayUnit());
        assertEquals(0, def.getDisplayPrecision());
        assertFalse(def.isValueChangeable());
        assertEquals(com.ecat.core.State.TextAttribute.class, def.getAttrClassType());
    }

    @Test
    public void testNoArgsConstructor() {
        // 测试无参构造函数
        LogicAttributeDefine def = new LogicAttributeDefine();

        assertNull(def.getAttrId());
        assertNull(def.getAttrClass());
        assertNull(def.getNativeUnit());
        assertNull(def.getDisplayUnit());
        assertEquals(0, def.getDisplayPrecision());
        assertFalse(def.isValueChangeable());
        assertNull(def.getAttrClassType());
    }

    @Test
    public void testSetters() {
        // 测试 Lombok 生成 setter 方法
        LogicAttributeDefine def = new LogicAttributeDefine();

        def.setAttrId("pm25");
        def.setAttrClass(AttributeClass.PM2_5);
        def.setNativeUnit(AirMassUnit.UGM3);
        def.setDisplayUnit(AirMassUnit.MGM3);
        def.setDisplayPrecision(1);
        def.setValueChangeable(true);
        def.setAttrClassType(com.ecat.core.State.NumericAttribute.class);

        assertEquals("pm25", def.getAttrId());
        assertEquals(AttributeClass.PM2_5, def.getAttrClass());
        assertEquals(AirMassUnit.UGM3, def.getNativeUnit());
        assertEquals(AirMassUnit.MGM3, def.getDisplayUnit());
        assertEquals(1, def.getDisplayPrecision());
        assertTrue(def.isValueChangeable());
        assertEquals(com.ecat.core.State.NumericAttribute.class, def.getAttrClassType());
    }
}
