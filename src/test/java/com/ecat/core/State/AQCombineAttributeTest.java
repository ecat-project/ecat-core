package com.ecat.core.State;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测试 AQCombineAttribute 类的功能
 * 
 * @author coffee
 */
public class AQCombineAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;
    @Mock
    private UnitInfo mockNativeUnit;
    @Mock
    private UnitInfo mockDisplayUnit;

    private AQAttribute attr1;
    private AQAttribute attr2;
    private AQCombineAttribute combineAttr;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("TVOC");
        when(mockAttrClass.isValidUnit(any())).thenReturn(true);
        when(mockNativeUnit.getDisplayName()).thenReturn("mg/m3");
        when(mockDisplayUnit.getDisplayName()).thenReturn("mg/m3");

        attr1 = mock(AQAttribute.class);
        attr2 = mock(AQAttribute.class);

        List<AQAttribute> speAttrs = Arrays.asList(attr1, attr2);

        combineAttr = new AQCombineAttribute(
                "tvoc",
                mockAttrClass,
                mockNativeUnit,
                mockDisplayUnit,
                2,
                true,
                speAttrs
        );
    }

    @Test
    public void testGetValueSum() {
        // 测试 getValue 返回子属性求和
        when(attr1.getValue()).thenReturn(1.2);
        when(attr2.getValue()).thenReturn(2.3);
        assertEquals(Double.valueOf(3.5), combineAttr.getValue(), 0.0001);
    }

    @Test
    public void testGetValueEmpty() {
        // 测试 getValue 子属性为空时返回0
        AQCombineAttribute emptyCombine = new AQCombineAttribute(
                "tvoc", mockAttrClass, mockNativeUnit, mockDisplayUnit, 2, true, Collections.emptyList());
        assertEquals(Double.valueOf(0.0), emptyCombine.getValue(), 0.0001);
    }

    @Test(expected = RuntimeException.class)
    public void testSetValueThrows() {
        // 测试 setValue 抛出异常
        combineAttr.setValue(1.0);
    }

    @Test(expected = RuntimeException.class)
    public void testUpdateValueThrows() {
        // 测试 updateValue 抛出异常
        combineAttr.updateValue(1.0);
    }

    @Test(expected = RuntimeException.class)
    public void testUpdateValueWithStatusThrows() {
        // 测试 updateValue(value, status) 抛出异常
        combineAttr.updateValue(1.0, AttributeStatus.NORMAL);
    }

    @Test(expected = RuntimeException.class)
    public void testConvertFromUnitImpThrows() {
        // 测试 convertFromUnitImp 抛出异常
        combineAttr.convertFromUnitImp(1.0, mockNativeUnit);
    }

    @Test
    public void testChangeDisplayUnit() {
        // 测试切换显示单位会同步子属性
        UnitInfo newUnit = mock(UnitInfo.class);
        when(mockAttrClass.isValidUnit(newUnit)).thenReturn(true);
        when(attr1.changeDisplayUnit(newUnit)).thenReturn(true);
        when(attr2.changeDisplayUnit(newUnit)).thenReturn(true);

        assertTrue(combineAttr.changeDisplayUnit(newUnit));
        assertEquals(newUnit, combineAttr.getDisplayUnit());
        verify(attr1).changeDisplayUnit(newUnit);
        verify(attr2).changeDisplayUnit(newUnit);
    }

    @Test
    public void testChangeDisplayUnit_NotAllowed() {
        // 测试不允许切换显示单位
        AQCombineAttribute attr = new AQCombineAttribute(
                "tvoc", mockAttrClass, mockNativeUnit, mockDisplayUnit, 2, false,
                Arrays.asList(attr1, attr2));
        UnitInfo newUnit = mock(UnitInfo.class);
        assertFalse(attr.changeDisplayUnit(newUnit));
    }

    @Test
    public void testSetDisplayValueImpAlwaysFalse() {
        // 测试 setDisplayValueImp 总是返回false
        assertFalse(combineAttr.setDisplayValueImp(1.0, combineAttr.getDisplayUnit()).join());
    }

    @Test
    public void testGetDisplayValueSum() {
        // 测试 getDisplayValue 返回子属性 getDisplayValue 求和
        when(attr1.getDisplayValue(mockDisplayUnit)).thenReturn("1.2");
        when(attr2.getDisplayValue(mockDisplayUnit)).thenReturn("2.3");
        assertEquals("3.5", combineAttr.getDisplayValue(mockDisplayUnit));
    }

    @Test
    public void testGetDisplayValueNullIfAnyNull() {
        // 测试 getDisplayValue 只要有一个子属性为null就返回null
        when(attr1.getDisplayValue(mockDisplayUnit)).thenReturn(null);
        when(attr2.getDisplayValue(mockDisplayUnit)).thenReturn("2.3");
        assertNull(combineAttr.getDisplayValue(mockDisplayUnit));
    }

    @Test
    public void testDeprecatedConstructorCompatibility() {
        // 测试已弃用的构造函数兼容性
        List<AQAttribute> speAttrs = Arrays.asList(attr1, attr2);
        AQCombineAttribute deprecatedAttr = new AQCombineAttribute(
                "deprecatedTvoc",
                "TVOC",
                mockAttrClass,
                mockNativeUnit,
                mockDisplayUnit,
                2,
                true,
                speAttrs
        );

        // 验证基本功能仍然正常工作
        assertEquals("deprecatedTvoc", deprecatedAttr.getAttributeID());
        assertEquals(mockAttrClass, deprecatedAttr.attrClass);
        assertEquals(mockNativeUnit, deprecatedAttr.nativeUnit);
        assertEquals(mockDisplayUnit, deprecatedAttr.displayUnit);
        assertEquals(2, deprecatedAttr.displayPrecision);
        assertTrue(deprecatedAttr.canUnitChange());
        assertFalse(deprecatedAttr.canValueChange());
        assertEquals(speAttrs, deprecatedAttr.speAttrs);

        // 验证I18n路径正确生成
        assertEquals("state.aq_combine_attr.deprecatedtvoc", deprecatedAttr.getI18nPrefixPath().withLastSegment("deprecatedtvoc").getI18nPath());

        // 验证displayName仍然可访问（已弃用）
        assertEquals("TVOC", deprecatedAttr.getDisplayName());

        // 验证基本操作仍然正常工作
        when(attr1.getValue()).thenReturn(1.2);
        when(attr2.getValue()).thenReturn(2.3);
        assertEquals(Double.valueOf(3.5), deprecatedAttr.getValue(), 0.0001);
    }
}
