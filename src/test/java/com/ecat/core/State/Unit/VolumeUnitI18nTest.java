package com.ecat.core.State.Unit;

import com.ecat.core.I18n.ResourceLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * VolumeUnit类的国际化单元测试
 */
public class VolumeUnitI18nTest {

    @Before
    public void setUp() {
        ResourceLoader.setLoadI18nResources(false);
    }

    @After
    public void tearDown() {
        ResourceLoader.setLoadI18nResources(true);
    }

    @Test
    public void testVolumeUnitDisplayName() {
        assertEquals("volume", VolumeUnit.L.getUnitCategory());
        assertEquals("l", VolumeUnit.L.getEnumName());

        String displayName = VolumeUnit.L.getDisplayName();
        assertNotNull("显示名称不应为null", displayName);
        assertEquals("升的单位符号应正确", "L", displayName);

        assertTrue("VolumeUnit应实现InternationalizedUnit接口",
                   VolumeUnit.L instanceof InternationalizedUnit);
    }

    @Test
    public void testAllVolumeUnitsDisplayName() {
        assertEquals("mL", VolumeUnit.ML.getDisplayName());
        assertEquals("L", VolumeUnit.L.getDisplayName());
        assertEquals("cm³", VolumeUnit.CM3.getDisplayName());
        assertEquals("m³", VolumeUnit.M3.getDisplayName());
    }
}
