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

    // 测试非零浮点数保留 2 位小数（银行家算法：12.345 → 12.34，因为4是偶数）
    @Test
    public void testFormatDoubleWithTwoPrecision() {
        double value = 12.345;
        int precision = 2;
        String expected = "12.34";  // 银行家算法：第三位4是偶数，后面5舍去
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

    // ==================== roundToDouble 方法测试 ====================

    // 测试 roundToDouble: 0 值保留 2 位小数
    @Test
    public void testRoundToDoubleZeroWithTwoPrecision() {
        int value = 0;
        int precision = 2;
        double expected = 0.00;
        double actual = NumberFormatter.roundToDouble(value, precision);
        assertEquals("roundToDouble: 0值保留2位小数错误", expected, actual, 0.0001);
    }

    // 测试 roundToDouble: 非零浮点数保留 2 位小数（银行家算法：12.345 → 12.34，因为4是偶数）
    @Test
    public void testRoundToDoubleWithTwoPrecision() {
        double value = 12.345;
        int precision = 2;
        double expected = 12.34;  // 银行家算法：第三位4是偶数，后面5舍去
        double actual = NumberFormatter.roundToDouble(value, precision);
        assertEquals("roundToDouble: 非零浮点数保留2位小数错误", expected, actual, 0.0001);
    }

    // 测试 roundToDouble: 精度为 0 的情况（取整）
    @Test
    public void testRoundToDoubleWithZeroPrecision() {
        double value = 12.789;
        int precision = 0;
        double expected = 13.0;
        double actual = NumberFormatter.roundToDouble(value, precision);
        assertEquals("roundToDouble: 精度为0时取整错误", expected, actual, 0.0001);
    }

    // 测试 roundToDouble: 负数精度参数（期望抛出异常）
    @Test(expected = IllegalArgumentException.class)
    public void testRoundToDoubleNegativePrecisionThrowsException() {
        double value = 10.0;
        int invalidPrecision = -1;
        NumberFormatter.roundToDouble(value, invalidPrecision);
    }

    // roundToDouble: 场景1：后一位 <5 → 舍去
    @Test
    public void testRoundToDoubleLessThanFive() {
        double value = 1.2344;
        int precision = 3;
        double expected = 1.234;
        double actual = NumberFormatter.roundToDouble(value, precision);
        assertEquals("roundToDouble: 后一位<5时应舍去", expected, actual, 0.0001);
    }

    // roundToDouble: 场景2：后一位 >5 → 进位
    @Test
    public void testRoundToDoubleMoreThanFive() {
        double value = 1.2346;
        int precision = 3;
        double expected = 1.235;
        double actual = NumberFormatter.roundToDouble(value, precision);
        assertEquals("roundToDouble: 后一位>5时应进位", expected, actual, 0.0001);
    }

    // roundToDouble: 场景3：后一位=5，前一位是偶数 → 舍去5（银行家算法核心）
    @Test
    public void testRoundToDoubleFiveWithEvenPrevious() {
        double value1 = 1.2345;  // 前一位是4（偶数）
        double value2 = 1.2365;  // 前一位是6（偶数）
        int precision = 3;

        assertEquals("roundToDouble: 前一位偶数时应舍去5", 1.234, NumberFormatter.roundToDouble(value1, precision), 0.0001);
        assertEquals("roundToDouble: 前一位偶数时应舍去5", 1.236, NumberFormatter.roundToDouble(value2, precision), 0.0001);
    }

    // roundToDouble: 场景4：后一位=5，前一位是奇数 → 进位（银行家算法核心）
    @Test
    public void testRoundToDoubleFiveWithOddPrevious() {
        double value1 = 1.2335;  // 前一位是3（奇数）
        double value2 = 1.2355;  // 前一位是5（奇数）
        int precision = 3;

        assertEquals("roundToDouble: 前一位奇数时应进位", 1.234, NumberFormatter.roundToDouble(value1, precision), 0.0001);
        assertEquals("roundToDouble: 前一位奇数时应进位", 1.236, NumberFormatter.roundToDouble(value2, precision), 0.0001);
    }

    // roundToDouble: 场景5：0值修约（前一位是0，偶数）
    @Test
    public void testRoundToDoubleZeroValue() {
        double value = 0.0005;  // 前一位是0（偶数）
        int precision = 3;
        double expected = 0.000;
        double actual = NumberFormatter.roundToDouble(value, precision);
        assertEquals("roundToDouble: 0值修约时应舍去5", expected, actual, 0.0001);
    }

    // roundToDouble: 场景6：负数精度校验（期望抛异常）
    @Test(expected = IllegalArgumentException.class)
    public void testRoundToDoubleNegativePrecision() {
        NumberFormatter.roundToDouble(10.0, -1);
    }

    // roundToDouble: 场景7：负数修约测试
    @Test
    public void testRoundToDoubleNegativeValue() {
        double value1 = -1.2345;  // 前一位是4（偶数）
        double value2 = -1.2335;  // 前一位是3（奇数）
        int precision = 3;

        assertEquals("roundToDouble: 负数前一位偶数时应舍去5", -1.234, NumberFormatter.roundToDouble(value1, precision), 0.0001);
        assertEquals("roundToDouble: 负数前一位奇数时应进位", -1.234, NumberFormatter.roundToDouble(value2, precision), 0.0001);
    }

    // roundToDouble: 场景8：Integer 类型测试
    @Test
    public void testRoundToDoubleIntegerInput() {
        Integer value = 123;
        int precision = 1;
        double expected = 123.0;
        double actual = NumberFormatter.roundToDouble(value, precision);
        assertEquals("roundToDouble: Integer类型输入错误", expected, actual, 0.0001);
    }

    // roundToDouble: 场景9：大精度测试
    @Test
    public void testRoundToDoubleHighPrecision() {
        double value = 123.456789;
        int precision = 5;
        double expected = 123.45679;
        double actual = NumberFormatter.roundToDouble(value, precision);
        assertEquals("roundToDouble: 高精度修约错误", expected, actual, 0.000001);
    }

    // ==================== formatValue 边界情况测试（修复 double 精度问题）====================

    // 边界测试1：2.345 → 2.34（前位4是偶数，应舍去5）
    @Test
    public void testFormatValueBoundary2345() {
        double value = 2.345;
        int precision = 2;
        String expected = "2.34";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("边界测试: 2.345应修约为2.34", expected, actual);
    }

    // 边界测试2：3.345 → 3.34（前位4是偶数，应舍去5）
    @Test
    public void testFormatValueBoundary3345() {
        double value = 3.345;
        int precision = 2;
        String expected = "3.34";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("边界测试: 3.345应修约为3.34", expected, actual);
    }

    // 边界测试3：2.355 → 2.36（前位5是奇数，应进位）
    @Test
    public void testFormatValueBoundary2355() {
        double value = 2.355;
        int precision = 2;
        String expected = "2.36";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("边界测试: 2.355应修约为2.36", expected, actual);
    }

    // 边界测试4：2.5 → 2（取整时，前位2是偶数，应舍去5）
    @Test
    public void testFormatValueBoundary25() {
        double value = 2.5;
        int precision = 0;
        String expected = "2";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("边界测试: 2.5取整应为2", expected, actual);
    }

    // 边界测试5：3.5 → 4（取整时，前位3是奇数，应进位）
    @Test
    public void testFormatValueBoundary35() {
        double value = 3.5;
        int precision = 0;
        String expected = "4";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("边界测试: 3.5取整应为4", expected, actual);
    }

    // 边界测试6：负数 -2.345 → -2.34（前位4是偶数，应舍去5）
    @Test
    public void testFormatValueBoundaryNegative2345() {
        double value = -2.345;
        int precision = 2;
        String expected = "-2.34";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("边界测试: -2.345应修约为-2.34", expected, actual);
    }

    // 边界测试7：负数 -2.355 → -2.36（前位5是奇数，应进位）
    @Test
    public void testFormatValueBoundaryNegative2355() {
        double value = -2.355;
        int precision = 2;
        String expected = "-2.36";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("边界测试: -2.355应修约为-2.36", expected, actual);
    }

    // 边界测试8：BigDecimal 输入确保精确处理
    @Test
    public void testFormatValueWithBigDecimalInput() {
        java.math.BigDecimal value = new java.math.BigDecimal("12.345");
        int precision = 2;
        String expected = "12.34";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("BigDecimal输入: 12.345应修约为12.34", expected, actual);
    }

    // 边界测试9：Long 类型输入
    @Test
    public void testFormatValueWithLongInput() {
        Long value = 12345L;
        int precision = 2;
        String expected = "12345.00";
        String actual = NumberFormatter.formatValue(value, precision);
        assertEquals("Long输入: 12345应格式化为12345.00", expected, actual);
    }
}
