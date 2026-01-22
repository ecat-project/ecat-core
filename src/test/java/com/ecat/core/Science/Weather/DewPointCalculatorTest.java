package com.ecat.core.Science.Weather;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;

import static org.junit.Assert.*;

/**
 * DewPointCalculator 单元测试
 *
 * 测试露点温度计算的各种场景，包括正常情况、边界条件和异常情况
 *
 * @author coffee
 * @version 1.0.0
 */
public class DewPointCalculatorTest {

    // 测试精度 tolerance
    private static final double DELTA = 0.01;

    @Before
    public void setUp() {
        // 测试前的准备工作
    }

    @After
    public void tearDown() {
        // 测试后的清理工作
    }

    /**
     * 测试正常情况下的露点温度计算
     */
    @Test
    public void testCalculateDewPoint_NormalConditions() {
        // 测试用例1：标准室温条件
        double temperature = 25.0;
        double humidity = 60.0;
        double expected = 16.684; // 预期露点温度
        double actual = DewPointCalculator.calculateDewPoint(temperature, humidity);
        assertEquals(expected, actual, DELTA);

        // 测试用例2：高温高湿条件
        temperature = 35.0;
        humidity = 80.0;
        expected = 31.017;
        actual = DewPointCalculator.calculateDewPoint(temperature, humidity);
        assertEquals(expected, actual, DELTA);

        // 测试用例3：低温低湿条件
        temperature = 10.0;
        humidity = 30.0;
        expected = -6.776;
        actual = DewPointCalculator.calculateDewPoint(temperature, humidity);
        assertEquals(expected, actual, DELTA);
    }

    /**
     * 测试边界条件
     */
    @Test
    public void testCalculateDewPoint_BoundaryConditions() {
        // 测试湿度接近100%
        double temperature = 20.0;
        double humidity = 99.0;
        double expected = 19.838;
        double actual = DewPointCalculator.calculateDewPoint(temperature, humidity);
        assertEquals(expected, actual, DELTA);

        // 测试湿度很低的情况
        humidity = 1.0;
        expected = -37.792;
        actual = DewPointCalculator.calculateDewPoint(temperature, humidity);
        assertEquals(expected, actual, DELTA);

        // 测试接近0°C的温度
        temperature = 0.1;
        humidity = 50.0;
        expected = -9.080;
        actual = DewPointCalculator.calculateDewPoint(temperature, humidity);
        assertEquals(expected, actual, DELTA);
    }

    /**
     * 测试异常情况
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCalculateDewPoint_ZeroHumidity() {
        double temperature = 20.0;
        double humidity = 0.0;
        DewPointCalculator.calculateDewPoint(temperature, humidity);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateDewPoint_NegativeHumidity() {
        double temperature = 20.0;
        double humidity = -10.0;
        DewPointCalculator.calculateDewPoint(temperature, humidity);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculateDewPoint_HumidityAbove100() {
        double temperature = 20.0;
        double humidity = 101.0;
        DewPointCalculator.calculateDewPoint(temperature, humidity);
    }

    /**
     * 测试极值温度（应该产生警告但仍计算）
     */
    @Test
    public void testCalculateDewPoint_ExtremeTemperatures() {
        // 测试极低温度
        double temperature = -60.0;
        double humidity = 50.0;
        double result = DewPointCalculator.calculateDewPoint(temperature, humidity);
        // 应该返回一个数值，虽然是极低温度
        assertTrue("极低温度下应该返回数值", Double.isFinite(result));

        // 测试极高温度
        temperature = 60.0;
        humidity = 50.0;
        result = DewPointCalculator.calculateDewPoint(temperature, humidity);
        // 应该返回一个数值，虽然是极高温度
        assertTrue("极高温度下应该返回数值", Double.isFinite(result));
    }

    /**
     * 测试安全计算方法
     */
    @Test
    public void testCalculateDewPointSafe_ValidInput() {
        double temperature = 25.0;
        double humidity = 60.0;
        double expected = 16.684;
        double actual = DewPointCalculator.calculateDewPointSafe(temperature, humidity);
        assertEquals(expected, actual, DELTA);
    }

    @Test
    public void testCalculateDewPointSafe_InvalidInput() {
        double temperature = 25.0;
        double humidity = 0.0; // 无效的湿度值
        double result = DewPointCalculator.calculateDewPointSafe(temperature, humidity);
        assertTrue("无效输入应该返回NaN", Double.isNaN(result));
    }

    /**
     * 测试结露可能性判断
     */
    @Test
    public void testIsCondensationPossible() {
        // 测试会结露的情况（高湿度，温度接近或低于露点）
        assertTrue("高湿度下应该可能结露",
            DewPointCalculator.isCondensationPossible(18.0, 95.0));

        // 测试不会结露的情况（湿度低）
        assertFalse("低湿度下不应该结露",
            DewPointCalculator.isCondensationPossible(25.0, 30.0));

        // 测试边界情况（湿度100%肯定会结露）
        assertTrue("湿度100%时应该结露",
            DewPointCalculator.isCondensationPossible(15.0, 100.0));
    }

    @Test
    public void testIsCondensationPossible_InvalidInput() {
        // 无效输入应该返回false
        assertFalse("无效输入不应该判断为可能结露",
            DewPointCalculator.isCondensationPossible(20.0, 0.0));
    }

    /**
     * 测试一些已知的物理场景
     */
    @Test
    public void testCalculateDewPoint_KnownScenarios() {
        // 场景1：20°C，50%湿度 - 露点约9.3°C
        double temperature = 20.0;
        double humidity = 50.0;
        double expected = 9.254;
        double actual = DewPointCalculator.calculateDewPoint(temperature, humidity);
        assertEquals(expected, actual, DELTA);

        // 场景2：15°C，70%湿度 - 露点约9.6°C
        temperature = 15.0;
        humidity = 70.0;
        expected = 9.571;
        actual = DewPointCalculator.calculateDewPoint(temperature, humidity);
        assertEquals(expected, actual, DELTA);

        // 场景3：30°C，40%湿度 - 露点约14.9°C
        temperature = 30.0;
        humidity = 40.0;
        expected = 14.906;
        actual = DewPointCalculator.calculateDewPoint(temperature, humidity);
        assertEquals(expected, actual, DELTA);
    }
}
