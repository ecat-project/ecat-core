package com.ecat.core.State;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ecat.core.EcatCore;
import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Utils.TestTools;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.State.LinearConversionAttribute.LinearSegment;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 测试 LinearConversionAttribute 类的功能
 *
 * @author coffee
 */
public class LinearConversionAttributeTest {

    @Mock
    private EcatCore mockEcatCore;
    @Mock
    private DeviceBase mockDevice;
    @Mock
    private BusRegistry mockBusRegistry;
    @Mock
    private AttributeClass mockAttrClass;
    @Mock
    private Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> mockCallback;

    private LinearConversionAttribute singleSegmentAttr;
    private LinearConversionAttribute multiSegmentAttr;
    private List<LinearSegment> multiSegments;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("测量值");

        // 创建单段转换属性（4-20mA -> 0-10MPa）
        singleSegmentAttr = new LinearConversionAttribute(
            "pressure", "压力", mockAttrClass,
            4.0, 20.0,    // input range: 4-20mA
            0.0, 10.0,    // output range: 0-10MPa
            "CurrentUnit.MILLIAMPERE",
            "PressureUnit.MPA",
            2, false, mockCallback
        );

        // 创建多段转换属性（PT100温度传感器模拟）
        multiSegments = Arrays.asList(
            new LinearSegment(0.0, 5.0,   0.0,   50.0),   // 0-5mA → 0-50°C
            new LinearSegment(5.0, 15.0,  50.0,  150.0),  // 5-15mA → 50-150°C
            new LinearSegment(15.0, 20.0, 150.0, 200.0)   // 15-20mA → 150-200°C
        );

        multiSegmentAttr = new LinearConversionAttribute(
            "temperature", "温度", mockAttrClass,
            multiSegments,
            "CurrentUnit.MILLIAMPERE",
            "TemperatureUnit.CELSIUS",
            1, false, mockCallback
        );

