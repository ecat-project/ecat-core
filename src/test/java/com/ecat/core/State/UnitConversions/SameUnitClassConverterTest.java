package com.ecat.core.State.UnitConversions;

import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SameUnitClassConverter 单元测试
 *
 * 测试相同单位类转换器功能，包括相同单位类转换、
 * 不同单位类异常处理、空值处理和边界值测试等。
 *
 * @author coffee
 * @version 1.0.0
 */
public class SameUnitClassConverterTest {

    // 测试精度 tolerance
    private static final double DELTA = 0.001;

    private SameUnitClassConverter massConverter;
    private SameUnitClassConverter volumeConverter;

    @Before
    public void setUp() {
        // 质量浓度单位转换器 (μg/m³ ↔ mg/m³)
        massConverter = new SameUnitClassConverter(AirMassUnit.UGM3, AirMassUnit.MGM3);

        // 体积浓度单位转换器 (ppm ↔ ppb)
        volumeConverter = new SameUnitClassConverter(AirVolumeUnit.PPM, AirVolumeUnit.PPB);
    }

    /**
     * 测试相同单位类转换 - 质量浓度单位
     */
    @Test
    public void testMassUnitConversion() {
        // μg/m³ → mg/m³
        double result = massConverter.convert(1000.0);
        assertEquals(1.0, result, DELTA);

        // 测试静态方法
        double staticResult = SameUnitClassConverter.convert(500.0, AirMassUnit.UGM3, AirMassUnit.MGM3);
        assertEquals(0.5, staticResult, DELTA);
    }

    /**
     * 测试相同单位类转换 - 体积浓度单位
     */
    @Test
    public void testVolumeUnitConversion() {
        // ppm → ppb (1 ppm = 1000 ppb)
        double result = volumeConverter.convert(1.0);
        assertEquals(1000.0, result, DELTA);

        // 反向转换 ppb → ppm
        SameUnitClassConverter reverseConverter = new SameUnitClassConverter(AirVolumeUnit.PPB, AirVolumeUnit.PPM);
        double reverseResult = reverseConverter.convert(2000.0);
        assertEquals(2.0, reverseResult, DELTA);
    }

    /**
     * 测试不同体积浓度单位转换
     */
    @Test
    public void testDifferentVolumeUnits() {
        // ppm → μmol/mol (ppm ratio=1000000.0, μmol/mol ratio=1000.0)
        SameUnitClassConverter ppmToMol = new SameUnitClassConverter(AirVolumeUnit.PPM, AirVolumeUnit.UMOL_PER_MOL);
        double result = ppmToMol.convert(1.0);
        assertEquals(1000.0, result, DELTA); // 1 ppm = 1000 μmol/mol

        // ppb → nmol/mol (ppb ratio=1000.0, nmol/mol ratio=1000000.0)
        SameUnitClassConverter ppbToNm = new SameUnitClassConverter(AirVolumeUnit.PPB, AirVolumeUnit.NMOL_PER_MOL);
        double result2 = ppbToNm.convert(1000.0);
        assertEquals(1.0, result2, DELTA); // 1000 ppb = 1 nmol/mol
    }

    /**
     * 测试工厂方法创建转换器
     */
    @Test
    public void testFactoryMethod() {
        SameUnitClassConverter converter = SameUnitClassConverter.create(AirMassUnit.MGM3, AirMassUnit.UGM3);
        assertNotNull(converter);
        assertEquals(AirMassUnit.MGM3, converter.getSourceUnit());
        assertEquals(AirMassUnit.UGM3, converter.getTargetUnit());

        double result = converter.convert(2.5);
        assertEquals(2500.0, result, DELTA);
    }

    /**
     * 测试不同单位类转换 - 应该抛出异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testDifferentUnitClassConversion() {
        // 尝试创建不同单位类的转换器 - 应该在构造函数中抛出异常
        new SameUnitClassConverter(AirMassUnit.MGM3, AirVolumeUnit.PPM);
    }

    /**
     * 测试静态方法处理不同单位类 - 应该抛出异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testStaticMethodDifferentUnitClass() {
        // 尝试使用静态方法转换不同单位类 - 应该抛出异常
        SameUnitClassConverter.convert(1.0, AirMassUnit.UGM3, AirVolumeUnit.PPB);
    }

    /**
     * 测试空单位处理 - 源单位为null
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNullSourceUnit() {
        new SameUnitClassConverter(null, AirMassUnit.MGM3);
    }

    /**
     * 测试空单位处理 - 目标单位为null
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNullTargetUnit() {
        new SameUnitClassConverter(AirMassUnit.UGM3, null);
    }

    /**
     * 测试静态方法空单位处理
     */
    @Test(expected = IllegalArgumentException.class)
    public void testStaticMethodNullUnit() {
        SameUnitClassConverter.convert(1.0, null, AirMassUnit.MGM3);
    }

