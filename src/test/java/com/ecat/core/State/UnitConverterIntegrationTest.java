package com.ecat.core.State;

import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * UnitConverter 集成测试
 * 测试 SameUnitClassConverter 与 UnitConverter 的集成
 *
 * @author coffee
 * @version 1.0.0
 */
public class UnitConverterIntegrationTest {

    private static final double DELTA = 0.001;

    /**
     * 测试 UnitConverter.convertSameUnitClass 方法
     */
    @Test
    public void testConvertSameUnitClassIntegration() {
        UnitConverter converter = new UnitConverter();

        // 测试质量浓度单位转换
        double massResult = converter.convertSameUnitClass(1200.0, AirMassUnit.UGM3, AirMassUnit.MGM3);
        assertEquals(1.2, massResult, DELTA);

        // 测试体积浓度单位转换
        double volumeResult = converter.convertSameUnitClass(2.5, AirVolumeUnit.PPM, AirVolumeUnit.PPB);
        assertEquals(2500.0, volumeResult, DELTA);
    }

    /**
     * 测试异常处理
     */
    @Test(expected = IllegalArgumentException.class)
    public void testConvertSameUnitClassException() {
        UnitConverter converter = new UnitConverter();
        // 尝试转换不同单位类的单位
        converter.convertSameUnitClass(1.0, AirMassUnit.MGM3, AirVolumeUnit.PPM);
    }
}
