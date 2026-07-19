package com.ecat.core.Bus.event;

import com.ecat.core.Bus.BusTopic;
import org.junit.Test;

import static org.junit.Assert.*;

/** 00-core Task 2：DEVICE_LIFECYCLE topic + DeviceLifecycleEvent。 */
public class DeviceLifecycleEventTest {

    @Test
    public void event_carriesFields_isImmutable() {
        DeviceLifecycleEvent e = new DeviceLifecycleEvent(
                "dev-1", "com.ecat:integration-x", "ent-1", DeviceLifecycleEvent.Action.CREATE);
        assertEquals("dev-1", e.getDeviceId());
        assertEquals("com.ecat:integration-x", e.getCoordinate());
        assertEquals("ent-1", e.getEntryId());
        assertEquals(DeviceLifecycleEvent.Action.CREATE, e.getAction());
    }

    @Test
    public void nullDeviceId_orNullAction_rejected() {
        try {
            new DeviceLifecycleEvent(null, "c", "e", DeviceLifecycleEvent.Action.CREATE);
            fail("null deviceId should throw");
        } catch (IllegalArgumentException ok) {}
        try {
            new DeviceLifecycleEvent("dev-1", "c", "e", null);
            fail("null action should throw");
        } catch (IllegalArgumentException ok) {}
    }

    @Test
    public void topic_registered_andSync() {
        assertEquals("device.lifecycle", BusTopic.DEVICE_LIFECYCLE.getTopicName());
        assertSame(DeviceLifecycleEvent.class, BusTopic.DEVICE_LIFECYCLE.getDataClass());
        // lifecycle 事件需有序 → SYNC 派发
        assertEquals(BusTopic.DispatchMode.SYNC,
                BusTopic.resolveMode(BusTopic.DEVICE_LIFECYCLE.getTopicName()));
    }

    @Test
    public void entryId_canBeNull_forGatewayChildWithoutEntry() {
        // 网关子设备 entryId 通常=网关 entryId；此字段允许 null（无 entry 的极端设备）
        DeviceLifecycleEvent e = new DeviceLifecycleEvent(
                "dev-1", "com.ecat:integration-x", null, DeviceLifecycleEvent.Action.REMOVE);
        assertNull(e.getEntryId());
    }
}
