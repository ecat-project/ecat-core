package com.ecat.core.State.UnitConversions;

import com.ecat.core.State.UnitConverter;
import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.Science.AirQuality.Consts.MolecularWeights;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * UnitConverter 单元测试
 *
 * 测试单位转换器的门面类功能，包括与各种转换实现类的集成测试。
 *
 * @author coffee
 * @version 1.0.0
 */
public class UnitConverterTest {

    // 测试精度 tolerance
    private static final double DELTA = 0.001;

    private UnitConverter unitConverter;
    private AirVolumeToAirMass volumeToMassConverter;
    private AirMassToAirVolume massToVolumeConverter;
    private UniversalAirMassConverter universalConverter;

    @Before
    public void setUp() {
        unitConverter = new UnitConverter();

        // 创建各种转换器实例
        volumeToMassConverter = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        massToVolumeConverter = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.CO
        );
        universalConverter = new UniversalAirMassConverter(
            AirVolumeUnit.PPB, AirMassUnit.UGM3, MolecularWeights.NO2
        );
    }

    /**
     * 测试使用 AirVolumeToAirMass 转换器
     */
    @Test
    public void testConvertValue_WithAirVolumeToAirMass() {
        double ppmValue = 0.1;
        double result = unitConverter.convertValue(ppmValue, volumeToMassConverter);

        // 验证计算：0.1 × 1000000 × 64.0 / 22.4 / 1000 = 285.714286
        assertEquals(285.714286, result, DELTA);
        assertTrue("转换结果应该为正数", result > 0);
    }

    /**
     * 测试使用 AirMassToAirVolume 转换器
     */
    @Test
    public void testConvertValue_WithAirMassToAirVolume() {
        double ugm3Value = 1000.0;
        double result = unitConverter.convertValue(ugm3Value, massToVolumeConverter);

        // 验证计算：1000 × 1000 × 22.4 / 28.0 / 1000000 = 0.800000
        assertEquals(0.800000, result, DELTA);
        assertTrue("转换结果应该为正数", result > 0);
    }

    /**
     * 测试使用 UniversalAirMassConverter 转换器
     */
    @Test
    public void testConvertValue_WithUniversalConverter() {
        double ppbValue = 50.0;
        double result = unitConverter.convertValue(ppbValue, universalConverter);

        // 验证计算：50 × 1000 × 46.0 / 22.4 / 1000 = 102.678571
        assertEquals(102.678571, result, DELTA);
        assertTrue("转换结果应该为正数", result > 0);
    }

    /**
     * 测试所有转换类型的集成
     */
    @Test
    public void testConvertValue_WithAllConversionTypes() {
        // 测试 AirVolumeToAirMass - SO2
        AirVolumeToAirMass so2Converter = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        double so2Result = unitConverter.convertValue(0.1, so2Converter);
        assertEquals(285.714286, so2Result, DELTA);

        // 测试 AirMassToAirVolume - O3
        AirMassToAirVolume o3Converter = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.O3
        );
        double o3Result = unitConverter.convertValue(1000.0, o3Converter);
        assertEquals(0.466667, o3Result, DELTA);

        // 测试 UniversalAirMassConverter - 体积到质量
        UniversalAirMassConverter volumeToMass = new UniversalAirMassConverter(
            AirVolumeUnit.PPB, AirMassUnit.UGM3, MolecularWeights.NO
        );
        double volumeToMassResult = unitConverter.convertValue(100.0, volumeToMass);
        assertEquals(133.928571, volumeToMassResult, DELTA);

        // 测试 UniversalAirMassConverter - 质量到质量
        UniversalAirMassConverter massToMass = new UniversalAirMassConverter(
            AirMassUnit.MGM3, AirMassUnit.UGM3, MolecularWeights.CO
        );
        double massToMassResult = unitConverter.convertValue(2.5, massToMass);
        assertEquals(2500.0, massToMassResult, DELTA);
    }

    /**
     * 测试不同分子量的转换
     */
    @Test
    public void testConvertValue_WithDifferentMolecularWeights() {

        // 测试所有定义的分子量常量
        testConversionWithMolecularWeight(MolecularWeights.SO2);
        testConversionWithMolecularWeight(MolecularWeights.CO);
        testConversionWithMolecularWeight(MolecularWeights.O3);
        testConversionWithMolecularWeight(MolecularWeights.NO);
        testConversionWithMolecularWeight(MolecularWeights.NO2);
    }

    /**
     * 测试往返转换的一致性
     */
    @Test
    public void testConvertValue_RoundTripConversion() {
        double originalValue = 0.05;

        // 使用 UniversalAirMassConverter 进行体积到质量转换
        UniversalAirMassConverter toMass = new UniversalAirMassConverter(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
        );

        // 使用标准转换器进行质量到体积转换
        AirMassToAirVolume standardToVolume = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.SO2
        );

        // PPM -> μg/m³
        double massValue = unitConverter.convertValue(originalValue, toMass);

        // μg/m³ -> PPM
        double backToVolume = unitConverter.convertValue(massValue, standardToVolume);

        // 验证往返转换的精度
        assertEquals("往返转换应该保持精度", originalValue, backToVolume, DELTA);
    }

    /**
     * 测试边界值
     */
    @Test
    public void testConvertValue_BoundaryValues() {
        // 零值测试
        double zeroResult = unitConverter.convertValue(0.0, volumeToMassConverter);
        assertEquals(0.0, zeroResult, DELTA);

        // 负值测试
        double negativeResult = unitConverter.convertValue(-0.1, volumeToMassConverter);
        assertEquals(-285.714286, negativeResult, DELTA);

        // 小值测试
        double smallResult = unitConverter.convertValue(0.001, volumeToMassConverter);
        assertTrue("小值转换结果应该为正数", smallResult > 0);

        // 大值测试
        double largeResult = unitConverter.convertValue(100.0, volumeToMassConverter);
        assertTrue("大值转换结果应该合理", largeResult > 0);
    }

    /**
     * 测试异常情况
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConvertValue_NullConversion() {
        unitConverter.convertValue(1.0, null);
    }

    /**
     * 测试特殊数值
     */
    @Test
    public void testConvertValue_SpecialValues() {
        // 测试 Double.NaN
        double nanResult = unitConverter.convertValue(Double.NaN, volumeToMassConverter);
        assertTrue("NaN应该传播", Double.isNaN(nanResult));

        // 测试正无穷
        double posInfResult = unitConverter.convertValue(Double.POSITIVE_INFINITY, volumeToMassConverter);
        assertTrue("正无穷应该传播", Double.isInfinite(posInfResult) && posInfResult > 0);

        // 测试负无穷
        double negInfResult = unitConverter.convertValue(Double.NEGATIVE_INFINITY, volumeToMassConverter);
        assertTrue("负无穷应该传播", Double.isInfinite(negInfResult) && negInfResult < 0);
    }

    /**
     * 测试不同单位组合
     */
    @Test
    public void testConvertValue_DifferentUnitCombinations() {
        // 测试所有 AirVolumeUnit -> AirMassUnit 组合
        AirVolumeUnit[] volumeUnits = {AirVolumeUnit.PPM, AirVolumeUnit.PPB};
        AirMassUnit[] massUnits = {AirMassUnit.UGM3, AirMassUnit.MGM3};

        for (AirVolumeUnit fromUnit : volumeUnits) {
            for (AirMassUnit toUnit : massUnits) {
                AirVolumeToAirMass converter = new AirVolumeToAirMass(
                    fromUnit, toUnit, MolecularWeights.NO
                );

                double result = unitConverter.convertValue(1.0, converter);
                assertTrue("所有单位组合转换结果应该为正数", result >= 0);
            }
        }

        // 测试所有 AirMassUnit -> AirVolumeUnit 组合
        for (AirMassUnit fromUnit : massUnits) {
            for (AirVolumeUnit toUnit : volumeUnits) {
                AirMassToAirVolume converter = new AirMassToAirVolume(
                    fromUnit, toUnit, MolecularWeights.NO
                );

                double result = unitConverter.convertValue(1.0, converter);
                assertTrue("所有单位组合转换结果应该为正数", result >= 0);
            }
        }
    }

    /**
     * 测试集成场景 - 模拟真实使用情况
     */
    @Test
    public void testConvertValue_RealWorldScenarios() {
        // 场景1：环境监测设备数据转换
        // SO2监测：设备输出0.05 PPM，需要转换为μg/m³显示
        AirVolumeToAirMass so2DeviceConverter = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        double deviceReading = 0.05;
        double displayValue = unitConverter.convertValue(deviceReading, so2DeviceConverter);
        assertEquals("设备读数转换", 142.857143, displayValue, DELTA);

        // 场景2：CO报警器数据转换
        // CO报警器：检测到25 PPB，需要转换为mg/m³
        AirVolumeToAirMass coAlarmConverter = new AirVolumeToAirMass(
            AirVolumeUnit.PPB, AirMassUnit.MGM3, MolecularWeights.CO
        );
        double alarmReading = 25.0;
        double alarmValue = unitConverter.convertValue(alarmReading, coAlarmConverter);
        assertEquals(0.031250, alarmValue, DELTA);

        // 场景3：数据记录格式转换
        // 数据库存储的是μg/m³，需要转换为PPM用于分析
        AirMassToAirVolume analysisConverter = new AirMassToAirVolume(
            AirMassUnit.UGM3, AirVolumeUnit.PPM, MolecularWeights.NO2
        );
        double dbValue = 200.0;
        double analysisValue = unitConverter.convertValue(dbValue, analysisConverter);
        assertEquals(0.097391, analysisValue, DELTA);
    }

    /**
     * 辅助方法：测试特定分子量的转换
     */
    private void testConversionWithMolecularWeight(double molecularWeight) {
        AirVolumeToAirMass converter = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, molecularWeight
        );

        double result = unitConverter.convertValue(1.0, converter);
        double expected = 1.0 * AirVolumeUnit.PPM.getRatio() * molecularWeight / 22.4 / AirMassUnit.UGM3.getRatio();
        assertEquals("分子量 " + molecularWeight + " 转换错误", expected, result, DELTA);

        // 验证结果合理性
        assertTrue("转换结果应该为正数", result >= 0);
    }

    /**
     * 测试转换精度和性能
     */
    @Test
    public void testConvertValue_PrecisionAndPerformance() {
        // 测试多次转换的精度稳定性
        double testValue = 0.123456789;
        double result1 = unitConverter.convertValue(testValue, volumeToMassConverter);
        double result2 = unitConverter.convertValue(testValue, volumeToMassConverter);
        double result3 = unitConverter.convertValue(testValue, volumeToMassConverter);

        // 相同输入应该产生相同输出
        assertEquals("多次转换结果应该一致", result1, result2, DELTA);
        assertEquals("多次转换结果应该一致", result2, result3, DELTA);

        // 测试转换精度
        assertTrue("转换结果应该有足够精度", Math.abs(result1) > 0);
    }

    /**
     * 测试转换器的独立性
     */
    @Test
    public void testConvertValue_ConverterIndependence() {
        // 创建多个相同的转换器
        AirVolumeToAirMass converter1 = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        AirVolumeToAirMass converter2 = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
        );

        double testValue = 0.1;
        double result1 = unitConverter.convertValue(testValue, converter1);
        double result2 = unitConverter.convertValue(testValue, converter2);

        // 不同转换器实例应该产生相同结果
        assertEquals("独立转换器实例结果应该一致", result1, result2, DELTA);
    }
}
