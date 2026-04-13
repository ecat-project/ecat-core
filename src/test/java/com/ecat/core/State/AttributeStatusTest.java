package com.ecat.core.State;

import org.junit.Test;
import static org.junit.Assert.*;

public class AttributeStatusTest {

    @Test
    public void testFromId_knownValues() {
        assertEquals(AttributeStatus.NORMAL, AttributeStatus.fromId(1));
        assertEquals(AttributeStatus.ALARM, AttributeStatus.fromId(102));
        assertEquals(AttributeStatus.EMPTY, AttributeStatus.fromId(-1));
        assertEquals(AttributeStatus.MAINTENANCE, AttributeStatus.fromId(104));
        assertEquals(AttributeStatus.CALIBRATION, AttributeStatus.fromId(107));
    }

    @Test
    public void testFromId_unknownReturnsEmpty() {
        assertEquals(AttributeStatus.EMPTY, AttributeStatus.fromId(999));
        assertEquals(AttributeStatus.EMPTY, AttributeStatus.fromId(0));
    }
}
