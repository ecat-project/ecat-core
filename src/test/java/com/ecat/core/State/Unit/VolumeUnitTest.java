package com.ecat.core.State.Unit;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * VolumeUnit类的单元测试
 */
public class VolumeUnitTest {

    @Test
    public void testUnitNames() {
        assertEquals("mL", VolumeUnit.ML.getName());
        assertEquals("L", VolumeUnit.L.getName());
        assertEquals("cm³", VolumeUnit.CM3.getName());
        assertEquals("m³", VolumeUnit.M3.getName());
    }

    @Test
    public void testUnitCategories() {
        assertEquals("volume", VolumeUnit.ML.getUnitCategory());
        assertEquals("volume", VolumeUnit.L.getUnitCategory());
        assertEquals("volume", VolumeUnit.CM3.getUnitCategory());
        assertEquals("volume", VolumeUnit.M3.getUnitCategory());
    }

    @Test
    public void testUnitRatios() {
        assertEquals(Double.valueOf(1.0), VolumeUnit.ML.getRatio());
        assertEquals(Double.valueOf(1000.0), VolumeUnit.L.getRatio());
        assertEquals(Double.valueOf(1.0), VolumeUnit.CM3.getRatio());
        assertEquals(Double.valueOf(1000000.0), VolumeUnit.M3.getRatio());
    }

    @Test
    public void testEnumNames() {
        assertEquals("ml", VolumeUnit.ML.getEnumName());
        assertEquals("l", VolumeUnit.L.getEnumName());
        assertEquals("cm3", VolumeUnit.CM3.getEnumName());
        assertEquals("m3", VolumeUnit.M3.getEnumName());
    }

    @Test
    public void testToString() {
        assertEquals("mL", VolumeUnit.ML.toString());
        assertEquals("L", VolumeUnit.L.toString());
        assertEquals("cm³", VolumeUnit.CM3.toString());
        assertEquals("m³", VolumeUnit.M3.toString());
    }
}
