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

package com.ecat.core.Bus.event;

import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.AttrState;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * DeviceDataChangedEvent unit tests.
 *
 * <p>验证 device.data.update 事件载荷的契约：deviceId/attrId/newState 必填（null 抛 IllegalArgumentException），
 * oldState 可空（首次更新场景），且载荷是 BusPayload（可经 BusEvent&lt;T extends BusPayload&gt; 类型化发布）。
 */
public class DeviceDataChangedEventTest {

    private static AttrState<?> state(String deviceId, String attrId) {
        return AttrState.builder()
                .deviceId(deviceId)
                .attrId(attrId)
                .displayValue("5.0")
                .status(AttributeStatus.NORMAL)
                .context(EventContext.root(EventContext.Source.SYSTEM, null))
                .build();
    }

    @Test
    public void constructor_setsAllFieldsWhenOldStatePresent() {
        AttrState<?> oldState = state("dev1", "so2");
        AttrState<?> newState = state("dev1", "so2");

        DeviceDataChangedEvent event = new DeviceDataChangedEvent("dev1", "so2", oldState, newState);

        assertEquals("dev1", event.getDeviceId());
        assertEquals("so2", event.getAttrId());
        assertSame(oldState, event.getOldState());
        assertSame(newState, event.getNewState());
    }

    @Test
    public void constructor_allowsNullOldStateForFirstUpdate() {
        // 首次更新：无旧状态，oldState 为 null 是合法的
        AttrState<?> newState = state("dev1", "so2");

        DeviceDataChangedEvent event = new DeviceDataChangedEvent("dev1", "so2", null, newState);

        assertNull(event.getOldState());
        assertSame(newState, event.getNewState());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsNullDeviceId() {
        new DeviceDataChangedEvent(null, "so2", null, state("dev1", "so2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsNullAttrId() {
        new DeviceDataChangedEvent("dev1", null, null, state("dev1", "so2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsNullNewState() {
        new DeviceDataChangedEvent("dev1", "so2", state("dev1", "so2"), null);
    }

    @Test
    public void isBusPayload_soTypeSafeBusEventCompiles() {
        // 载荷实现 BusPayload，可经 BusEvent<T extends BusPayload> 类型化发布与消费
        DeviceDataChangedEvent payload = new DeviceDataChangedEvent(
                "dev1", "so2", null, state("dev1", "so2"));
        BusEvent<DeviceDataChangedEvent> event = BusEvent.of(
                "device.data.update", payload,
                EventContext.root(EventContext.Source.SYSTEM, null));

        assertSame(payload, event.getPayload());
        assertTrue("payload must be BusPayload", event.getPayload() instanceof BusPayload);
    }
}
