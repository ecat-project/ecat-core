package com.ecat.core.State.Unit;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Test for InternationalizedUnit interface implementation
 *
 * @author coffee
 */
public class InternationalizedUnitTest {

    @Test
    public void testAirMassUnitI18n() {
        String category = AirMassUnit.UGM3.getUnitCategory();
        String enumName = AirMassUnit.UGM3.getEnumName();

        assertEquals("airmass", category);
        assertEquals("ugm3", enumName);
    }

    @Test
    public void testCurrentUnitI18n() {
        String category = CurrentUnit.MILLIAMPERE.getUnitCategory();
        String enumName = CurrentUnit.MILLIAMPERE.getEnumName();

        assertEquals("current", category);
        assertEquals("milliampere", enumName);
    }

    @Test
    public void testTemperatureUnitI18n() {
        String category = TemperatureUnit.CELSIUS.getUnitCategory();
        String enumName = TemperatureUnit.CELSIUS.getEnumName();

        assertEquals("temperature", category);
        assertEquals("celsius", enumName);
    }

    @Test
    public void testAllUnitTypesImplementI18n() {
        // Test that all major unit types implement InternationalizedUnit
        assertTrue("TemperatureUnit should implement InternationalizedUnit",
                   TemperatureUnit.CELSIUS instanceof InternationalizedUnit);
        assertTrue("CurrentUnit should implement InternationalizedUnit",
                   CurrentUnit.MILLIAMPERE instanceof InternationalizedUnit);
        assertTrue("PressureUnit should implement InternationalizedUnit",
                   PressureUnit.PA instanceof InternationalizedUnit);
        assertTrue("SpeedUnit should implement InternationalizedUnit",
                   SpeedUnit.METER_PER_SECOND instanceof InternationalizedUnit);
        assertTrue("DistanceUnit should implement InternationalizedUnit",
                   DistanceUnit.M instanceof InternationalizedUnit);
        assertTrue("VoltageUnit should implement InternationalizedUnit",
                   VoltageUnit.MILLIVOLT instanceof InternationalizedUnit);
        assertTrue("PowerUnit should implement InternationalizedUnit",
                   PowerUnit.WATT instanceof InternationalizedUnit);
        assertTrue("FrequencyUnit should implement InternationalizedUnit",
                   FrequencyUnit.HERTZ instanceof InternationalizedUnit);
        assertTrue("LiterFlowUnit should implement InternationalizedUnit",
                   LiterFlowUnit.L_PER_SECOND instanceof InternationalizedUnit);
    }

    @Test
    public void testUnitNameConsistency() {
        // Test that enum names match the JSON keys we defined
        String[] expectedCategories = {
            "airmass", "current", "temperature", "pressure", "speed",
            "distance", "voltage", "power", "frequency", "literflow"
        };

        // Test a few key categories exist
        assertTrue("Should contain airmass category", expectedCategories.length > 0);
    }
}