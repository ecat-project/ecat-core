package com.ecat.core.State;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测试 NumericAttribute 类的功能
 * 
 * @author coffee
 */
public class NumericAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;
    @Mock
    private Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> mockCallback;

    private NumericAttribute attr;
    private TestUnit nativeUnit;
    private TestUnit displayUnit;

    // 简单实现一个 UnitInfo，便于 getClass() 判断
    static class TestUnit implements UnitInfo {
        private final String name;
        private final double ratio;
        public TestUnit(String name, double ratio) {
            this.name = name;
            this.ratio = ratio;
        }
        @Override
        public String getDisplayName() { return name; }
        @Override
        public String getName() { return name; }
        @Override
        public Double getRatio() { return ratio; }
        @Override
        public Double convertUnit(UnitInfo toUnit) {
            if (!(toUnit instanceof TestUnit)) throw new IllegalArgumentException();
            TestUnit t = (TestUnit) toUnit;
            // 如果单位名不同，视为跨class，抛异常
            if (!t.name.equals(this.name)) throw new RuntimeException("Invalid unit conversion");
            return t.ratio / this.ratio;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("电压");
        nativeUnit = new TestUnit("V", 1.0);
        displayUnit = new TestUnit("V", 2.0);
        attr = new NumericAttribute(
                "voltage",
                mockAttrClass,
                nativeUnit,
                displayUnit,
                2,
                true,
                true,
                mockCallback
        );
    }

    @Test
    public void testConstructorAndGetters() {
        assertEquals("voltage", attr.getAttributeID());
        // 测试时直接验证I18n路径，不依赖具体的资源文件
        assertEquals("state.numeric_attr.voltage", attr.getI18nPrefixPath().withLastSegment("voltage").getI18nPath());
        assertEquals(mockAttrClass, attr.attrClass);
        assertEquals(nativeUnit, attr.nativeUnit);
        assertEquals(displayUnit, attr.displayUnit);
        assertEquals(2, attr.displayPrecision);
        assertTrue(attr.canUnitChange());
        assertTrue(attr.canValueChange());
    }

    @Test
    public void testUpdateValue() {
        assertTrue(attr.updateValue(12.34));
        assertEquals(Double.valueOf(12.34), attr.getValue());
    }

    @Test
    public void testUpdateValueWithStatus() {
        assertTrue(attr.updateValue(56.78, AttributeStatus.NORMAL));
        assertEquals(Double.valueOf(56.78), attr.getValue());
        assertEquals(AttributeStatus.NORMAL, attr.getStatus());
    }

    @Test
    public void testSetDisplayValueImp_SameClass() throws Exception {
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attr.onChangedCallback = mockCallback;
        CompletableFuture<Boolean> future = attr.setDisplayValueImp(10.0, attr.getDisplayUnit());
        assertTrue(future.get());
        // 10.0 * displayUnit.convertUnit(nativeUnit) = 10.0 * (1.0/2.0) = 5.0
        assertEquals(Double.valueOf(5.0), attr.getValue());
    }

    @Test
    public void testSetDisplayValueImp_CrossClass() {
        // 测试跨class单位转换抛异常
        TestUnit otherUnit = new TestUnit("A", 1.0);
        NumericAttribute attr2 = new NumericAttribute(
                "current", mockAttrClass, nativeUnit, otherUnit, 2, true, true, mockCallback);
        try {
            attr2.setDisplayValueImp(10.0, attr2.getDisplayUnit());
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Invalid unit conversion", e.getMessage());
        }
    }

    @Test
    public void testGetDisplayValue_SameClass() {
        attr.value = 20.0;
        String displayValue = attr.getDisplayValue(displayUnit);
        // 20.0 * nativeUnit.convertUnit(displayUnit) = 20.0 * (2.0/1.0) = 40.0
        assertEquals("40.00", displayValue);
    }

    @Test
    public void testGetDisplayValue_CrossClass() {
        // 测试跨class单位转换抛异常
        TestUnit otherUnit = new TestUnit("A", 1.0);
        NumericAttribute attr2 = new NumericAttribute(
                "current", mockAttrClass, nativeUnit, otherUnit, 2, true, true, mockCallback);
        attr2.value = 20.0;
        try {
            attr2.getDisplayValue(otherUnit);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Invalid unit conversion", e.getMessage());
        }
    }

    @Test
    public void testConvertFromUnitImp_SameClass() {
        TestUnit displayUnit2 = new TestUnit("V", 2.0);
        Double result = attr.convertFromUnitImp(10.0, displayUnit2);
        // 10.0 * displayUnit2.convertUnit(nativeUnit) = 10.0 * (1.0/2.0) = 5.0
        assertEquals(Double.valueOf(5.0), result);
    }

    @Test
    public void testConvertFromUnitImp_CrossClass() {
        // 测试跨class单位转换抛异常
        TestUnit otherUnit = new TestUnit("A", 1.0);
        NumericAttribute attr2 = new NumericAttribute(
                "current", mockAttrClass, nativeUnit, otherUnit, 2, true, true, mockCallback);
        try {
            attr2.convertFromUnitImp(10.0, otherUnit);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("Invalid unit conversion", e.getMessage());
        }
    }

    @Test
    public void testDeprecatedConstructorCompatibility() {
        // 测试已弃用的构造函数兼容性
        NumericAttribute deprecatedAttr = new NumericAttribute(
                "deprecatedVoltage",
                "电压",
                mockAttrClass,
                nativeUnit,
                displayUnit,
                2,
                true,
                true,
                mockCallback
        );

        // 验证基本功能仍然正常工作
        assertEquals("deprecatedVoltage", deprecatedAttr.getAttributeID());
        assertEquals(mockAttrClass, deprecatedAttr.attrClass);
        assertEquals(nativeUnit, deprecatedAttr.nativeUnit);
        assertEquals(displayUnit, deprecatedAttr.displayUnit);
        assertEquals(2, deprecatedAttr.displayPrecision);
        assertTrue(deprecatedAttr.canUnitChange());
        assertTrue(deprecatedAttr.canValueChange());

        // 验证I18n路径正确生成
        assertEquals("state.numeric_attr.deprecatedvoltage", deprecatedAttr.getI18nPrefixPath().withLastSegment("deprecatedvoltage").getI18nPath());

        // 验证displayName仍然可访问（已弃用）
        assertEquals("电压", deprecatedAttr.getDisplayName());
    }
}
