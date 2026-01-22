package com.ecat.Utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.ecat.core.Utils.NumberFormatter;

public class NumberFormatterTest {
    // 测试 0 值保留 2 位小数
    @Test
    public void testFormatZeroWithTwoPrecision() {
        int value = 0;
        int precision = 2;
        String expected = "0.00";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("0值保留2位小数格式错误", expected, actual);
    }

    // 测试非零浮点数保留 2 位小数
    @Test
    public void testFormatDoubleWithTwoPrecision() {
        double value = 12.345;
        int precision = 2;
        String expected = "12.35";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("非零浮点数保留2位小数格式错误", expected, actual);
    }

    // 测试浮点数末尾补零（如 8.8 → 8.80）
    @Test
    public void testFormatDoubleWithTrailingZero() {
        double value = 8.8;
        int precision = 2;
        String expected = "8.80";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("浮点数末尾补零格式错误", expected, actual);
    }

    // 测试精度为 0 的情况（取整）
    @Test
    public void testFormatWithZeroPrecision() {
        double value = 12.789;
        int precision = 0;
        String expected = "13";  // 四舍五入后取整
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("精度为0时取整错误", expected, actual);
    }

    // 测试负数精度参数（期望抛出异常）
    @Test(expected = IllegalArgumentException.class)
    public void testNegativePrecisionThrowsException() {
        double value = 10.0;
        int invalidPrecision = -1;
        NumberFormatter.formatValue(value, invalidPrecision);
    }

    // 场景1：后一位 <5 → 舍去
    @Test
    public void testLessThanFive() {
        double value = 1.2344;
        int precision = 3;
        String expected = "1.234";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("后一位<5时应舍去", expected, actual);
    }

    // 场景2：后一位 >5 → 进位
    @Test
    public void testMoreThanFive() {
        double value = 1.2346;
        int precision = 3;
        String expected = "1.235";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("后一位>5时应进位", expected, actual);
    }

    // 场景3：后一位=5，前一位是偶数 → 舍去5
    @Test
    public void testFiveWithEvenPrevious() {
        double value1 = 1.2345;  // 前一位是4（偶数）
        double value2 = 1.2365;  // 前一位是6（偶数）
        int precision = 3;

        assertEquals("前一位偶数时应舍去5", "1.234", NumberFormatter.formatValue(value1, precision));
        assertEquals("前一位偶数时应舍去5", "1.236", NumberFormatter.formatValue(value2, precision));
    }

    // 场景4：后一位=5，前一位是奇数 → 进位
    @Test
    public void testFiveWithOddPrevious() {
        double value1 = 1.2335;  // 前一位是3（奇数）
        double value2 = 1.2355;  // 前一位是5（奇数）
        int precision = 3;

        assertEquals("前一位奇数时应进位", "1.234", NumberFormatter.formatValue(value1, precision));
        assertEquals("前一位奇数时应进位", "1.236", NumberFormatter.formatValue(value2, precision));
    }

    // 场景5：0值修约（前一位是0，偶数）
    @Test
    public void testZeroValue() {
        double value = 0.0005;  // 前一位是0（偶数）
        int precision = 3;
        String expected = "0.000";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("0值修约时应舍去5", expected, actual);
    }

    // 场景6：负数精度校验（期望抛异常）
    @Test(expected = IllegalArgumentException.class)
    public void testNegativePrecision() {
        NumberFormatter.formatValue(10.0, -1);
    }
}
