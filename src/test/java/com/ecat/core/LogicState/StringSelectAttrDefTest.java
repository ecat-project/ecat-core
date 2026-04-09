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
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * StringSelectAttrDef 单元测试
 *
 * @author coffee
 */
public class StringSelectAttrDefTest {

    @Test
    public void constructorStoresOptions() {
        List<String> options = Arrays.asList("Normal", "Maintenance", "Calibration");
        StringSelectAttrDef def = new StringSelectAttrDef(
                "manual_status",
                AttributeClass.MODE,
                null,
                null,
                0,
                true,
                LStringSelectAttribute.class,
                false,
                options
        );

        assertEquals("manual_status", def.getAttrId());
        assertEquals(AttributeClass.MODE, def.getAttrClass());
        assertNull(def.getNativeUnit());
        assertNull(def.getDisplayUnit());
        assertEquals(0, def.getDisplayPrecision());
        assertTrue(def.isValueChangeable());
        assertEquals(LStringSelectAttribute.class, def.getAttrClassType());
        assertFalse(def.isMapable());
        assertSame(options, def.getOptions());
        assertEquals(3, def.getOptions().size());
        assertEquals("Normal", def.getOptions().get(0));
        assertEquals("Maintenance", def.getOptions().get(1));
        assertEquals("Calibration", def.getOptions().get(2));
    }

    @Test
    public void constructorWithAllNullUnits() {
        List<String> options = Arrays.asList("On", "Off");
        StringSelectAttrDef def = new StringSelectAttrDef(
                "switch_state",
                AttributeClass.SYSTEM_STATE,
                null,
                null,
                0,
                false,
                LStringSelectAttribute.class,
                true,
                options
        );

        assertEquals("switch_state", def.getAttrId());
        assertEquals(AttributeClass.SYSTEM_STATE, def.getAttrClass());
        assertNull(def.getNativeUnit());
        assertNull(def.getDisplayUnit());
        assertEquals(0, def.getDisplayPrecision());
        assertFalse(def.isValueChangeable());
        assertEquals(LStringSelectAttribute.class, def.getAttrClassType());
        assertTrue(def.isMapable());
        assertEquals(2, def.getOptions().size());
    }

    @Test
    public void mapableDefaultsViaSuper() {
        // StringSelectAttrDef constructor calls setMapable(mapable) explicitly
        // Verify that mapable=true works
        List<String> options = Arrays.asList("A", "B");
        StringSelectAttrDef def = new StringSelectAttrDef(
                "test_attr",
                AttributeClass.MODE,
                null,
                null,
                0,
                false,
                LStringSelectAttribute.class,
                true,
                options
        );

        assertTrue(def.isMapable());
        // displayable should still default to true from super 7-param constructor
        assertTrue(def.isDisplayable());
    }

    @Test
    public void valueChangeableCorrect() {
        List<String> options = Arrays.asList("X", "Y");

        StringSelectAttrDef changeable = new StringSelectAttrDef(
                "attr1", AttributeClass.MODE, null, null, 0,
                true, LStringSelectAttribute.class, true, options);
        assertTrue(changeable.isValueChangeable());

        StringSelectAttrDef notChangeable = new StringSelectAttrDef(
                "attr2", AttributeClass.MODE, null, null, 0,
                false, LStringSelectAttribute.class, true, options);
        assertFalse(notChangeable.isValueChangeable());
    }

    @Test
    public void extendsLogicAttributeDefine() {
        List<String> options = Arrays.asList("A");
        StringSelectAttrDef def = new StringSelectAttrDef(
                "test", AttributeClass.MODE, null, null, 0,
                false, LStringSelectAttribute.class, false, options);
        assertTrue(def instanceof LogicAttributeDefine);
    }
}
