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
 * CommandAttrDef 单元测试
 *
 * @author coffee
 */
public class CommandAttrDefTest {

    @Test
    public void constructorStoresCommands() {
        List<String> commands = Arrays.asList("ZERO_START", "ZERO_END", "SPAN_START");
        CommandAttrDef def = new CommandAttrDef(
                "dispatch_command",
                AttributeClass.DISPATCH_COMMAND,
                null,
                null,
                0,
                true,
                LCommandAttribute.class,
                false,
                commands
        );

        assertEquals("dispatch_command", def.getAttrId());
        assertEquals(AttributeClass.DISPATCH_COMMAND, def.getAttrClass());
        assertNull(def.getNativeUnit());
        assertNull(def.getDisplayUnit());
        assertEquals(0, def.getDisplayPrecision());
        assertTrue(def.isValueChangeable());
        assertEquals(LCommandAttribute.class, def.getAttrClassType());
        assertFalse(def.isMapable());
        assertSame(commands, def.getCommands());
        assertEquals(3, def.getCommands().size());
        assertEquals("ZERO_START", def.getCommands().get(0));
        assertEquals("ZERO_END", def.getCommands().get(1));
        assertEquals("SPAN_START", def.getCommands().get(2));
    }

    @Test
    public void constructorWithAllNullUnits() {
        List<String> commands = Arrays.asList("START", "STOP");
        CommandAttrDef def = new CommandAttrDef(
                "control_cmd",
                AttributeClass.DISPATCH_COMMAND,
                null,
                null,
                0,
                false,
                LCommandAttribute.class,
                true,
                commands
        );

        assertEquals("control_cmd", def.getAttrId());
        assertEquals(AttributeClass.DISPATCH_COMMAND, def.getAttrClass());
        assertNull(def.getNativeUnit());
        assertNull(def.getDisplayUnit());
        assertEquals(0, def.getDisplayPrecision());
        assertFalse(def.isValueChangeable());
        assertEquals(LCommandAttribute.class, def.getAttrClassType());
        assertTrue(def.isMapable());
        assertEquals(2, def.getCommands().size());
    }

    @Test
    public void valueChangeableFalseForCommands() {
        // Command attributes typically have valueChangeable=false
        // since commands are one-shot actions, not persistent values
        List<String> commands = Arrays.asList("ZERO_START");
        CommandAttrDef def = new CommandAttrDef(
                "cmd", AttributeClass.DISPATCH_COMMAND, null, null, 0,
                false, LCommandAttribute.class, false, commands);

        assertFalse("Command attr valueChangeable should be false", def.isValueChangeable());
    }

    @Test
    public void mapableCanBeTrue() {
        List<String> commands = Arrays.asList("ZERO_START");
        CommandAttrDef def = new CommandAttrDef(
                "cmd", AttributeClass.DISPATCH_COMMAND, null, null, 0,
                false, LCommandAttribute.class, true, commands);

        assertTrue(def.isMapable());
        // displayable should still default to true from super 7-param constructor
        assertTrue(def.isDisplayable());
    }

    @Test
    public void extendsLogicAttributeDefine() {
        List<String> commands = Arrays.asList("START");
        CommandAttrDef def = new CommandAttrDef(
                "test", AttributeClass.DISPATCH_COMMAND, null, null, 0,
                false, LCommandAttribute.class, false, commands);
        assertTrue(def instanceof LogicAttributeDefine);
    }
}