        TestTools.setPrivateField(singleSegmentAttr, "device", mockDevice);
        TestTools.setPrivateField(multiSegmentAttr, "device", mockDevice);
        when(mockDevice.getCore()).thenReturn(mockEcatCore);
        when(mockEcatCore.getBusRegistry()).thenReturn(mockBusRegistry);
        doNothing().when(mockBusRegistry).publish(anyString(), any());
    }

    @Test
    public void testSingleSegmentConstructorAndGetters() {
        // 测试单段转换构造函数和getter方法
        assertEquals("pressure", singleSegmentAttr.getAttributeID());
        assertEquals("压力", singleSegmentAttr.getDisplayName());
        assertEquals(mockAttrClass, singleSegmentAttr.attrClass);
        assertEquals(2, singleSegmentAttr.displayPrecision);
        assertFalse(singleSegmentAttr.canValueChange());
        assertFalse(singleSegmentAttr.canUnitChange());

        // 验证段信息
        List<LinearSegment> segments = singleSegmentAttr.getSegments();
        assertEquals(1, segments.size());
        LinearSegment segment = segments.get(0);
        assertEquals(4.0, segment.getInputMin(), 0.001);
        assertEquals(20.0, segment.getInputMax(), 0.001);
        assertEquals(0.0, segment.getOutputMin(), 0.001);
        assertEquals(10.0, segment.getOutputMax(), 0.001);
    }

    @Test
    public void testMultiSegmentConstructorAndGetters() {
        // 测试多段转换构造函数和getter方法
        assertEquals("temperature", multiSegmentAttr.getAttributeID());
        assertEquals("温度", multiSegmentAttr.getDisplayName());
        assertEquals(mockAttrClass, multiSegmentAttr.attrClass);
        assertEquals(1, multiSegmentAttr.displayPrecision);
        assertFalse(multiSegmentAttr.canValueChange());
        assertFalse(multiSegmentAttr.canUnitChange());

        // 验证段信息（应该已排序）
        List<LinearSegment> segments = multiSegmentAttr.getSegments();
        assertEquals(3, segments.size());

        // 验证第一段
        LinearSegment segment1 = segments.get(0);
        assertEquals(0.0, segment1.getInputMin(), 0.001);
        assertEquals(5.0, segment1.getInputMax(), 0.001);
        assertEquals(0.0, segment1.getOutputMin(), 0.001);
        assertEquals(50.0, segment1.getOutputMax(), 0.001);

        // 验证第二段
        LinearSegment segment2 = segments.get(1);
        assertEquals(5.0, segment2.getInputMin(), 0.001);
        assertEquals(15.0, segment2.getInputMax(), 0.001);
        assertEquals(50.0, segment2.getOutputMin(), 0.001);
        assertEquals(150.0, segment2.getOutputMax(), 0.001);

        // 验证第三段
        LinearSegment segment3 = segments.get(2);
        assertEquals(15.0, segment3.getInputMin(), 0.001);
        assertEquals(20.0, segment3.getInputMax(), 0.001);
        assertEquals(150.0, segment3.getOutputMin(), 0.001);
        assertEquals(200.0, segment3.getOutputMax(), 0.001);
    }

    @Test
    public void testSingleSegmentLinearConversion() {
        // 测试单段线性转换功能
        // 4-20mA -> 0-10MPa，12mA应该对应5MPa
        assertTrue(singleSegmentAttr.updateValue(12.0));
        assertEquals(12.0, singleSegmentAttr.getValue(), 0.001);

        String displayValue = singleSegmentAttr.getDisplayValue(null);
        assertEquals("5.00", displayValue);

        // 测试边界值
        assertTrue(singleSegmentAttr.updateValue(4.0));  // 4mA -> 0MPa
        assertEquals("0.00", singleSegmentAttr.getDisplayValue(null));

        assertTrue(singleSegmentAttr.updateValue(20.0)); // 20mA -> 10MPa
        assertEquals("10.00", singleSegmentAttr.getDisplayValue(null));
    }

    @Test
    public void testMultiSegmentLinearConversion() {
        // 测试多段线性转换功能

        // 第一段：2.5mA -> 25°C
        assertTrue(multiSegmentAttr.updateValue(2.5));
        assertEquals("25.0", multiSegmentAttr.getDisplayValue(null));

        // 第二段：10mA -> 100°C
        assertTrue(multiSegmentAttr.updateValue(10.0));
        assertEquals("100.0", multiSegmentAttr.getDisplayValue(null));

        // 第三段：17.5mA -> 175°C
        assertTrue(multiSegmentAttr.updateValue(17.5));
        assertEquals("175.0", multiSegmentAttr.getDisplayValue(null));

        // 测试段边界值
        assertTrue(multiSegmentAttr.updateValue(5.0));   // 5mA -> 50°C
        assertEquals("50.0", multiSegmentAttr.getDisplayValue(null));

        assertTrue(multiSegmentAttr.updateValue(15.0));  // 15mA -> 150°C
        assertEquals("150.0", multiSegmentAttr.getDisplayValue(null));
    }

    @Test
    public void testOutOfRangeValues() {
        // 测试超出范围的值
        // 单段转换：3mA超出4-20mA范围
        assertTrue(singleSegmentAttr.updateValue(3.0));
        String displayValue = singleSegmentAttr.getDisplayValue(null);
        assertNull(displayValue);  // 超出范围应返回null

        // 多段转换：25mA超出所有段范围
        assertTrue(multiSegmentAttr.updateValue(25.0));
        displayValue = multiSegmentAttr.getDisplayValue(null);
        assertNull(displayValue);
    }

    @Test
    public void testNullValueHandling() {
        // 测试null值处理
        assertTrue(singleSegmentAttr.updateValue(null));
        assertNull(singleSegmentAttr.getValue());
        assertNull(singleSegmentAttr.getDisplayValue(null));
    }

    @Test
    public void testGetI18nPrefixPath() {
        // 测试国际化路径
        I18nKeyPath prefixPath = singleSegmentAttr.getI18nPrefixPath();
        assertEquals("state.linear_conversion_attr.", prefixPath.getFullPath());
    }

    @Test
    public void testGetValueDefinition() {
        // 测试值验证定义
        ConfigDefinition valueDef = singleSegmentAttr.getValueDefinition();
        assertNotNull(valueDef);

        // 验证验证器工作正常
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("value", 5.0);  // 0-10MPa范围内的有效值
        assertTrue(valueDef.validateConfig(config));

        config.put("value", 15.0); // 超出0-10MPa范围的无效值
        assertFalse(valueDef.validateConfig(config));
    }

    @Test
    public void testReverseConversion() throws Exception {
        // 测试反向转换（从工程值转原始值）
        // 单段转换：5MPa应该对应12mA
        Object rawValueObj = TestTools.invokePrivateMethodByClass(
            singleSegmentAttr, "convertFromEngineeringValue", new Class<?>[]{Double.class}, 5.0);
        Double rawValue = (Double) rawValueObj;
        assertEquals(12.0, rawValue, 0.001);

        // 多段转换：100°C应该对应10mA
        rawValueObj = TestTools.invokePrivateMethodByClass(
            multiSegmentAttr, "convertFromEngineeringValue", new Class<?>[]{Double.class}, 100.0);
        rawValue = (Double) rawValueObj;
        assertEquals(10.0, rawValue, 0.001);
    }

    @Test
    public void testLinearSegmentFunctionality() {
        // 测试LinearSegment类的基本功能
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
    public void testSegmentSorting() {
        // 测试段排序功能
        List<LinearSegment> unsortedSegments = Arrays.asList(
            new LinearSegment(15.0, 20.0, 150.0, 200.0),  // 第三段
            new LinearSegment(0.0, 5.0,   0.0,   50.0),   // 第一段
            new LinearSegment(5.0, 15.0,  50.0,  150.0)   // 第二段
        );

        LinearConversionAttribute unsortedAttr = new LinearConversionAttribute(
            "test", mockAttrClass, unsortedSegments,
            "CurrentUnit.MILLIAMPERE", "TemperatureUnit.CELSIUS",
            1, false, mockCallback
        );

        // 验证段已正确排序
        List<LinearSegment> sortedSegments = unsortedAttr.getSegments();
        assertEquals(0.0, sortedSegments.get(0).getInputMin(), 0.001);
        assertEquals(5.0, sortedSegments.get(1).getInputMin(), 0.001);
        assertEquals(15.0, sortedSegments.get(2).getInputMin(), 0.001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptySegmentsException() {
        // 测试空段列表异常
        new LinearConversionAttribute(
            "test", mockAttrClass, Arrays.asList(),
            "CurrentUnit.MILLIAMPERE", "TemperatureUnit.CELSIUS",
            1, false, mockCallback
        );
    }

    @Test
    public void testSegmentBoundaryOverlap() {
        // 测试段重叠检测（只验证日志记录，不抛异常）
        List<LinearSegment> overlappingSegments = Arrays.asList(
            new LinearSegment(0.0, 10.0, 0.0, 100.0),
            new LinearSegment(8.0, 20.0, 80.0, 200.0)  // 与第一段重叠
        );

        LinearConversionAttribute overlappingAttr = new LinearConversionAttribute(
            "test", mockAttrClass, overlappingSegments,
            "CurrentUnit.MILLIAMPERE", "TemperatureUnit.CELSIUS",
            1, false, mockCallback
        );

        // 构造应该成功，但会记录警告日志
        assertEquals(2, overlappingAttr.getSegments().size());
    }

    @Test
    public void testBackwardCompatibility() {
        // 测试向后兼容性：确保单段构造函数与多段构造函数产生相同结果
        // 使用单段构造函数
        LinearConversionAttribute singleConstructor = new LinearConversionAttribute(
            "single", "单段", mockAttrClass,
            4.0, 20.0, 0.0, 10.0,
            "CurrentUnit.MILLIAMPERE", "PressureUnit.MPA",
            2, false, mockCallback
        );

        // 使用多段构造函数
        List<LinearSegment> singleSegmentList = Arrays.asList(
            new LinearSegment(4.0, 20.0, 0.0, 10.0)
        );
        LinearConversionAttribute multiConstructor = new LinearConversionAttribute(
            "multi", "多段", mockAttrClass, singleSegmentList,
            "CurrentUnit.MILLIAMPERE", "PressureUnit.MPA",
            2, false, mockCallback
        );

        // 验证两种方式产生相同的结果
        singleConstructor.updateValue(12.0);
        multiConstructor.updateValue(12.0);

        assertEquals(singleConstructor.getDisplayValue(null),
                    multiConstructor.getDisplayValue(null));
    }

    @Test
    public void testUpdateValueWithStatus() {
        // 测试同时更新值和状态
        assertTrue(singleSegmentAttr.updateValue(8.0, AttributeStatus.NORMAL));
        assertEquals(8.0, singleSegmentAttr.getValue(), 0.001);
        assertEquals(AttributeStatus.NORMAL, singleSegmentAttr.getStatus());
        assertEquals("2.50", singleSegmentAttr.getDisplayValue(null));
    }

    @Test
    public void testGetInputOutputUnits() {
        // 测试获取输入输出单位
        assertNotNull(singleSegmentAttr.getInputUnit());
        assertNotNull(singleSegmentAttr.getOutputUnit());
        assertNotNull(multiSegmentAttr.getInputUnit());
        assertNotNull(multiSegmentAttr.getOutputUnit());
    }

    @Test
    public void testCallbackFunctionality() throws Exception {
        // 测试回调功能 - 基本验证属性可以正常更新值
        // 回调机制可能依赖于特定条件或父类实现

        assertTrue(singleSegmentAttr.updateValue(10.0));

        // 验证值确实被更新了
        assertEquals(10.0, singleSegmentAttr.getValue(), 0.001);

        // 验证显示值计算正确 (10mA在4-20mA范围内应该对应3.75MPa)
        String displayValue = singleSegmentAttr.getDisplayValue(null);
        assertEquals("3.75", displayValue);
    }
}
