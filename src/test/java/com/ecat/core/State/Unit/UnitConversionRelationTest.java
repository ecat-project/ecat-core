package com.ecat.core.State.Unit;

import com.ecat.core.State.UnitInfo;
import com.ecat.core.State.UnitConversions.SameUnitClassConverter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * 单位枚举线性换算关系守卫。
 *
 * <p>ratio 只要求同一枚举类内使用同一刻度；本测试用代表性物理等式锁定
 * {@code value * sourceRatio / targetRatio} 的方向，防止比例单位等常见反向错误回归。</p>
 */
public class UnitConversionRelationTest {

    private static final double DELTA = 0.000000001;

    private static void assertConversion(double value, UnitInfo from, UnitInfo to,
                                         double expected, String relation) {
        assertEquals(relation, expected,
                SameUnitClassConverter.convert(value, from, to), DELTA);
    }

    @Test
    public void concentrationUnitsConvertCorrectly() {
        assertConversion(1000, AirMassUnit.UGM3, AirMassUnit.MGM3, 1,
                "1000 ug/m3 = 1 mg/m3");
        assertConversion(1, AirVolumeUnit.PPM, AirVolumeUnit.PPB, 1000,
                "1 ppm = 1000 ppb");
        assertConversion(1, AirVolumeUnit.PPM, AirVolumeUnit.UMOL_PER_MOL, 1,
                "1 ppm = 1 umol/mol");
        assertConversion(1, AirVolumeUnit.PPB, AirVolumeUnit.NMOL_PER_MOL, 1,
                "1 ppb = 1 nmol/mol");
    }

    @Test
    public void electricalAndPowerUnitsConvertCorrectly() {
        assertConversion(1, CurrentUnit.AMPERE, CurrentUnit.MILLIAMPERE, 1000,
                "1 A = 1000 mA");
        assertConversion(1, VoltageUnit.VOLT, VoltageUnit.MILLIVOLT, 1000,
                "1 V = 1000 mV");
        assertConversion(1, FrequencyUnit.KILOHERTZ, FrequencyUnit.HERTZ, 1000,
                "1 kHz = 1000 Hz");
        assertConversion(1000, PowerUnit.MILLIWATT, PowerUnit.WATT, 1,
                "1000 mW = 1 W");
        assertConversion(1, PowerUnit.KILOWATT, PowerUnit.WATT, 1000,
                "1 kW = 1000 W");
        assertConversion(1, ApparentPowerUnit.VOLT_AMPERE, ApparentPowerUnit.VOLT_AMPERE, 1,
                "apparent power uses its own unit class");
        assertConversion(1, ReactivePowerUnit.VAR, ReactivePowerUnit.VAR, 1,
                "reactive power uses its own unit class");
    }

    @Test
    public void geometryAndFlowUnitsConvertCorrectly() {
        assertConversion(1, DistanceUnit.M, DistanceUnit.CM, 100,
                "1 m = 100 cm");
        assertConversion(1, DistanceUnit.KM, DistanceUnit.M, 1000,
                "1 km = 1000 m");
        assertConversion(1, VolumeUnit.L, VolumeUnit.ML, 1000,
                "1 L = 1000 mL");
        assertConversion(1, VolumeUnit.ML, VolumeUnit.CM3, 1,
                "1 mL = 1 cm3");
        assertConversion(1, VolumeUnit.M3, VolumeUnit.L, 1000,
                "1 m3 = 1000 L");
        assertConversion(1, WeightUnit.KG, WeightUnit.G, 1000,
                "1 kg = 1000 g");
        assertConversion(1, LiterFlowUnit.L_PER_MINUTE, LiterFlowUnit.L_PER_HOUR, 60,
                "1 L/min = 60 L/h");
        assertConversion(1, LiterFlowUnit.ML_PER_MINUTE, LiterFlowUnit.L_PER_MINUTE, 0.001,
                "1 mL/min = 0.001 L/min");
    }

    @Test
    public void environmentAndTimeUnitsConvertCorrectly() {
        assertConversion(1, PressureUnit.KPA, PressureUnit.HPA, 10,
                "1 kPa = 10 hPa");
        assertConversion(1, PressureUnit.ATM, PressureUnit.KPA, 101.325,
                "1 atm = 101.325 kPa");
        assertConversion(1, SpeedUnit.METER_PER_SECOND, SpeedUnit.KILOMETER_PER_HOUR, 3.6,
                "1 m/s = 3.6 km/h");
        assertConversion(1, SpeedUnit.KNOT, SpeedUnit.METER_PER_SECOND, 1852.0 / 3600.0,
                "1 knot = 1852/3600 m/s");
        assertConversion(1, TimeDeltaUnit.MINUTE, TimeDeltaUnit.SECOND, 60,
                "1 min = 60 s");
        assertConversion(1, RotationSpeedUnit.KRPM, RotationSpeedUnit.RPM, 1000,
                "1 krpm = 1000 rpm");
        assertConversion(12.3, RatioUnit.PERCENT, RatioUnit.PER_MILLE, 123,
                "12.3% = 123 per mille");
    }

    @Test
    public void voltageUnitDoesNotContainFrequencyUnit() {
        for (VoltageUnit unit : VoltageUnit.values()) {
            assertFalse("Hz is a frequency unit and must not be defined by VoltageUnit: " + unit,
                    "HERTZ".equals(unit.name()));
        }
    }

    @Test
    public void activePowerUnitDoesNotContainApparentPowerUnit() {
        for (PowerUnit unit : PowerUnit.values()) {
            assertFalse("VA is apparent power and must not be defined by PowerUnit: " + unit,
                    "VOLT_AMPERE".equals(unit.name()));
        }
    }

    @Test
    public void noConversionUnitOnlyConvertsIdenticalUnit() {
        UnitInfo degree = NoConversionUnit.of("Degree");
        assertEquals(5, SameUnitClassConverter.convert(5, degree, degree), DELTA);

        try {
            SameUnitClassConverter.convert(5, degree, NoConversionUnit.of("unitless"));
            throw new AssertionError("Different NoConversionUnit instances must not be convertible");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
