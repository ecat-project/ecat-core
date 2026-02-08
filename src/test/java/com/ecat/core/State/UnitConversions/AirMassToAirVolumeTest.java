package com.ecat.core.State.UnitConversions;

import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.Science.AirQuality.Consts.MolecularWeights;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * AirMassToAirVolume 单元测试
 *
 * 测试空气质量从质量浓度到体积浓度的转换功能，包括正常情况、
 * 边界条件和异常情况的测试。
 *
 * @author coffee
 * @version 1.0.0
 */
public class AirMassToAirVolumeTest {

    // 测试精度 tolerance
    private static final double DELTA = 0.001;

    private AirMassToAirVolume so2Converter;
    private AirMassToAirVolume coConverter;
    private AirMassToAirVolume o3Converter;
    private AirMassToAirVolume noConverter;

    @Before
    public void setUp() {
        so2Converter = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.SO2
        );
        coConverter = new AirMassToAirVolume(
            AirMassUnit.MGM3, AirVolumeUnit.PPB, MolecularWeights.CO
        );
        o3Converter = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.O3
        );
        noConverter = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.NO
        );
    }

    /**
     * 测试 SO2 从 μg/m³ 到 PPM 的转换
     * 计算公式：value × fromRatio × MOLAR_VOLUME_25C / molecularWeight / toRatio
     * 1000 × 1000 × 24.465 / 64.066 / 1000000 = 0.382225
     */
    @Test
    public void testConvert_SO2_UGM3_to_PPM() {
        double result = so2Converter.convert(1000.0);
        assertEquals(0.382225, result, DELTA);
    }

    /**
     * 测试 CO 从 mg/m³ 到 PPB 的转换
     * 计算公式：2.5 × 1000000 × 24.465 / 28.010 / 1 = 2183.595
     */
    @Test
    public void testConvert_CO_MGM3_to_PPB() {
        double result = coConverter.convert(2.5);
        assertEquals(2183.595, result, DELTA);
    }

    /**
     * 测试 O3 从 μg/m³ 到 PPM 的转换
     * 计算公式：500 × 1000 × 24.465 / 47.998 / 1000000 = 0.254851
     */
    @Test
    public void testConvert_O3_UGM3_to_PPM() {
        AirMassToAirVolume o3TestConverter = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.O3
        );
        double result = o3TestConverter.convert(500.0);
        assertEquals(0.254851, result, DELTA);
    }

    /**
     * 测试 NO 从 μg/m³ 到 PPM 的转换
     * 计算公式：200 × 1000 × 24.465 / 30.006 / 1000000 = 0.163097
     */
    @Test
    public void testConvert_NO_UGM3_to_PPM() {
        double result = noConverter.convert(200.0);
        assertEquals(0.163097, result, DELTA);
    }

    /**
     * 测试往返转换的一致性
     */
    @Test
    public void testRoundTripConversion() {
        // SO2 往返转换测试
        double originalPPM = 0.1;

        // PPM -> μg/m³
        AirVolumeToAirMass toMass = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        double ugm3Value = toMass.convert(originalPPM);

        // μg/m³ -> PPM
        AirMassToAirVolume toVolume = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.SO2
        );
        double backToPPM = toVolume.convert(ugm3Value);

        // 验证往返转换的精度
        assertEquals("往返转换应该保持精度", originalPPM, backToPPM, DELTA);
    }

    /**
     * 测试所有单位组合的转换
     */
    @Test
    public void testConvert_AllUnitCombinations() {
        AirVolumeUnit[] volumeUnits = {AirVolumeUnit.PPM, AirVolumeUnit.PPB};
        AirMassUnit[] massUnits = {AirMassUnit.UGM3, AirMassUnit.MGM3};

        for (AirMassUnit fromUnit : massUnits) {
            for (AirVolumeUnit toUnit : volumeUnits) {
                AirMassToAirVolume converter = new AirMassToAirVolume(
                    fromUnit, toUnit, MolecularWeights.SO2
                );

                // 测试转换不为负数
                double result = converter.convert(1.0);
                assertTrue("转换结果应该为正数", result >= 0);

                // 测试零值转换
                double zeroResult = converter.convert(0.0);
                assertEquals("零值应该转换为零", 0.0, zeroResult, DELTA);
            }
        }
    }

    /**
     * 测试边界值转换
     */
    @Test
    public void testConvert_BoundaryValues() {
        // 零值测试
        assertEquals(0.0, so2Converter.convert(0.0), DELTA);

        // 小值测试
        double smallValue = 0.001;
        double smallResult = so2Converter.convert(smallValue);
        assertTrue("小值转换结果应该为正数", smallResult > 0);

        // 大值测试
        double largeValue = 100000.0;
        double largeResult = so2Converter.convert(largeValue);
        assertTrue("大值转换结果应该合理", largeResult > 0);

        // 极小值测试
        double tinyValue = Double.MIN_VALUE;
        double tinyResult = so2Converter.convert(tinyValue);
        // 使用 MOLAR_VOLUME_25C = 24.465 进行验证
        double expected = tinyValue * so2Converter.getFromUnit().getRatio() * MolecularWeights.MOLAR_VOLUME_25C /
                    MolecularWeights.SO2 / so2Converter.getToUnit().getRatio();
        assertEquals("极小值应该正确转换", expected, tinyResult, DELTA);
    }

    /**
     * 测试负值转换
     */
    @Test
    public void testConvert_NegativeValue() {
        double result = so2Converter.convert(-1000.0);
        assertEquals("负值应该正确转换", -0.382225, result, DELTA);
    }

    /**
     * 测试构造函数 null 检查
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NullFromUnit() {
        new AirMassToAirVolume(null, AirVolumeUnit.PPM, MolecularWeights.SO2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NullToUnit() {
        new AirMassToAirVolume(AirMassUnit.UGM3, null, MolecularWeights.SO2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NegativeMolecularWeight() {
        new AirMassToAirVolume(AirMassUnit.UGM3, AirVolumeUnit.PPM, -64.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_ZeroMolecularWeight() {
        new AirMassToAirVolume(AirMassUnit.UGM3, AirVolumeUnit.PPM, 0.0);
    }

    /**
     * 测试 Getter 方法
     */
    @Test
    public void testGetFromUnit() {
        assertEquals(AirMassUnit.UGM3, so2Converter.getFromUnit());
        assertEquals(AirMassUnit.MGM3, coConverter.getFromUnit());
    }

    @Test
    public void testGetToUnit() {
        assertEquals(AirVolumeUnit.PPM, so2Converter.getToUnit());
        assertEquals(AirVolumeUnit.PPB, coConverter.getToUnit());
    }

    @Test
    public void testGetMolecularWeight() {
        assertEquals(MolecularWeights.SO2, so2Converter.getMolecularWeight(), DELTA);
        assertEquals(MolecularWeights.CO, coConverter.getMolecularWeight(), DELTA);
        assertEquals(MolecularWeights.O3, o3Converter.getMolecularWeight(), DELTA);
        assertEquals(MolecularWeights.NO, noConverter.getMolecularWeight(), DELTA);
    }

    /**
     * 测试 toString 方法
     */
    @Test
    public void testToString() {
        String expected = "AirMassToAirVolume{ug/m3 -> ppm, MW=64.066}";
        assertEquals(expected, so2Converter.toString());

        String expected2 = "AirMassToAirVolume{mg/m3 -> ppb, MW=28.010}";
        assertEquals(expected2, coConverter.toString());
    }

    /**
     * 测试使用所有分子量常量
     */
    @Test
    public void testConvert_UsingAllMolecularWeights() {
        // 测试所有定义的分子量常量都能正常工作
        testWithMolecularWeight(MolecularWeights.SO2);
        testWithMolecularWeight(MolecularWeights.CO);
        testWithMolecularWeight(MolecularWeights.O3);
        testWithMolecularWeight(MolecularWeights.NO);
        testWithMolecularWeight(MolecularWeights.NO2);
    }

    /**
     * 辅助方法：测试特定分子量的转换
     */
    private void testWithMolecularWeight(double molecularWeight) {
        AirMassToAirVolume converter = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, molecularWeight
        );

        // 测试正常转换
        double result = converter.convert(1000.0);
        // 使用 MOLAR_VOLUME_25C = 24.465 进行验证
        double expected = 1000.0 * AirMassUnit.UGM3.getRatio() * MolecularWeights.MOLAR_VOLUME_25C / molecularWeight / AirVolumeUnit.PPM.getRatio();
        assertEquals("分子量 " + molecularWeight + " 转换错误", expected, result, DELTA);

        // 验证结果合理性
        assertTrue("转换结果应该为正数", result >= 0);

        // 验证toString方法
        String toString = converter.toString();
        assertNotNull("toString不应为null", toString);
        assertTrue("toString应包含分子量信息", toString.contains("MW=" + String.format("%.3f", molecularWeight)));
    }

    /**
     * 测试特殊数值处理
     */
    @Test
    public void testConvert_SpecialValues() {
        // 测试 Double.NaN
        double nanResult = so2Converter.convert(Double.NaN);
        assertTrue("NaN应该传播", Double.isNaN(nanResult));

        // 测试正无穷
        double posInfResult = so2Converter.convert(Double.POSITIVE_INFINITY);
        assertTrue("正无穷应该传播", Double.isInfinite(posInfResult) && posInfResult > 0);

        // 测试负无穷
        double negInfResult = so2Converter.convert(Double.NEGATIVE_INFINITY);
        assertTrue("负无穷应该传播", Double.isInfinite(negInfResult) && negInfResult < 0);
    }

    /**
     * 测试单位比率转换的数学正确性
     */
    @Test
    public void testConvert_UnitRatioCorrectness() {
        // 测试不同质量单位之间的转换
        AirMassToAirVolume fromUGM3 = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.SO2
        );
        AirMassToAirVolume fromMGM3 = new AirMassToAirVolume(
            AirMassUnit.MGM3, AirVolumeUnit.PPM, MolecularWeights.SO2
        );

        double ugm3Value = 1000.0;
        double mgm3Value = 1.0; // 等价于 1000 μg/m³

        double result1 = fromUGM3.convert(ugm3Value);
        double result2 = fromMGM3.convert(mgm3Value);

        // 两种方式的结果应该相同
        assertEquals("不同质量单位的等价值转换应该一致", result1, result2, DELTA);
    }

    /**
     * 测试不同体积单位之间的转换
     */
    @Test
    public void testConvert_DifferentVolumeUnits() {
        AirMassToAirVolume toPPM = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.CO
        );
        AirMassToAirVolume toPPB = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPB, MolecularWeights.CO
        );

        double massValue = 1000.0;
        double ppmResult = toPPM.convert(massValue);
        double ppbResult = toPPB.convert(massValue);

        // PPM 和 PPB 的关系：1 PPM = 1000 PPB
        assertEquals("PPM和PPB转换关系错误", ppmResult * 1000.0, ppbResult, DELTA);
    }
}
