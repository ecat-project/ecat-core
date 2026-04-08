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

import java.time.Instant;

import static org.junit.Assert.*;

/**
 * BusTopic.DispatchMode and resolveMode() unit tests
 */
public class BusTopicDispatchModeTest {

    // --- DispatchMode enum values ---

    @Test
    public void dispatchMode_hasExactlyTwoValues() {
        assertEquals(2, BusTopic.DispatchMode.values().length);
    }

    @Test
    public void dispatchMode_containsSyncAndAsync() {
        BusTopic.DispatchMode[] modes = BusTopic.DispatchMode.values();
        boolean foundSync = false;
        boolean foundAsync = false;
        for (BusTopic.DispatchMode mode : modes) {
            if (mode == BusTopic.DispatchMode.SYNC) foundSync = true;
            if (mode == BusTopic.DispatchMode.ASYNC) foundAsync = true;
        }
        assertTrue("SYNC should exist", foundSync);
        assertTrue("ASYNC should exist", foundAsync);
    }

    // --- resolveMode() tests ---

    @Test
    public void resolveMode_returnsSyncForIntegrationAllLoaded() {
        assertEquals(BusTopic.DispatchMode.SYNC, BusTopic.resolveMode("integration.all_loaded"));
    }

    @Test
    public void resolveMode_returnsSyncForLogicDeviceAllLoaded() {
        assertEquals(BusTopic.DispatchMode.SYNC, BusTopic.resolveMode("logic_device.all_loaded"));
    }

    @Test
    public void resolveMode_returnsAsyncForDeviceDataUpdate() {
        assertEquals(BusTopic.DispatchMode.ASYNC, BusTopic.resolveMode("device.data.update"));
    }

    @Test
    public void resolveMode_returnsAsyncForUnknownTopic() {
        assertEquals(BusTopic.DispatchMode.ASYNC, BusTopic.resolveMode("unknown.topic"));
    }

    // --- New enum values exist with correct topic names ---

    @Test
    public void integrationsAllLoaded_exists() {
        assertNotNull(BusTopic.INTEGRATIONS_ALL_LOADED);
    }

    @Test
    public void integrationsAllLoaded_hasCorrectTopicName() {
        assertEquals("integration.all_loaded", BusTopic.INTEGRATIONS_ALL_LOADED.getTopicName());
    }

    @Test
    public void integrationsAllLoaded_hasInstantDataClass() {
        assertEquals(Instant.class, BusTopic.INTEGRATIONS_ALL_LOADED.getDataClass());
    }

    @Test
    public void logicDevicesAllLoaded_exists() {
        assertNotNull(BusTopic.LOGIC_DEVICES_ALL_LOADED);
    }

    @Test
    public void logicDevicesAllLoaded_hasCorrectTopicName() {
        assertEquals("logic_device.all_loaded", BusTopic.LOGIC_DEVICES_ALL_LOADED.getTopicName());
    }

    @Test
    public void logicDevicesAllLoaded_hasInstantDataClass() {
        assertEquals(Instant.class, BusTopic.LOGIC_DEVICES_ALL_LOADED.getDataClass());
    }
}
