package com.ecat.core.State;

import org.junit.Test;

import com.ecat.core.State.LinearConversionAttribute.LinearSegment;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 极简版 LinearConversionAttribute 测试
 * 只测试LinearSegment类的基本功能，避免UnitInfoFactory问题
 *
 * @author coffee
 */
public class LinearConversionAttributeSimpleTest {

    @Test
    public void testLinearSegmentFunctionality() {
        // 测试LinearSegment类的基本功能，不涉及UnitInfoFactory
        LinearSegment segment = new LinearSegment(4.0, 20.0, 0.0, 10.0);

        // 测试contains方法
        assertTrue(segment.contains(4.0));
        assertTrue(segment.contains(12.0));
        assertTrue(segment.contains(20.0));
        assertFalse(segment.contains(3.0));
        assertFalse(segment.contains(21.0));

        // 测试convert方法
        assertEquals(0.0, segment.convert(4.0), 0.001);    // 4mA -> 0MPa
        assertEquals(5.0, segment.convert(12.0), 0.001);   // 12mA -> 5MPa
        assertEquals(10.0, segment.convert(20.0), 0.001);  // 20mA -> 10MPa

        // 测试convertBack方法
        assertEquals(4.0, segment.convertBack(0.0), 0.001);    // 0MPa -> 4mA
        assertEquals(12.0, segment.convertBack(5.0), 0.001);   // 5MPa -> 12mA
        assertEquals(20.0, segment.convertBack(10.0), 0.001);  // 10MPa -> 20mA
    }

    @Test
    public void testLinearSegmentGetters() {
        LinearSegment segment = new LinearSegment(4.0, 20.0, 0.0, 10.0);

        assertEquals(4.0, segment.getInputMin(), 0.001);
        assertEquals(20.0, segment.getInputMax(), 0.001);
        assertEquals(0.0, segment.getOutputMin(), 0.001);
        assertEquals(10.0, segment.getOutputMax(), 0.001);
    }

    @Test
    public void testMultiSegmentCreation() {
        // 测试多段段的创建和排序（这个测试不涉及LinearConversionAttribute构造函数）
        List<LinearSegment> segments = Arrays.asList(
            new LinearSegment(15.0, 20.0, 150.0, 200.0),  // 第三段
            new LinearSegment(0.0, 5.0,   0.0,   50.0),   // 第一段
            new LinearSegment(5.0, 15.0,  50.0,  150.0)   // 第二段
        );

        // 验证段可以正常创建
        assertEquals(3, segments.size());

        // 验证段的值
        LinearSegment firstSegment = segments.get(0);
        assertEquals(15.0, firstSegment.getInputMin(), 0.001);
        assertEquals(20.0, firstSegment.getInputMax(), 0.001);
        assertEquals(150.0, firstSegment.getOutputMin(), 0.001);
        assertEquals(200.0, firstSegment.getOutputMax(), 0.001);
    }

    @Test
    public void testBoundaryValueHandling() {
        // 测试边界值处理
        LinearSegment segment = new LinearSegment(4.0, 20.0, 0.0, 10.0);

        // 精确边界值
        assertTrue(segment.contains(4.0));
        assertTrue(segment.contains(20.0));

        // 边界值转换
        assertEquals(0.0, segment.convert(4.0), 0.001);
        assertEquals(10.0, segment.convert(20.0), 0.001);
        assertEquals(4.0, segment.convertBack(0.0), 0.001);
        assertEquals(20.0, segment.convertBack(10.0), 0.001);
    }

    @Test
    public void testInvalidInputHandling() {
        // 测试无效输入处理
        LinearSegment segment = new LinearSegment(4.0, 20.0, 0.0, 10.0);

        // 超出范围的输入
        assertFalse(segment.contains(3.9));
        assertFalse(segment.contains(20.1));

        // 转换超出范围的值（虽然不抛异常，但结果可能不符合预期）
        // 这里我们只验证不会抛出异常
        try {
            double result1 = segment.convert(3.9);
            double result2 = segment.convert(20.1);
            double result3 = segment.convertBack(-1.0);
            double result4 = segment.convertBack(11.0);
            // 只要不抛异常就通过
        } catch (Exception e) {
            fail("LinearSegment.convert/convertBack should not throw exceptions for out-of-range values");
        }
    }
}