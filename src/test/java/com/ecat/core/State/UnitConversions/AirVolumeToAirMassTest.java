package com.ecat.core.State.UnitConversions;

import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.Science.AirQuality.Consts.MolecularWeights;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * AirVolumeToAirMass 单元测试
 *
 * 测试空气质量从体积浓度到质量浓度的转换功能，包括正常情况、
 * 边界条件和异常情况的测试。
 *
 * @author coffee
 * @version 1.0.0
 */
public class AirVolumeToAirMassTest {

    // 测试精度 tolerance
    private static final double DELTA = 0.001;

    private AirVolumeToAirMass so2Converter;
    private AirVolumeToAirMass coConverter;
    private AirVolumeToAirMass o3Converter;
    private AirVolumeToAirMass no2Converter;

    @Before
    public void setUp() {
        so2Converter = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        coConverter = new AirVolumeToAirMass(
            AirVolumeUnit.PPB, AirMassUnit.MGM3, MolecularWeights.CO
        );
        o3Converter = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.O3
        );
        no2Converter = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.NO2
        );
    }

    /**
     * 测试 SO2 从 PPM 到 μg/m³ 的转换
     * 计算公式：value × fromRatio × molecularWeight / MOLAR_VOLUME_25C / toRatio
     * 0.1 × 1000000 × 64.066 / 24.465 / 1000 = 261.868
     */
    @Test
    public void testConvert_SO2_PPM_to_UGM3() {
        double result = so2Converter.convert(0.1);
        assertEquals(261.868, result, DELTA);
    }

    /**
     * 测试 CO 从 PPB 到 mg/m³ 的转换
     * 计算公式：100 × 1000 × 28.0 / 24.465 / 1000000 = 0.114452
     */
    @Test
    public void testConvert_CO_PPB_to_MGM3() {
        double result = coConverter.convert(100.0);
        assertEquals(0.114452, result, DELTA);
    }

    /**
     * 测试 O3 从 PPM 到 μg/m³ 的转换
     * 计算公式：0.05 × 1000000 × 47.998 / 24.465 / 1000 = 98.095
     */
    @Test
    public void testConvert_O3_PPM_to_UGM3() {
        AirVolumeToAirMass o3TestConverter = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.O3
        );
        double result = o3TestConverter.convert(0.05);
        assertEquals(98.095, result, DELTA);
    }

    /**
     * 测试 NO2 从 PPM 到 μg/m³ 的转换
     * 计算公式：0.2 × 1000000 × 46.006 / 24.465 / 1000 = 376.096
     */
    @Test
    public void testConvert_NO2_PPM_to_UGM3() {
        double result = no2Converter.convert(0.2);
        assertEquals(376.096, result, DELTA);
    }

    /**
     * 测试所有单位组合的转换
     */
    @Test
    public void testConvert_AllUnitCombinations() {
        AirVolumeUnit[] volumeUnits = {AirVolumeUnit.PPM, AirVolumeUnit.PPB};
        AirMassUnit[] massUnits = {AirMassUnit.UGM3, AirMassUnit.MGM3};

        for (AirVolumeUnit fromUnit : volumeUnits) {
            for (AirMassUnit toUnit : massUnits) {
                AirVolumeToAirMass converter = new AirVolumeToAirMass(
                    fromUnit, toUnit, MolecularWeights.NO
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
        double largeValue = 1000.0;
        double largeResult = so2Converter.convert(largeValue);
        assertTrue("大值转换结果应该合理", largeResult > 0);

        // 极小值测试
        double tinyValue = Double.MIN_VALUE;
        double tinyResult = so2Converter.convert(tinyValue);
        // 使用 MOLAR_VOLUME_25C = 24.465 进行验证
        double expected = tinyValue * so2Converter.getFromUnit().getRatio() *
                    MolecularWeights.SO2 / MolecularWeights.MOLAR_VOLUME_25C / so2Converter.getToUnit().getRatio();
        assertEquals("极小值应该正确转换", expected, tinyResult, DELTA);
    }

    /**
     * 测试负值转换
     */
    @Test
    public void testConvert_NegativeValue() {
        double result = so2Converter.convert(-0.1);
        assertEquals("负值应该正确转换", -261.868, result, DELTA);
    }

    /**
     * 测试构造函数 null 检查
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NullFromUnit() {
        new AirVolumeToAirMass(null, AirMassUnit.UGM3, MolecularWeights.SO2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NullToUnit() {
        new AirVolumeToAirMass(AirVolumeUnit.PPM, null, MolecularWeights.SO2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NegativeMolecularWeight() {
        new AirVolumeToAirMass(AirVolumeUnit.PPM, AirMassUnit.UGM3, -64.066);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_ZeroMolecularWeight() {
        new AirVolumeToAirMass(AirVolumeUnit.PPM, AirMassUnit.UGM3, 0.0);
    }

    /**
     * 测试 Getter 方法
     */
    @Test
    public void testGetFromUnit() {
        assertEquals(AirVolumeUnit.PPM, so2Converter.getFromUnit());
        assertEquals(AirVolumeUnit.PPB, coConverter.getFromUnit());
    }

    @Test
    public void testGetToUnit() {
        assertEquals(AirMassUnit.UGM3, so2Converter.getToUnit());
        assertEquals(AirMassUnit.MGM3, coConverter.getToUnit());
    }

    @Test
    public void testGetMolecularWeight() {
        assertEquals(MolecularWeights.SO2, so2Converter.getMolecularWeight(), DELTA);
        assertEquals(MolecularWeights.CO, coConverter.getMolecularWeight(), DELTA);
        assertEquals(MolecularWeights.O3, o3Converter.getMolecularWeight(), DELTA);
        assertEquals(MolecularWeights.NO2, no2Converter.getMolecularWeight(), DELTA);
    }

    /**
     * 测试 toString 方法
     */
    @Test
    public void testToString() {
        String expected = "AirVolumeToAirMass{ppm -> ug/m3, MW=64.066}";
        assertEquals(expected, so2Converter.toString());

        String expected2 = "AirVolumeToAirMass{ppb -> mg/m3, MW=28.010}";
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
        AirVolumeToAirMass converter = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.UGM3, molecularWeight
        );

        // 测试正常转换
        double result = converter.convert(1.0);
        // 使用 MOLAR_VOLUME_25C = 24.465 进行验证
        double expected = 1.0 * AirVolumeUnit.PPM.getRatio() * molecularWeight / MolecularWeights.MOLAR_VOLUME_25C / AirMassUnit.UGM3.getRatio();
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
     * 测试 SATP 标准示例计算：1 ppb SO₂ = 2.6187 μg/m³
     *
     * 验证 MolecularWeights.java 中 MOLAR_VOLUME_25C 常量的使用。
     *
     * 量级对应关系：
     * - ppb ↔ μg/m³ (同量级)
     * - ppm ↔ mg/m³ (同量级)
     *
     * 物理计算 (SATP: 25°C, 1 atm, 摩尔体积 = 24.465 L/mol, SO2 = 64.066 g/mol)：
     * 1 ppm SO₂ = (1/24.465) × 64.066 = 2.619 mg/m³
     * 1 ppb SO₂ = (0.001/24.465) × 64.066 = 0.002619 mg/m³ = 2.619 μg/m³
     */
    @Test
    public void testConvert_SO2_PPB_to_UGM3_SATP_Example() {
        // 测试 ppm ↔ mg/m³ (同量级)
        AirVolumeToAirMass so2PpmToMgm3 = new AirVolumeToAirMass(
            AirVolumeUnit.PPM, AirMassUnit.MGM3, MolecularWeights.SO2
        );
        double ppmResult = so2PpmToMgm3.convert(1.0);
        assertEquals("1 ppm SO₂ 应该等于约 2.619 mg/m³", 2.619, ppmResult, 0.001);

        // 测试 ppb ↔ μg/m³ (同量级)
        AirVolumeToAirMass so2PpbToUgm3 = new AirVolumeToAirMass(
            AirVolumeUnit.PPB, AirMassUnit.UGM3, MolecularWeights.SO2
        );
        double ppbResult = so2PpbToUgm3.convert(1.0);
        assertEquals("1 ppb SO₂ 应该等于约 2.619 μg/m³", 2.619, ppbResult, 0.001);

        // 验证量级关系：
        // ppm 结果的数值应该等于 ppb 结果的数值 (因为单位也差1000倍)
        assertEquals("ppm→mg/m³ 和 ppb→μg/m³ 的数值应该相等", ppmResult, ppbResult, 0.001);
    }
}
