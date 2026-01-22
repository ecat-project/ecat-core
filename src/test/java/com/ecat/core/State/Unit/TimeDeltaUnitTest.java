package com.ecat.core.State.Unit;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * TimeDeltaUnit类的单元测试
 */
public class TimeDeltaUnitTest {

    @Test
    public void testUnitNames() {
        assertEquals("ms", TimeDeltaUnit.MILLISECOND.getName());
        assertEquals("s", TimeDeltaUnit.SECOND.getName());
        assertEquals("min", TimeDeltaUnit.MINUTE.getName());
        assertEquals("h", TimeDeltaUnit.HOUR.getName());
    }

    @Test
    public void testUnitCategories() {
        assertEquals("time_delta", TimeDeltaUnit.MILLISECOND.getUnitCategory());
        assertEquals("time_delta", TimeDeltaUnit.SECOND.getUnitCategory());
        assertEquals("time_delta", TimeDeltaUnit.MINUTE.getUnitCategory());
        assertEquals("time_delta", TimeDeltaUnit.HOUR.getUnitCategory());
    }

    @Test
    public void testUnitRatios() {
        assertEquals(Double.valueOf(1.0), TimeDeltaUnit.MILLISECOND.getRatio());
        assertEquals(Double.valueOf(1000.0), TimeDeltaUnit.SECOND.getRatio());
        assertEquals(Double.valueOf(60000.0), TimeDeltaUnit.MINUTE.getRatio());
        assertEquals(Double.valueOf(3600000.0), TimeDeltaUnit.HOUR.getRatio());
    }

    @Test
    public void testEnumNames() {
        assertEquals("millisecond", TimeDeltaUnit.MILLISECOND.getEnumName());
        assertEquals("second", TimeDeltaUnit.SECOND.getEnumName());
        assertEquals("minute", TimeDeltaUnit.MINUTE.getEnumName());
        assertEquals("hour", TimeDeltaUnit.HOUR.getEnumName());
    }

    @Test
    public void testToString() {
        assertEquals("ms", TimeDeltaUnit.MILLISECOND.toString());
        assertEquals("s", TimeDeltaUnit.SECOND.toString());
        assertEquals("min", TimeDeltaUnit.MINUTE.toString());
        assertEquals("h", TimeDeltaUnit.HOUR.toString());
    }
}