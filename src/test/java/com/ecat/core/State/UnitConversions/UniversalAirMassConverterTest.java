package com.ecat.core.State.UnitConversions;

import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.Science.AirQuality.Consts.MolecularWeights;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * UniversalAirMassConverter 单元测试
 *
 * 测试通用空气质量转换器功能，包括智能类型检测、体积浓度转换、
 * 质量浓度转换和异常处理等。
 *
 * @author coffee
 * @version 1.0.0
 */
public class UniversalAirMassConverterTest {

    // 测试精度 tolerance
    private static final double DELTA = 0.001;

    private UniversalAirMassConverter so2VolumeConverter;
    private UniversalAirMassConverter coMassConverter;
    private UniversalAirMassConverter o3VolumeConverter;
    private UniversalAirMassConverter no2MassConverter;

    @Before
    public void setUp() {
        // 体积单位转换器
        so2VolumeConverter = new UniversalAirMassConverter(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        o3VolumeConverter = new UniversalAirMassConverter(
            AirVolumeUnit.PPB, AirMassUnit.MGM3, MolecularWeights.O3
        );

        // 质量单位转换器
        coMassConverter = new UniversalAirMassConverter(
            AirMassUnit.MGM3, AirMassUnit.UGM3, MolecularWeights.CO
        );
        no2MassConverter = new UniversalAirMassConverter(
            AirMassUnit.UGM3, AirMassUnit.MGM3, MolecularWeights.NO2
        );
    }

    /**
     * 测试从体积单位的转换 - SO2 PPM到μg/m³
     * 计算公式：value × fromRatio × molecularWeight / MOLAR_VOLUME_25C / toRatio
     * 0.1 × 1000000 × 64.066 / 24.465 / 1000 = 261.868
     */
    @Test
    public void testConvert_FromAirVolumeUnit_SO2() {
        double result = so2VolumeConverter.convert(0.1);
        assertEquals(261.868, result, DELTA);
        assertTrue("应该是体积单位转换", so2VolumeConverter.isFromVolumeUnit());
        assertFalse("不应该是质量单位转换", so2VolumeConverter.isFromMassUnit());
    }

    /**
     * 测试从体积单位的转换 - O3 PPB到mg/m³
     * 计算公式：100 × 1000 × 47.998 / 24.465 / 1000000 = 0.196239
     */
    @Test
    public void testConvert_FromAirVolumeUnit_O3() {
        double result = o3VolumeConverter.convert(100.0);
        assertEquals(0.196239, result, DELTA);
        assertTrue("应该是体积单位转换", o3VolumeConverter.isFromVolumeUnit());
        assertFalse("不应该是质量单位转换", o3VolumeConverter.isFromMassUnit());
    }

    /**
     * 测试从质量单位的转换 - CO mg/m³到μg/m³
     * 计算公式：value × fromRatio / toRatio
     * 2.5 × 1000000 / 1000 = 2500.0
     */
    @Test
    public void testConvert_FromAirMassUnit_CO() {
        double result = coMassConverter.convert(2.5);
        assertEquals(2500.0, result, DELTA);
        assertTrue("应该是质量单位转换", coMassConverter.isFromMassUnit());
        assertFalse("不应该是体积单位转换", coMassConverter.isFromVolumeUnit());
    }

    /**
     * 测试从质量单位的转换 - NO2 μg/m³到mg/m³
     * 计算公式：5000 × 1000 / 1000000 = 5.0
     */
    @Test
    public void testConvert_FromAirMassUnit_NO2() {
        double result = no2MassConverter.convert(5000.0);
        assertEquals(5.0, result, DELTA);
        assertTrue("应该是质量单位转换", no2MassConverter.isFromMassUnit());
        assertFalse("不应该是体积单位转换", no2MassConverter.isFromVolumeUnit());
    }

    /**
     * 测试所有体积单位组合
     */
    @Test
    public void testConvert_AllAirVolumeUnits() {
        AirVolumeUnit[] volumeUnits = {AirVolumeUnit.PPM, AirVolumeUnit.PPB};
        AirMassUnit[] massUnits = {AirMassUnit.UGM3, AirMassUnit.MGM3};

        for (AirVolumeUnit fromUnit : volumeUnits) {
            for (AirMassUnit toUnit : massUnits) {
                UniversalAirMassConverter converter = new UniversalAirMassConverter(
                    fromUnit, toUnit, MolecularWeights.NO
                );

                assertTrue("应该是体积单位转换", converter.isFromVolumeUnit());
                assertFalse("不应该是质量单位转换", converter.isFromMassUnit());

                // 测试转换
                double result = converter.convert(1.0);
                assertTrue("转换结果应该为正数", result >= 0);
            }
        }
    }

    /**
     * 测试所有质量单位组合
     */
    @Test
    public void testConvert_AllAirMassUnits() {
        AirMassUnit[] massUnits = {AirMassUnit.UGM3, AirMassUnit.MGM3};

        for (AirMassUnit fromUnit : massUnits) {
            for (AirMassUnit toUnit : massUnits) {
                UniversalAirMassConverter converter = new UniversalAirMassConverter(
                    fromUnit, toUnit, MolecularWeights.SO2
                );

                assertFalse("不应该是体积单位转换", converter.isFromVolumeUnit());
                assertTrue("应该是质量单位转换", converter.isFromMassUnit());

                // 测试转换
                double result = converter.convert(1.0);
                assertTrue("转换结果应该为正数", result >= 0);
            }
        }
    }

    /**
     * 测试不支持的单位类型
     * 注意：由于UnitInfo接口的设计，直接创建不支持的类型比较困难
     * 这个测试主要验证构造函数和基本功能
     */
    @Test
    public void testConvert_SupportedUnitTypes() {
        // 只测试支持的单位类型：AirVolumeUnit和AirMassUnit

        // AirVolumeUnit 应该被正确识别
        UniversalAirMassConverter volumeConverter = new UniversalAirMassConverter(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        assertTrue("AirVolumeUnit应该被识别为体积单位", volumeConverter.isFromVolumeUnit());
        assertFalse("AirVolumeUnit不应该被识别为质量单位", volumeConverter.isFromMassUnit());

        // AirMassUnit 应该被正确识别
        UniversalAirMassConverter massConverter = new UniversalAirMassConverter(
            AirMassUnit.UGM3, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        assertFalse("AirMassUnit不应该被识别为体积单位", massConverter.isFromVolumeUnit());
        assertTrue("AirMassUnit应该被识别为质量单位", massConverter.isFromMassUnit());
    }

    /**
     * 测试边界值转换
     */
    @Test
    public void testConvert_BoundaryValues() {
        // 零值测试
        assertEquals(0.0, so2VolumeConverter.convert(0.0), DELTA);
        assertEquals(0.0, coMassConverter.convert(0.0), DELTA);

        // 小值测试
        double smallVolumeResult = so2VolumeConverter.convert(0.001);
        assertTrue("小体积值转换结果应该为正数", smallVolumeResult > 0);

        double smallMassResult = coMassConverter.convert(0.001);
        assertTrue("小质量值转换结果应该为正数", smallMassResult > 0);

        // 大值测试
        double largeVolumeResult = so2VolumeConverter.convert(1000.0);
        assertTrue("大体积值转换结果应该合理", largeVolumeResult > 0);

        double largeMassResult = coMassConverter.convert(1000.0);
        assertTrue("大质量值转换结果应该合理", largeMassResult > 0);
    }

    /**
     * 测试往返转换的一致性
     */
    @Test
    public void testRoundTripConversion_VolumeToMass() {
        // 使用标准转换器进行往返转换验证
        double originalPPM = 0.1;

        // Universal: PPM -> μg/m³
        double ugm3Value = so2VolumeConverter.convert(originalPPM);

        // Standard: μg/m³ -> PPM (使用AirMassToAirVolume)
        AirMassToAirVolume reverseConverter = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.SO2
        );
        double backToPPM = reverseConverter.convert(ugm3Value);

        // 验证往返转换的精度
        assertEquals("往返转换应该保持精度", originalPPM, backToPPM, DELTA);
    }

    /**
     * 测试构造函数参数验证
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NullSourceUnit() {
        new UniversalAirMassConverter(null, AirMassUnit.UGM3, MolecularWeights.SO2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NullTargetUnit() {
        new UniversalAirMassConverter(AirVolumeUnit.PPM, null, MolecularWeights.SO2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NegativeMolecularWeight() {
        new UniversalAirMassConverter(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, -64.0
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_ZeroMolecularWeight() {
        new UniversalAirMassConverter(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, 0.0
        );
    }

    /**
     * 测试 Getter 方法
     */
    @Test
    public void testGetSourceUnit() {
        assertEquals(AirVolumeUnit.PPM, so2VolumeConverter.getSourceUnit());
        assertEquals(AirMassUnit.MGM3, coMassConverter.getSourceUnit());
        assertEquals(AirVolumeUnit.PPB, o3VolumeConverter.getSourceUnit());
        assertEquals(AirMassUnit.UGM3, no2MassConverter.getSourceUnit());
    }

    @Test
    public void testGetTargetUnit() {
        assertEquals(AirMassUnit.UGM3, so2VolumeConverter.getTargetUnit());
        assertEquals(AirMassUnit.UGM3, coMassConverter.getTargetUnit());
        assertEquals(AirMassUnit.MGM3, o3VolumeConverter.getTargetUnit());
        assertEquals(AirMassUnit.MGM3, no2MassConverter.getTargetUnit());
    }

    @Test
    public void testGetMolecularWeight() {
        assertEquals(MolecularWeights.SO2, so2VolumeConverter.getMolecularWeight(), DELTA);
        assertEquals(MolecularWeights.CO, coMassConverter.getMolecularWeight(), DELTA);
        assertEquals(MolecularWeights.O3, o3VolumeConverter.getMolecularWeight(), DELTA);
        assertEquals(MolecularWeights.NO2, no2MassConverter.getMolecularWeight(), DELTA);
    }

    /**
     * 测试 toString 方法
     */
    @Test
    public void testToString() {
        String so2Expected = "UniversalAirMassConverter{ppm -> ug/m3, MW=64.066, volume->mass}";
        assertEquals(so2Expected, so2VolumeConverter.toString());

        String coExpected = "UniversalAirMassConverter{mg/m3 -> ug/m3, MW=28.010, mass->mass}";
        assertEquals(coExpected, coMassConverter.toString());

        String o3Expected = "UniversalAirMassConverter{ppb -> mg/m3, MW=47.998, volume->mass}";
        assertEquals(o3Expected, o3VolumeConverter.toString());

        String no2Expected = "UniversalAirMassConverter{ug/m3 -> mg/m3, MW=46.006, mass->mass}";
        assertEquals(no2Expected, no2MassConverter.toString());
    }

    /**
     * 测试使用所有分子量常量
     */
    @Test
    public void testConvert_UsingAllMolecularWeights() {
        // 测试从体积单位转换
        testVolumeConversionWithMolecularWeight(MolecularWeights.SO2);
        testVolumeConversionWithMolecularWeight(MolecularWeights.CO);
        testVolumeConversionWithMolecularWeight(MolecularWeights.O3);
        testVolumeConversionWithMolecularWeight(MolecularWeights.NO);
        testVolumeConversionWithMolecularWeight(MolecularWeights.NO2);

        // 测试从质量单位转换
        testMassConversionWithMolecularWeight(MolecularWeights.SO2);
        testMassConversionWithMolecularWeight(MolecularWeights.CO);
        testMassConversionWithMolecularWeight(MolecularWeights.O3);
        testMassConversionWithMolecularWeight(MolecularWeights.NO);
        testMassConversionWithMolecularWeight(MolecularWeights.NO2);
    }

    /**
     * 辅助方法：测试体积单位转换
     */
    private void testVolumeConversionWithMolecularWeight(double molecularWeight) {
        UniversalAirMassConverter converter = new UniversalAirMassConverter(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, molecularWeight
        );

        assertTrue("应该是体积单位转换", converter.isFromVolumeUnit());
        assertFalse("不应该是质量单位转换", converter.isFromMassUnit());

        // 测试转换
        double result = converter.convert(1.0);
        // 使用 MOLAR_VOLUME_25C = 24.465 进行验证
        double expected = 1.0 * AirVolumeUnit.PPM.getRatio() * molecularWeight / MolecularWeights.MOLAR_VOLUME_25C / AirMassUnit.UGM3.getRatio();
        assertEquals("体积转换 - 分子量 " + molecularWeight + " 错误", expected, result, DELTA);

        // 验证结果合理性
        assertTrue("转换结果应该为正数", result >= 0);
    }

    /**
     * 辅助方法：测试质量单位转换
     */
    private void testMassConversionWithMolecularWeight(double molecularWeight) {
        UniversalAirMassConverter converter = new UniversalAirMassConverter(
            AirMassUnit.MGM3, AirMassUnit.UGM3, molecularWeight
        );

        assertFalse("不应该是体积单位转换", converter.isFromVolumeUnit());
        assertTrue("应该是质量单位转换", converter.isFromMassUnit());

        // 测试转换
        double result = converter.convert(1.0);
        double expected = 1.0 * AirMassUnit.MGM3.getRatio() / AirMassUnit.UGM3.getRatio();
        assertEquals("质量转换 - 分子量 " + molecularWeight + " 错误", expected, result, DELTA);

        // 验证结果合理性
        assertTrue("转换结果应该为正数", result >= 0);

        // 质量转换不应该依赖分子量
        double result2 = converter.convert(2.0);
        assertEquals("质量转换结果应该是2倍", expected * 2.0, result2, DELTA);
    }

    /**
     * 测试特殊数值处理
     */
    @Test
    public void testConvert_SpecialValues() {
        // 测试 Double.NaN
        double nanResult = so2VolumeConverter.convert(Double.NaN);
        assertTrue("NaN应该传播", Double.isNaN(nanResult));

        // 测试正无穷
        double posInfResult = so2VolumeConverter.convert(Double.POSITIVE_INFINITY);
        assertTrue("正无穷应该传播", Double.isInfinite(posInfResult) && posInfResult > 0);

        // 测试负无穷
        double negInfResult = so2VolumeConverter.convert(Double.NEGATIVE_INFINITY);
        assertTrue("负无穷应该传播", Double.isInfinite(negInfResult) && negInfResult < 0);

        // 质量转换器也要测试特殊数值
        nanResult = coMassConverter.convert(Double.NaN);
        assertTrue("NaN应该传播", Double.isNaN(nanResult));
    }

    /**
     * 测试负值转换
     */
    @Test
    public void testConvert_NegativeValues() {
        double negativeVolumeResult = so2VolumeConverter.convert(-0.1);
        assertEquals("负体积值应该正确转换", -261.868, negativeVolumeResult, DELTA);

        double negativeMassResult = coMassConverter.convert(-2.5);
        assertEquals("负质量值应该正确转换", -2500.0, negativeMassResult, DELTA);
    }

    /**
     * 测试相同单位转换
     */
    @Test
    public void testConvert_SameUnits() {
        // 相同质量单位转换
        UniversalAirMassConverter sameMassConverter = new UniversalAirMassConverter(
            AirMassUnit.UGM3, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        double result = sameMassConverter.convert(100.0);
        assertEquals("相同质量单位转换应该等于原值", 100.0, result, DELTA);
        assertTrue("应该是质量单位转换", sameMassConverter.isFromMassUnit());

        // 相同质量单位但不同的转换器
        UniversalAirMassConverter sameMassConverter2 = new UniversalAirMassConverter(
            AirMassUnit.MGM3, AirMassUnit.MGM3, MolecularWeights.SO2
        );
        double result2 = sameMassConverter2.convert(1.0);
        assertEquals("相同质量单位转换应该等于原值", 1.0, result2, DELTA);
    }
}
