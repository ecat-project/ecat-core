package com.ecat.core.State.Unit;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * RatioUnit类的单元测试
 */
public class RatioUnitTest {

    @Test
    public void testUnitNames() {
        assertEquals("%", RatioUnit.PERCENT.getName());
        assertEquals("‰", RatioUnit.PER_MILLE.getName());
    }

    @Test
    public void testUnitCategories() {
        assertEquals("ratio", RatioUnit.PERCENT.getUnitCategory());
        assertEquals("ratio", RatioUnit.PER_MILLE.getUnitCategory());
    }

    @Test
    public void testUnitRatios() {
        assertEquals(Double.valueOf(1.0), RatioUnit.PERCENT.getRatio());
        assertEquals(Double.valueOf(0.1), RatioUnit.PER_MILLE.getRatio());
    }

    @Test
    public void testConvertUnit() {
        assertEquals(Double.valueOf(10.0), RatioUnit.PERCENT.convertUnit(RatioUnit.PER_MILLE));
        assertEquals(Double.valueOf(0.1), RatioUnit.PER_MILLE.convertUnit(RatioUnit.PERCENT));

        // 12.3% = 123‰
        assertEquals(123.0, 12.3 * RatioUnit.PERCENT.convertUnit(RatioUnit.PER_MILLE), 0.0001);
    }

    @Test
    public void testEnumNames() {
        assertEquals("percent", RatioUnit.PERCENT.getEnumName());
        assertEquals("per_mille", RatioUnit.PER_MILLE.getEnumName());
    }

    @Test
    public void testToString() {
        assertEquals("%", RatioUnit.PERCENT.toString());
        assertEquals("‰", RatioUnit.PER_MILLE.toString());
    }
}
