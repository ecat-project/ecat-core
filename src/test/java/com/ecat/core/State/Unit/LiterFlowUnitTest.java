package com.ecat.core.State.Unit;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test for LiterFlowUnit enum ratio correctness and unit conversion.
 *
 * <p>Base unit is L_PER_HOUR (ratio=1.0). All other ratios represent
 * "1 of this unit = X L_PER_HOUR". This is consistent with PressureUnit
 * where base PA (ratio=1.0) and 1 kPa = 1000 Pa.
 *
 * <p>Conversion formula: displayValue = value × (nativeUnit.ratio / toUnit.ratio)
 *
 * @author coffee
 */
public class LiterFlowUnitTest {

    // ========== ratio 基准值验证 ==========

    @Test
    public void testBaseUnitRatio() {
        // L/h is the base unit with ratio = 1.0
        assertEquals("L/h should be the base unit",
                Double.valueOf(1.0), LiterFlowUnit.L_PER_HOUR.getRatio());
    }

    @Test
    public void testLPerMinuteRatio() {
        // 1 L/min = 60 L/h
        assertEquals("L/min ratio should be 60.0",
                Double.valueOf(60.0), LiterFlowUnit.L_PER_MINUTE.getRatio());
    }

    @Test
    public void testMlPerMinuteRatio() {
        // 1 ml/min = 1/1000 L/min = 0.06 L/h
        assertEquals("ml/min ratio should be 0.06",
                Double.valueOf(0.06), LiterFlowUnit.ML_PER_MINUTE.getRatio(), 1e-6);
    }

    @Test
    public void testLPerSecondRatio() {
        // 1 L/s = 3600 L/h
        assertEquals("L/s ratio should be 3600.0",
                Double.valueOf(3600.0), LiterFlowUnit.L_PER_SECOND.getRatio());
    }

    // ========== convertUnit 转换验证 ==========

    @Test
    public void testConvertUnit_LPerHourToLPerMinute() {
        // L/h → L/min: factor = 1.0/60.0 = 0.01667
        Double factor = LiterFlowUnit.L_PER_HOUR.convertUnit(LiterFlowUnit.L_PER_MINUTE);
        assertEquals(Double.valueOf(1.0 / 60.0), factor, 1e-6);
        // 0.97 L/h ÷ 60 = 0.01617 L/min
        assertEquals(0.01617, 0.97 * factor, 1e-4);
    }

    @Test
    public void testConvertUnit_LPerMinuteToLPerHour() {
        // L/min → L/h: factor = 60.0/1.0 = 60
        Double factor = LiterFlowUnit.L_PER_MINUTE.convertUnit(LiterFlowUnit.L_PER_HOUR);
        assertEquals(Double.valueOf(60.0), factor, 1e-6);
        // 16.7 L/min × 60 = 1002 L/h
        assertEquals(1002.0, 16.7 * factor, 0.01);
    }

    @Test
    public void testConvertUnit_MlPerMinuteToLPerMinute() {
        // ml/min → L/min: factor = 0.06/60.0 = 0.001
        Double factor = LiterFlowUnit.ML_PER_MINUTE.convertUnit(LiterFlowUnit.L_PER_MINUTE);
        assertEquals(Double.valueOf(0.001), factor, 1e-6);
        // 500 ml/min ÷ 1000 = 0.5 L/min
        assertEquals(0.5, 500.0 * factor, 1e-6);
    }

    @Test
    public void testConvertUnit_LPerSecondToLPerMinute() {
        // L/s → L/min: factor = 3600.0/60.0 = 60
        Double factor = LiterFlowUnit.L_PER_SECOND.convertUnit(LiterFlowUnit.L_PER_MINUTE);
        assertEquals(Double.valueOf(60.0), factor, 1e-6);
        // 1 L/s = 60 L/min
        assertEquals(60.0, 1.0 * factor, 1e-6);
    }

    @Test
    public void testConvertUnit_LPerSecondToLPerHour() {
        // L/s → L/h: factor = 3600.0/1.0 = 3600
        Double factor = LiterFlowUnit.L_PER_SECOND.convertUnit(LiterFlowUnit.L_PER_HOUR);
        assertEquals(Double.valueOf(3600.0), factor, 1e-6);
        // 1 L/s = 3600 L/h
        assertEquals(3600.0, 1.0 * factor, 1e-6);
    }

    @Test
    public void testConvertUnit_MlPerMinuteToLPerHour() {
        // ml/min → L/h: factor = 0.06/1.0 = 0.06
        Double factor = LiterFlowUnit.ML_PER_MINUTE.convertUnit(LiterFlowUnit.L_PER_HOUR);
        assertEquals(Double.valueOf(0.06), factor, 1e-6);
        // 500 ml/min × 0.06 = 30 L/h
        assertEquals(30.0, 500.0 * factor, 1e-6);
    }

