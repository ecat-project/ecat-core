package com.ecat.core.Bus.event;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * EventContext 溯源上下文单测——root/chain 因果链与 null 校验。
 */
public class EventContextTest {

    @Test
    public void rootHasNoParent() {
        EventContext c = EventContext.root(EventContext.Source.USER_ACTION, "u-1");
        assertNotNull(c.getUuid());
        assertNull(c.getParentUuid());
        assertEquals(EventContext.Source.USER_ACTION, c.getSource());
        assertEquals("u-1", c.getUserId());
    }

    @Test
    public void chainInheritsParentUuid() {
        EventContext parent = EventContext.root(EventContext.Source.DEVICE_POLL, null);
        EventContext child = EventContext.chain(parent, EventContext.Source.LOGIC_REPUBLISH, null);
        assertEquals(parent.getUuid(), child.getParentUuid());
        assertNotEquals(parent.getUuid(), child.getUuid());
        assertEquals(EventContext.Source.LOGIC_REPUBLISH, child.getSource());
    }

    @Test
    public void nullSourceThrows() {
        try {
            EventContext.root(null, null);
            fail("source=null 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException ok) { }
    }
}
