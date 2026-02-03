package com.ecat.core.State;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.EcatCore;
import com.ecat.core.State.Unit.AirVolumeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        // Set molecularWeight directly since it's a public field
        attr1.molecularWeight = 64.06;  // SO2 molecular weight
        attr2.molecularWeight = 64.06;

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

    @Test(expected = IllegalArgumentException.class)
    public void testGetValueEmpty() {
        // 测试 getValue 子属性为空时抛出异常
        new AQCombineAttribute(
                "tvoc", mockAttrClass, mockNativeUnit, mockDisplayUnit, 2, true, Collections.emptyList());
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
        // 测试 getDisplayValue 返回子属性 getDisplayValue 求和并应用精度格式化
        // combineAttr 的 displayPrecision=2，所以结果应该有2位小数
        when(attr1.getDisplayValue(mockDisplayUnit)).thenReturn("1.2");
        when(attr2.getDisplayValue(mockDisplayUnit)).thenReturn("2.3");
        assertEquals("3.50", combineAttr.getDisplayValue(mockDisplayUnit));
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

    // ========== convertValueToUnit 单元测试 ==========

    @Test
    public void testConvertValueToUnit_SameClass_MassToMass() {
        // 测试同单位类转换：mg/m³ → μg/m³
        // AQCombineAttribute 继承自 AQAttribute，应该支持同单位类转换
        Double result = combineAttr.convertValueToUnit(1.0, mockNativeUnit, mockNativeUnit);
        // 由于 mockNativeUnit 的 convertUnit 方法没有 mock，这里主要验证方法可调用
        assertNotNull(result);
    }

    @Test
    public void testConvertValueToUnit_NullHandling() {
        // 测试 null 值处理
        Double result = combineAttr.convertValueToUnit(null, mockNativeUnit, mockDisplayUnit);
        assertNull(result);
    }

    @Test(expected = NullPointerException.class)
    public void testConvertValueToUnit_NullFromUnit() {
        // 测试 fromUnit 为 null 时抛出异常
        combineAttr.convertValueToUnit(1.0, null, mockDisplayUnit);
    }

    @Test(expected = NullPointerException.class)
    public void testConvertValueToUnit_NullToUnit() {
        // 测试 toUnit 为 null 时抛出异常
        combineAttr.convertValueToUnit(1.0, mockNativeUnit, null);
    }

    @Test
    public void testConvertValueToUnit_WithRealAQAttributes() {
        // 使用真实的 AQAttribute 对象测试跨单位类转换
        AQAttribute so2Attr1 = new AQAttribute("so2_1", mockAttrClass,
                com.ecat.core.State.Unit.AirVolumeUnit.PPM,
                com.ecat.core.State.Unit.AirMassUnit.UGM3, 0, true, false, 64.06);
        AQAttribute so2Attr2 = new AQAttribute("so2_2", mockAttrClass,
                com.ecat.core.State.Unit.AirVolumeUnit.PPM,
                com.ecat.core.State.Unit.AirMassUnit.UGM3, 0, true, false, 64.06);

        List<AQAttribute> speAttrs = Arrays.asList(so2Attr1, so2Attr2);
        AQCombineAttribute realCombineAttr = new AQCombineAttribute(
                "sox", mockAttrClass,
                com.ecat.core.State.Unit.AirVolumeUnit.PPM,
                com.ecat.core.State.Unit.AirMassUnit.UGM3, 0, true, speAttrs);

        // 测试跨单位类转换：ppm → μg/m³
        Double result = realCombineAttr.convertValueToUnit(1.0,
                com.ecat.core.State.Unit.AirVolumeUnit.PPM,
                com.ecat.core.State.Unit.AirMassUnit.UGM3);
        assertNotNull(result);
        assertTrue(result > 2850 && result < 2870);
    }

    @Test
    public void testConvertValueToUnit_RoundTrip() {
        // 测试往返转换：ppm → μg/m³ → ppm
        AQAttribute so2Attr1 = new AQAttribute("so2_1", mockAttrClass,
                com.ecat.core.State.Unit.AirVolumeUnit.PPM,
                com.ecat.core.State.Unit.AirMassUnit.UGM3, 0, true, false, 64.06);

        List<AQAttribute> speAttrs = Arrays.asList(so2Attr1);
        AQCombineAttribute realCombineAttr = new AQCombineAttribute(
                "sox", mockAttrClass,
                com.ecat.core.State.Unit.AirVolumeUnit.PPM,
                com.ecat.core.State.Unit.AirMassUnit.UGM3, 0, true, speAttrs);

        Double original = 0.5;
        Double mass = realCombineAttr.convertValueToUnit(original,
                com.ecat.core.State.Unit.AirVolumeUnit.PPM,
                com.ecat.core.State.Unit.AirMassUnit.UGM3);
        Double backToVolume = realCombineAttr.convertValueToUnit(mass,
                com.ecat.core.State.Unit.AirMassUnit.UGM3,
                com.ecat.core.State.Unit.AirVolumeUnit.PPM);
        assertEquals(original, backToVolume, 0.01);
    }

    @Test
    public void testConvertValueToUnit_DatabaseToDisplayValue() {
        // 测试场景：从数据库读取的值（存储为 ppm）转换为用户显示单位（μg/m³）
        AQAttribute so2Attr1 = new AQAttribute("so2_1", mockAttrClass,
                com.ecat.core.State.Unit.AirVolumeUnit.PPM,
                com.ecat.core.State.Unit.AirMassUnit.UGM3, 1, true, false, 64.06);
        so2Attr1.updateValue(0.5);  // 数据库值为 0.5 ppm

        List<AQAttribute> speAttrs = Arrays.asList(so2Attr1);
        AQCombineAttribute realCombineAttr = new AQCombineAttribute(
                "sox", mockAttrClass,
                com.ecat.core.State.Unit.AirVolumeUnit.PPM,
                com.ecat.core.State.Unit.AirMassUnit.UGM3, 1, true, speAttrs);

        // 获取以 μg/m³ 为单位的显示值
        // 0.5 * 64.06 / 22.4 = 1.43 g/m³ = 1430 μg/m³
        Double displayValue = realCombineAttr.convertValueToUnit(realCombineAttr.getValue(),
                com.ecat.core.State.Unit.AirVolumeUnit.PPM,
                com.ecat.core.State.Unit.AirMassUnit.UGM3);
        assertNotNull(displayValue);
        assertEquals(1429.0, displayValue, 1.0);
    }

    // ========== 小数位精度测试 ==========

    @Test
    public void testGetDisplayValueWithPrecision() {
        // 使用真实的 AQAttribute 对象测试小数位精度格式化
        // NumberFormatter 使用 HALF_EVEN（Banker's rounding）舍入模式
        // 注意：子属性的 getDisplayValue 返回已格式化的字符串，会被解析后再相加
        // 例如：attr1.getDisplayValue(precision=1) 返回 "0.1"，attr2 返回 "0.2"，和为 0.3
        AQAttribute attr1 = new AQAttribute("no", AttributeClass.NO,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false, false, 30.0);
        AQAttribute attr2 = new AQAttribute("no2", AttributeClass.NO2,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false, false, 46.0);

        AQCombineAttribute noxAttr = new AQCombineAttribute(
                "nox_total", AttributeClass.NOX,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false,
                Arrays.asList(attr1, attr2));

        // 设置子属性值（有更多小数位）
        attr1.updateValue(0.14);
        attr2.updateValue(0.24);

        // 子属性格式化：0.14 -> "0.1", 0.24 -> "0.2"
        // 组合属性求和：0.1 + 0.2 = 0.3
        // 组合属性格式化：0.3 -> "0.3"
        String displayValue = noxAttr.getDisplayValue(AirVolumeUnit.PPM);
        assertEquals("0.3", displayValue);
    }

    @Test
    public void testGetDisplayValueWithZeroPrecision() {
        // 测试 displayPrecision=0 的情况（四舍五入到整数）
        // HALF_EVEN: 1.5 rounds to 2 (nearest even), 2.5 rounds to 2
        AQAttribute attr1 = new AQAttribute("no", AttributeClass.NO,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 0, false, false, 30.0);
        AQAttribute attr2 = new AQAttribute("no2", AttributeClass.NO2,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 0, false, false, 46.0);

        AQCombineAttribute noxAttr = new AQCombineAttribute(
                "nox_total", AttributeClass.NOX,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 0, false,
                Arrays.asList(attr1, attr2));

        attr1.updateValue(1.0);
        attr2.updateValue(0.0);

        // NOX total = 1.0, displayPrecision=0, 应显示为 "1"
        String displayValue = noxAttr.getDisplayValue(AirVolumeUnit.PPM);
        assertEquals("1", displayValue);
    }

    @Test
    public void testGetDisplayValueWithTwoPrecision() {
        // 测试 displayPrecision=2 的情况
        AQAttribute attr1 = new AQAttribute("no", AttributeClass.NO,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 2, false, false, 30.0);
        AQAttribute attr2 = new AQAttribute("no2", AttributeClass.NO2,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 2, false, false, 46.0);

        AQCombineAttribute noxAttr = new AQCombineAttribute(
                "nox_total", AttributeClass.NOX,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 2, false,
                Arrays.asList(attr1, attr2));

        attr1.updateValue(0.123);
        attr2.updateValue(0.234);

        // 子属性格式化：0.123 -> "0.12", 0.234 -> "0.23"
        // 组合属性求和：0.12 + 0.23 = 0.35
        // 组合属性格式化：0.35 -> "0.35" (HALF_EVEN, precision=2)
        String displayValue = noxAttr.getDisplayValue(AirVolumeUnit.PPM);
        assertEquals("0.35", displayValue);
    }

    // ========== 数据总线推送测试 ==========

    @Test
    public void testPublicStateWithSubAttributeUpdate() {
        // 使用真实的 AQAttribute 对象
        AQAttribute attr1 = new AQAttribute("no", AttributeClass.NO,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false, false, 30.0);
        AQAttribute attr2 = new AQAttribute("no2", AttributeClass.NO2,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false, false, 46.0);

        AQCombineAttribute noxAttr = new AQCombineAttribute(
                "nox_total", AttributeClass.NOX,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false,
                Arrays.asList(attr1, attr2));

        // Mock device and core
        DeviceBase mockDevice = mock(DeviceBase.class);
        EcatCore mockCore = mock(EcatCore.class);
        BusRegistry mockBusRegistry = mock(BusRegistry.class);

        when(mockDevice.getCore()).thenReturn(mockCore);
        when(mockCore.getBusRegistry()).thenReturn(mockBusRegistry);

        noxAttr.setDevice(mockDevice);

        // 初始状态：子属性未更新，组合属性未更新
        assertFalse(noxAttr.isValueUpdated());

        // 更新子属性
        attr1.updateValue(0.5);
        assertTrue(attr1.isValueUpdated());
        assertFalse(noxAttr.isValueUpdated());  // 组合属性未直接更新

        // 调用 publicState() 应该检测到子属性更新并推送
        // 注意：publicState() 成功后会重置 isValueUpdated 标志
        boolean result = noxAttr.publicState();
        assertTrue(result);  // publicState() 应该返回 true 表示成功
        assertFalse(noxAttr.isValueUpdated());  // publicState() 后应该重置标志

        // 验证推送到数据总线
        verify(mockBusRegistry).publish(eq("device.data.update"), eq(noxAttr));
    }

    @Test
    public void testPublicStateWithoutSubAttributeUpdate() {
        // 测试子属性未更新时不推送
        AQAttribute attr1 = new AQAttribute("no", AttributeClass.NO,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false, false, 30.0);
        AQAttribute attr2 = new AQAttribute("no2", AttributeClass.NO2,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false, false, 46.0);

        AQCombineAttribute noxAttr = new AQCombineAttribute(
                "nox_total", AttributeClass.NOX,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false,
                Arrays.asList(attr1, attr2));

        DeviceBase mockDevice = mock(DeviceBase.class);
        EcatCore mockCore = mock(EcatCore.class);
        BusRegistry mockBusRegistry = mock(BusRegistry.class);

        when(mockDevice.getCore()).thenReturn(mockCore);
        when(mockCore.getBusRegistry()).thenReturn(mockBusRegistry);

        noxAttr.setDevice(mockDevice);

        // 不更新子属性，直接调用 publicState()
        assertFalse(noxAttr.isValueUpdated());
        assertFalse(attr1.isValueUpdated());
        assertFalse(attr2.isValueUpdated());

        // 调用 publicState() 不应该推送（子属性未更新）
        noxAttr.publicState();

        // 验证没有推送到数据总线
        verify(mockBusRegistry, never()).publish(eq("device.data.update"), eq(noxAttr));
    }

    @Test
    public void testPublicStateWithBothSubAttributesUpdated() {
        // 测试两个子属性都更新的情况
        AQAttribute attr1 = new AQAttribute("no", AttributeClass.NO,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false, false, 30.0);
        AQAttribute attr2 = new AQAttribute("no2", AttributeClass.NO2,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false, false, 46.0);

        AQCombineAttribute noxAttr = new AQCombineAttribute(
                "nox_total", AttributeClass.NOX,
                AirVolumeUnit.PPM, AirVolumeUnit.PPM, 1, false,
                Arrays.asList(attr1, attr2));

        DeviceBase mockDevice = mock(DeviceBase.class);
        EcatCore mockCore = mock(EcatCore.class);
        BusRegistry mockBusRegistry = mock(BusRegistry.class);

        when(mockDevice.getCore()).thenReturn(mockCore);
        when(mockCore.getBusRegistry()).thenReturn(mockBusRegistry);

        noxAttr.setDevice(mockDevice);

        // 更新两个子属性
        attr1.updateValue(0.3);
        attr2.updateValue(0.4);

        assertTrue(attr1.isValueUpdated());
        assertTrue(attr2.isValueUpdated());

        // 调用 publicState() 应该推送
        noxAttr.publicState();

        // 验证推送到数据总线
        verify(mockBusRegistry).publish(eq("device.data.update"), eq(noxAttr));
        assertFalse(noxAttr.isValueUpdated());  // publicState() 后应该重置标志
    }
}
