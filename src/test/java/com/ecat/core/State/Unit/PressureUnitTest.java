package com.ecat.core.State.Unit;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test for PressureUnit enum ratio correctness and unit conversion.
 *
 * <p>Base unit is PA (ratio=1.0). All other ratios represent
 * "1 of this unit = X PA". This is consistent with the UnitInfo
 * convention used across all unit enums.
 *
 * <p>Conversion formula: displayValue = value × (nativeUnit.ratio / toUnit.ratio)
 *
 * @author coffee
 */
public class PressureUnitTest {

    // ========== ratio 基准值验证 ==========

    @Test
    public void testBaseUnitRatio() {
        // Pa is the base unit with ratio = 1.0
        assertEquals(Double.valueOf(1.0), PressureUnit.PA.getRatio());
    }

    @Test
    public void testHpaRatio() {
        // 1 hPa = 100 Pa
        assertEquals(Double.valueOf(100.0), PressureUnit.HPA.getRatio());
    }

    @Test
    public void testKpaRatio() {
        // 1 kPa = 1000 Pa
        assertEquals(Double.valueOf(1000.0), PressureUnit.KPA.getRatio());
    }

    @Test
    public void testMpaRatio() {
        // 1 MPa = 1000000 Pa
        assertEquals(Double.valueOf(1000000.0), PressureUnit.MPA.getRatio());
    }

    @Test
    public void testAtmRatio() {
        // 1 atm = 101325 Pa
        assertEquals(Double.valueOf(101325.0), PressureUnit.ATM.getRatio());
    }

    @Test
    public void testMmhgRatio() {
        // 1 mmHg = 133.3223684 Pa (standard torr definition)
        assertEquals(Double.valueOf(133.3223684), PressureUnit.MMHG.getRatio(), 1e-6);
    }

    // ========== convertUnit 转换验证 ==========

    @Test
    public void testConvertUnit_PaToKpa() {
        Double factor = PressureUnit.PA.convertUnit(PressureUnit.KPA);
        assertEquals(Double.valueOf(0.001), factor, 1e-10);
        // 5000 Pa = 5 kPa
        assertEquals(5.0, 5000.0 * factor, 1e-6);
    }

    @Test
    public void testConvertUnit_KpaToPa() {
        Double factor = PressureUnit.KPA.convertUnit(PressureUnit.PA);
        assertEquals(Double.valueOf(1000.0), factor, 1e-10);
        // 5 kPa = 5000 Pa
        assertEquals(5000.0, 5.0 * factor, 1e-6);
    }

    @Test
    public void testConvertUnit_PaToHpa() {
        Double factor = PressureUnit.PA.convertUnit(PressureUnit.HPA);
        assertEquals(Double.valueOf(0.01), factor, 1e-10);
        // 101325 Pa = 1013.25 hPa
        assertEquals(1013.25, 101325.0 * factor, 1e-6);
    }

    @Test
    public void testConvertUnit_MmhgToKpa() {
        // 760 mmHg → kPa (standard atmospheric pressure)
        Double factor = PressureUnit.MMHG.convertUnit(PressureUnit.KPA);
        assertEquals(0.1333223684, factor, 1e-6);
        // 760 mmHg = 101.325 kPa
        double result = 760.0 * factor;
        assertEquals(101.325, result, 0.001);
    }

    @Test
    public void testConvertUnit_KpaToMmhg() {
        // 101.325 kPa → mmHg
        Double factor = PressureUnit.KPA.convertUnit(PressureUnit.MMHG);
        // 1000 / 133.3223684 ≈ 7.500617
        double result = 101.325 * factor;
        assertEquals(760.0, result, 0.01);
    }

    @Test
    public void testConvertUnit_MmhgToPa() {
        // 760 mmHg → Pa
        Double factor = PressureUnit.MMHG.convertUnit(PressureUnit.PA);
        double result = 760.0 * factor;
        assertEquals(101325.0, result, 0.01);
    }

    @Test
    public void testConvertUnit_AtmToKpa() {
        // 1 atm → kPa
        Double factor = PressureUnit.ATM.convertUnit(PressureUnit.KPA);
        double result = 1.0 * factor;
        assertEquals(101.325, result, 0.001);
    }

