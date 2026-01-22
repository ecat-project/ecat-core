package com.ecat.core.State.Unit;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the AirMassUnit enum.
 * 
 * This test class verifies the functionality of the AirMassUnit enum,
 * including its methods and properties.
 * 
 * @author coffee
 */
public class AirMassUnitTest {
    @Test
    public void testEnumValues() {
        AirMassUnit[] units = AirMassUnit.UGM3.getClass().getEnumConstants();
        assertEquals(2, units.length);
        assertEquals(AirMassUnit.UGM3, units[0]);
        assertEquals(AirMassUnit.MGM3, units[1]);
    }

    @Test
    public void testGetName() {
        assertEquals("ug/m3", AirMassUnit.UGM3.getName());
        assertEquals("mg/m3", AirMassUnit.MGM3.getName());
    }

    @Test
    public void testGetDisplayName() {
        assertEquals("ug/m3", AirMassUnit.UGM3.getDisplayName());
        assertEquals("mg/m3", AirMassUnit.MGM3.getDisplayName());
    }

    @Test
    public void testGetRatio() {
        assertEquals(1000.0, AirMassUnit.UGM3.getRatio(), 0.0001);
        assertEquals(1000000.0, AirMassUnit.MGM3.getRatio(), 0.0001);
    }

    @Test
    public void testToString() {
        assertEquals("ug/m3", AirMassUnit.UGM3.toString());
        assertEquals("mg/m3", AirMassUnit.MGM3.toString());
    }

    @Test
    public void testNullEnum() {
        try {
            AirMassUnit unit = null;
            unit.getName();
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }
}
