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

import static org.junit.Assert.*;

/**
 * LogicAttributeFactory 单元测试
 *
 * @author coffee
 */
public class LogicAttributeFactoryTest {

    // ========== StringSelectAttrDef factory tests ==========

    @Test
    public void createStringSelectAttributeFromFactory() {
        StringSelectAttrDef def = new StringSelectAttrDef(
                "manual_status",
                AttributeClass.MODE,
                null, null, 0,
                true,
                LStringSelectAttribute.class,
                false,
                Arrays.asList("Normal", "Maintenance", "Calibration")
        );

        LStringSelectAttribute attr = LogicAttributeFactory.create(
                LStringSelectAttribute.class, def);

        assertEquals("manual_status", attr.getAttributeID());
        assertEquals(AttributeClass.MODE, attr.getAttrClass());
        assertTrue(attr.isStandalone());
        // Verify options are set from def
        assertEquals(3, attr.getOptions().size());
        assertEquals("Normal", attr.getOptions().get(0));
        assertEquals("Maintenance", attr.getOptions().get(1));
        assertEquals("Calibration", attr.getOptions().get(2));
    }

    @Test
    public void createStringSelectAttributeUpdateValue() {
        StringSelectAttrDef def = new StringSelectAttrDef(
                "fan_mode",
                AttributeClass.MODE,
                null, null, 0,
                true,
                LStringSelectAttribute.class,
                false,
                Arrays.asList("Auto", "Manual")
        );

        LStringSelectAttribute attr = LogicAttributeFactory.create(
                LStringSelectAttribute.class, def);

        // Should be able to update value to a valid option
        assertTrue(attr.updateValue("Manual"));
        assertEquals("Manual", attr.getValue());

        // Should fail for invalid option
        assertFalse(attr.updateValue("Invalid"));
    }

    // ========== CommandAttrDef factory tests ==========

    @Test
    public void createCommandAttributeFromFactory() {
        CommandAttrDef def = new CommandAttrDef(
                "dispatch_command",
                AttributeClass.DISPATCH_COMMAND,
                null, null, 0,
                true,
                LCommandAttribute.class,
                false,
                Arrays.asList("ZERO_START", "SPAN_START", "ZERO_END")
        );

        LCommandAttribute attr = LogicAttributeFactory.create(
                LCommandAttribute.class, def);

        assertEquals("dispatch_command", attr.getAttributeID());
        assertEquals(AttributeClass.DISPATCH_COMMAND, attr.getAttrClass());
        assertTrue(attr.isStandalone());
        // Verify commands are set from def
        assertEquals(3, attr.getStandardCommands().size());
        assertEquals("ZERO_START", attr.getStandardCommands().get(0));
        assertEquals("SPAN_START", attr.getStandardCommands().get(1));
        assertEquals("ZERO_END", attr.getStandardCommands().get(2));
    }

    // ========== Base LogicAttributeDefine factory tests ==========

    @Test
    public void createLStringSelectAttributeWithBaseDef() {
        // Base def (no options) should still work — options will be null
        LogicAttributeDefine def = new LogicAttributeDefine(
                "test_status",
                AttributeClass.STATUS,
                null, null, 0,
                false,
                LStringSelectAttribute.class
        );

        LStringSelectAttribute attr = LogicAttributeFactory.create(
                LStringSelectAttribute.class, def);

        assertEquals("test_status", attr.getAttributeID());
        assertEquals(AttributeClass.STATUS, attr.getAttrClass());
        assertTrue(attr.isStandalone());
    }
}