    // ========== 往返转换验证 ==========

    @Test
    public void testRoundTrip_LPerHour_LPerMinute() {
        double original = 0.97;
        double toMin = original * LiterFlowUnit.L_PER_HOUR.convertUnit(LiterFlowUnit.L_PER_MINUTE);
        double back = toMin * LiterFlowUnit.L_PER_MINUTE.convertUnit(LiterFlowUnit.L_PER_HOUR);
        assertEquals(original, back, 1e-10);
    }

    @Test
    public void testRoundTrip_LPerMinute_MlPerMinute() {
        double original = 500.0;
        double toMl = original * LiterFlowUnit.L_PER_MINUTE.convertUnit(LiterFlowUnit.ML_PER_MINUTE);
        double back = toMl * LiterFlowUnit.ML_PER_MINUTE.convertUnit(LiterFlowUnit.L_PER_MINUTE);
        assertEquals(original, back, 1e-10);
    }

    @Test
    public void testRoundTrip_LPerHour_LPerSecond() {
        double original = 3600.0;
        double toSec = original * LiterFlowUnit.L_PER_HOUR.convertUnit(LiterFlowUnit.L_PER_SECOND);
        double back = toSec * LiterFlowUnit.L_PER_SECOND.convertUnit(LiterFlowUnit.L_PER_HOUR);
        assertEquals(original, back, 1e-10);
    }

    // ========== 实际业务场景验证 ==========

    @Test
    public void testRealScenario_TjSo2_SampleFlow_LPerHourToLPerMinute() {
        // TJ SO2: sam_flow = 0.97 L/h, LogicDevice expects L/min
        double physicalValue = 0.97; // L/h (nativeUnit)
        double factor = LiterFlowUnit.L_PER_HOUR.convertUnit(LiterFlowUnit.L_PER_MINUTE);
        double displayValue = physicalValue * factor;
        // 0.97 / 60 ≈ 0.01617
        assertEquals(0.01617, displayValue, 1e-4);
    }

    @Test
    public void testRealScenario_SailheroPm_StandardFlow_MlPerMinuteToLPerMinute() {
        // Sailhero PM: standard_flow = 500 ml/min, LogicDevice expects L/min
        double physicalValue = 500.0; // ml/min (nativeUnit)
        double factor = LiterFlowUnit.ML_PER_MINUTE.convertUnit(LiterFlowUnit.L_PER_MINUTE);
        double displayValue = physicalValue * factor;
        // 500 / 1000 = 0.5 L/min
        assertEquals(0.5, displayValue, 1e-6);
    }

    @Test
    public void testRealScenario_SailheroPm_Flow_LPerMinuteToLPerHour() {
        // Sailhero PM: flow = 16.7 L/min, displayed as L/h
        double physicalValue = 16.7; // L/min (nativeUnit)
        double factor = LiterFlowUnit.L_PER_MINUTE.convertUnit(LiterFlowUnit.L_PER_HOUR);
        double displayValue = physicalValue * factor;
        // 16.7 × 60 = 1002 L/h
        assertEquals(1002.0, displayValue, 0.01);
    }

    // ========== 国际化和基础方法验证 ==========

    @Test
    public void testUnitCategory() {
        assertEquals("literflow", LiterFlowUnit.L_PER_HOUR.getUnitCategory());
    }

    @Test
    public void testEnumName() {
        assertEquals("l_per_hour", LiterFlowUnit.L_PER_HOUR.getEnumName());
        assertEquals("l_per_minute", LiterFlowUnit.L_PER_MINUTE.getEnumName());
        assertEquals("ml_per_minute", LiterFlowUnit.ML_PER_MINUTE.getEnumName());
        assertEquals("l_per_second", LiterFlowUnit.L_PER_SECOND.getEnumName());
    }

    @Test
    public void testGetName() {
        assertEquals("L/h", LiterFlowUnit.L_PER_HOUR.getName());
        assertEquals("L/min", LiterFlowUnit.L_PER_MINUTE.getName());
        assertEquals("ml/min", LiterFlowUnit.ML_PER_MINUTE.getName());
        assertEquals("L/s", LiterFlowUnit.L_PER_SECOND.getName());
    }

    @Test
    public void testSameUnitConversion() {
        // Converting to the same unit should return 1.0
        Double factor = LiterFlowUnit.L_PER_MINUTE.convertUnit(LiterFlowUnit.L_PER_MINUTE);
        assertEquals(Double.valueOf(1.0), factor);
    }
}