    /**
     * 测试NaN数值处理
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNaNValue() {
        massConverter.convert(Double.NaN);
    }

    /**
     * 测试无限值处理
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInfiniteValue() {
        volumeConverter.convert(Double.POSITIVE_INFINITY);
    }

    /**
     * 测试边界值
     */
    @Test
    public void testBoundaryValues() {
        // 测试零值
        assertEquals(0.0, massConverter.convert(0.0), DELTA);
        assertEquals(0.0, volumeConverter.convert(0.0), DELTA);

        // 测试正值
        assertEquals(1000.0, massConverter.convert(1000000.0), DELTA); // μg/m³ → mg/m³
        assertEquals(1000000.0, volumeConverter.convert(1000.0), DELTA); // ppm → ppb

        // 测试负值
        assertEquals(-1000.0, massConverter.convert(-1000000.0), DELTA);
        assertEquals(-1000000.0, volumeConverter.convert(-1000.0), DELTA);

        // 测试极小值
        assertEquals(0.000001, massConverter.convert(1.0), DELTA); // 1 μg/m³ = 0.000001 mg/m³
        assertEquals(1000.0, volumeConverter.convert(1.0), DELTA); // 1 ppm = 1000 ppb
    }

    /**
     * 测试相同单位转换（无转换）
     */
    @Test
    public void testSameUnitConversion() {
        SameUnitClassConverter sameUnitConverter = new SameUnitClassConverter(AirMassUnit.MGM3, AirMassUnit.MGM3);
        assertEquals(100.0, sameUnitConverter.convert(100.0), DELTA);
        assertEquals(0.0, sameUnitConverter.convert(0.0), DELTA);
        assertEquals(-50.5, sameUnitConverter.convert(-50.5), DELTA);
    }

    /**
     * 测试isSameUnitClass静态方法
     */
    @Test
    public void testIsSameUnitClass() {
        // 相同单位类
        assertTrue(SameUnitClassConverter.isSameUnitClass(AirMassUnit.MGM3, AirMassUnit.UGM3));
        assertTrue(SameUnitClassConverter.isSameUnitClass(AirVolumeUnit.PPM, AirVolumeUnit.PPB));

        // 不同单位类
        assertFalse(SameUnitClassConverter.isSameUnitClass(AirMassUnit.MGM3, AirVolumeUnit.PPM));
        assertFalse(SameUnitClassConverter.isSameUnitClass(AirVolumeUnit.PPB, AirMassUnit.UGM3));

        // 包含null的情况
        assertFalse(SameUnitClassConverter.isSameUnitClass(null, AirMassUnit.MGM3));
        assertFalse(SameUnitClassConverter.isSameUnitClass(AirVolumeUnit.PPM, null));
        assertFalse(SameUnitClassConverter.isSameUnitClass(null, null));
    }

    /**
     * 测试toString方法
     */
    @Test
    public void testToString() {
        String result = massConverter.toString();
        assertTrue(result.contains("SameUnitClassConverter"));
        assertTrue(result.contains("from=ug/m3"));  // 实际名称是 "ug/m3" 而不是 "μg/m³"
        assertTrue(result.contains("to=mg/m3"));    // 实际名称是 "mg/m3" 而不是 "mg/m³"
    }

    /**
     * 测试getter方法
     */
    @Test
    public void testGetters() {
        assertEquals(AirMassUnit.UGM3, massConverter.getSourceUnit());
        assertEquals(AirMassUnit.MGM3, massConverter.getTargetUnit());
        assertEquals(AirVolumeUnit.PPM, volumeConverter.getSourceUnit());
        assertEquals(AirVolumeUnit.PPB, volumeConverter.getTargetUnit());
    }
}