    // ========== 往返转换验证 ==========

    @Test
    public void testRoundTrip_Pa_Kpa() {
        double original = 101325.0;
        double toKpa = original * PressureUnit.PA.convertUnit(PressureUnit.KPA);
        double back = toKpa * PressureUnit.KPA.convertUnit(PressureUnit.PA);
        assertEquals(original, back, 1e-6);
    }

    @Test
    public void testRoundTrip_Kpa_Mmhg() {
        double original = 101.325;
        double toMmhg = original * PressureUnit.KPA.convertUnit(PressureUnit.MMHG);
        double back = toMmhg * PressureUnit.MMHG.convertUnit(PressureUnit.KPA);
        assertEquals(original, back, 0.01);
    }

    // ========== 实际业务场景验证 ==========

    @Test
    public void testRealScenario_ThermoFisher5030iq_BaroPres_MmhgToKpa() {
        // ThermoFisher 5030iq: baro_pres = 760.0 mmHg
        // LogicDevice expects kPa
        double physicalValue = 760.0; // mmHg (nativeUnit)
        double factor = PressureUnit.MMHG.convertUnit(PressureUnit.KPA);
        double displayValue = physicalValue * factor;
        // 760 mmHg ≈ 101.325 kPa (标准大气压)
        assertEquals(101.325, displayValue, 0.01);
    }

    @Test
    public void testRealScenario_TjPm_AmbientPressure_HpaToKpa() {
        // TJ PM: ambient_pressure = 1013.0 hPa
        // LogicDevice expects kPa
        double physicalValue = 1013.0; // hPa (nativeUnit)
        double factor = PressureUnit.HPA.convertUnit(PressureUnit.KPA);
        double displayValue = physicalValue * factor;
        // 1013 hPa = 101.3 kPa
        assertEquals(101.3, displayValue, 0.01);
    }

    @Test
    public void testRealScenario_SailheroPm_AmbientPressure_HpaToKpa() {
        // Sailhero PM: ambient_pressure = 102.0 hPa → 但实际是 102.0 kPa level
        // 如果 nativeUnit 是 hPa: 102.0 hPa = 10.2 kPa (不太合理)
        // 如果 nativeUnit 是 kPa: 102.0 kPa (合理)
        // 验证 hPa → kPa 的转换本身是正确的
        double factor = PressureUnit.HPA.convertUnit(PressureUnit.KPA);
        assertEquals(0.1, factor, 1e-10);
    }

    // ========== 国际化和基础方法验证 ==========

    @Test
    public void testUnitCategory() {
        assertEquals("pressure", PressureUnit.PA.getUnitCategory());
    }

    @Test
    public void testEnumNames() {
        assertEquals("pa", PressureUnit.PA.getEnumName());
        assertEquals("hpa", PressureUnit.HPA.getEnumName());
        assertEquals("kpa", PressureUnit.KPA.getEnumName());
        assertEquals("mpa", PressureUnit.MPA.getEnumName());
        assertEquals("atm", PressureUnit.ATM.getEnumName());
        assertEquals("mmhg", PressureUnit.MMHG.getEnumName());
    }

    @Test
    public void testGetNames() {
        assertEquals("Pa", PressureUnit.PA.getName());
        assertEquals("hPa", PressureUnit.HPA.getName());
        assertEquals("kPa", PressureUnit.KPA.getName());
        assertEquals("MPa", PressureUnit.MPA.getName());
        assertEquals("atm", PressureUnit.ATM.getName());
        assertEquals("mmHg", PressureUnit.MMHG.getName());
    }

    @Test
    public void testSameUnitConversion() {
        Double factor = PressureUnit.KPA.convertUnit(PressureUnit.KPA);
        assertEquals(Double.valueOf(1.0), factor);
    }

    @Test
    public void testAllUnitsImplementInternationalizedUnit() {
        for (PressureUnit unit : PressureUnit.values()) {
            assertTrue(unit.getName() + " should implement InternationalizedUnit",
                    unit instanceof InternationalizedUnit);
        }
    }
}
