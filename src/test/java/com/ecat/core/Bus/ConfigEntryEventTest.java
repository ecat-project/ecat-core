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

package com.ecat.core.Bus;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ConfigEntryEvent unit tests
 */
public class ConfigEntryEventTest {

    // --- Constructor and getters ---

    @Test
    public void constructor_setsAllFieldsCorrectly() {
        ConfigEntryEvent event = new ConfigEntryEvent(
                "entry-123",
                "com.ecat:integration-sailhero",
                ConfigEntryEvent.Action.CREATE
        );

        assertEquals("entry-123", event.getEntryId());
        assertEquals("com.ecat:integration-sailhero", event.getCoordinate());
        assertEquals(ConfigEntryEvent.Action.CREATE, event.getAction());
    }

    @Test
    public void getEntryId_returnsCorrectValue() {
        ConfigEntryEvent event = new ConfigEntryEvent(
                "my-entry-id",
                "com.ecat:integration-test",
                ConfigEntryEvent.Action.RECONFIGURE
        );
        assertEquals("my-entry-id", event.getEntryId());
    }

    @Test
    public void getCoordinate_returnsCorrectValue() {
        ConfigEntryEvent event = new ConfigEntryEvent(
                "entry-1",
                "com.ecat:integration-saimosen",
                ConfigEntryEvent.Action.ENABLE
        );
        assertEquals("com.ecat:integration-saimosen", event.getCoordinate());
    }

    @Test
    public void getAction_returnsCorrectValue() {
        ConfigEntryEvent event = new ConfigEntryEvent(
                "entry-1",
                "com.ecat:integration-test",
                ConfigEntryEvent.Action.DISABLE
        );
        assertEquals(ConfigEntryEvent.Action.DISABLE, event.getAction());
    }

    // --- Action enum values ---

    @Test
    public void action_hasExactlyFiveValues() {
        assertEquals(5, ConfigEntryEvent.Action.values().length);
    }

    @Test
    public void action_containsAllExpectedValues() {
        ConfigEntryEvent.Action[] actions = ConfigEntryEvent.Action.values();
        boolean foundCreate = false;
        boolean foundReconfigure = false;
        boolean foundRemove = false;
        boolean foundEnable = false;
        boolean foundDisable = false;

        for (ConfigEntryEvent.Action action : actions) {
            switch (action) {
                case CREATE:      foundCreate = true; break;
                case RECONFIGURE: foundReconfigure = true; break;
                case REMOVE:      foundRemove = true; break;
                case ENABLE:      foundEnable = true; break;
                case DISABLE:     foundDisable = true; break;
            }
        }

        assertTrue("CREATE should exist", foundCreate);
        assertTrue("RECONFIGURE should exist", foundReconfigure);
        assertTrue("REMOVE should exist", foundRemove);
        assertTrue("ENABLE should exist", foundEnable);
        assertTrue("DISABLE should exist", foundDisable);
    }

    // --- Verify each action can be used in an event ---

    @Test
    public void action_CREATE_worksInEvent() {
        ConfigEntryEvent event = new ConfigEntryEvent("id", "coord", ConfigEntryEvent.Action.CREATE);
        assertEquals(ConfigEntryEvent.Action.CREATE, event.getAction());
    }

    @Test
    public void action_REMOVE_worksInEvent() {
        ConfigEntryEvent event = new ConfigEntryEvent("id", "coord", ConfigEntryEvent.Action.REMOVE);
        assertEquals(ConfigEntryEvent.Action.REMOVE, event.getAction());
    }
}
