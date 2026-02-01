package com.ecat.core.State;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.State.Unit.WeightUnit;

/**
 * 测试 AQAttribute 类的功能
 * 
 * @author coffee
 */
public class AQAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;
    @Mock
    private DeviceBase mockDevice;
    @Mock
    private Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> mockCallback;

    private AQAttribute attr;
    private AirMassUnit nativeUnit;
    private AirMassUnit displayUnit;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("AQAttrClass");
        when(mockAttrClass.isValidUnit(any())).thenReturn(true);
        when(mockDevice.getId()).thenReturn("mockDeviceId");
        nativeUnit = AirMassUnit.MGM3;
        displayUnit = AirMassUnit.MGM3;
        attr = new AQAttribute(
                "aq1",
                mockAttrClass,
                nativeUnit,
                displayUnit,
                2,
                true,
                true,
                64.0
        );
        attr.setDevice(mockDevice);
    }

    @Test
    public void testConstructorAndGetters() {
        // 测试构造函数和getter方法，确保属性初始化正确
        assertEquals("aq1", attr.getAttributeID());
        // 测试时直接验证I18n路径，不依赖具体的资源文件
        assertEquals("state.aq_attr.aq1", attr.getI18nPrefixPath().withLastSegment("aq1").getI18nPath());
        assertEquals(mockAttrClass, attr.attrClass);
        assertEquals(nativeUnit, attr.nativeUnit);
        assertEquals(displayUnit, attr.displayUnit);
        assertEquals(2, attr.displayPrecision);
        assertTrue(attr.canUnitChange());
        assertTrue(attr.canValueChange());
        assertEquals(mockDevice, attr.getDevice());
        assertEquals(Double.valueOf(64.0), attr.molecularWeight);
    }

    @Test
    public void testSetAndGetStatus() {
        // 测试设置和获取属性状态
        assertTrue(attr.setStatus(AttributeStatus.NORMAL));
        assertEquals(AttributeStatus.NORMAL, attr.getStatus());
    }

    @Test
    public void testChangeDisplayUnit() {
        // 测试切换显示单位
        AirMassUnit newUnit = AirMassUnit.UGM3;
        when(mockAttrClass.isValidUnit(newUnit)).thenReturn(true);
        assertTrue(attr.changeDisplayUnit(newUnit));
        assertEquals(newUnit, attr.getDisplayUnit());
    }

    @Test
    public void testChangeDisplayUnit_NotAllowed() {
        // 测试不允许切换显示单位的情况
        AQAttribute attr2 = new AQAttribute(
                "aq2", mockAttrClass, nativeUnit, displayUnit, 2, false, true, 64.0);
        attr2.setDevice(mockDevice);
        AirMassUnit newUnit = AirMassUnit.UGM3;
        assertFalse(attr2.changeDisplayUnit(newUnit));
    }

    @Test
    public void testChangeDisplayPrecision() {
        // 测试切换显示精度
        assertTrue(attr.changeDisplayPrecision(5));
    }

    @Test
    public void testGetDisplayUnitStr() {
        // 测试获取显示单位字符串
        assertEquals("mg/m3", attr.getDisplayUnitStr());
        AQAttribute attr2 = new AQAttribute(
                "aq2", mockAttrClass, nativeUnit, null, 2, true, true, 64.0);
        attr2.setDevice(mockDevice);
        assertEquals("", attr2.getDisplayUnitStr());
    }

    @Test
    public void testSetDisplayValue_Success() throws Exception {
        // 测试通过字符串设置显示值并成功回调
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attr.onChangedCallback = mockCallback;
        CompletableFuture<Boolean> future = attr.setDisplayValue("123.45");
        assertTrue(future.get());
        assertEquals(Double.valueOf(123.45), attr.getValue());
    }

    @Test
    public void testSetDisplayValue_TypeConversionFail() {
        // 测试设置显示值时类型转换失败
        CompletableFuture<Boolean> future = attr.setDisplayValue("notADouble");
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    public void testSetDisplayValueImpAndSetValue() throws Exception {
        // 测试直接设置显示值并回调
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attr.onChangedCallback = mockCallback;
        CompletableFuture<Boolean> future = attr.setDisplayValueImp(456.78, attr.getDisplayUnit());
        assertTrue(future.get());
        assertEquals(Double.valueOf(456.78), attr.getValue());
    }

    @Test
    public void testSetValue_ValueChangeableFalse() throws Exception {
        // 测试属性不可更改值时setValue返回false
        AQAttribute attr2 = new AQAttribute(
                "aq2", mockAttrClass, nativeUnit, displayUnit, 2, true, false, 64.0);
        attr2.setDevice(mockDevice);
        CompletableFuture<Boolean> future = attr2.setValue(789.01);
        assertFalse(future.get());
    }

    @Test
    public void testSetValueUpdatedAndUpdateTime() {
        // 测试设置值已更新并获取更新时间
        attr.setValueUpdated(true);
        assertTrue(attr.isValueUpdated());
        assertNotNull(attr.getUpdateTime());
    }

    @Test
    public void testUpdateValueAndStatus() {
        // 测试更新属性值和状态
        assertTrue(attr.updateValue(111.11));
        assertEquals(Double.valueOf(111.11), attr.getValue());
        assertTrue(attr.updateValue(222.22, AttributeStatus.NORMAL));
        assertEquals(Double.valueOf(222.22), attr.getValue());
        assertEquals(AttributeStatus.NORMAL, attr.getStatus());
    }

    @Test
    public void testPublicState_Success() {
        // 测试属性状态发布成功
        attr.setValueUpdated(true);
        DeviceBase device = mock(DeviceBase.class, RETURNS_DEEP_STUBS);
        when(device.getId()).thenReturn("mockDeviceId");
        attr.setDevice(device);

        BusRegistry mockBusRegistry = mock(BusRegistry.class);
        when(device.getCore().getBusRegistry()).thenReturn(mockBusRegistry);
        doNothing().when(mockBusRegistry).publish(anyString(), any());
        assertTrue(attr.publicState());
        assertFalse(attr.isValueUpdated());
    }

    @Test
    public void testPublicState_Fail() {
        // 测试属性状态发布失败
        attr.setValueUpdated(true);
        DeviceBase device = mock(DeviceBase.class);
        attr.setDevice(device);
        when(device.getCore()).thenThrow(new RuntimeException("fail"));
        assertFalse(attr.publicState());
    }

    @Test
    public void testSetDisplayValue_WithUnit() throws Exception {
        // 测试带单位设置显示值
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attr.onChangedCallback = mockCallback;
        CompletableFuture<Boolean> future = attr.setDisplayValue("321.12", displayUnit);
        assertTrue(future.get());
        assertEquals(Double.valueOf(321.12), attr.getValue());
    }

    @Test
    public void testSetValue_WithUnit_NullUnit() {
        // 测试设置值时单位为null抛出异常
        AQAttribute attr2 = new AQAttribute(
                "aq2", mockAttrClass, nativeUnit, displayUnit, 2, true, true, 64.0);
        attr2.setDevice(mockDevice);
        attr2.value = 1.0;
        try {
            attr2.setValue(2.0, null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetValue_WithUnit_NativeUnitNull() {
        // 测试设置值时nativeUnit为null抛出异常
        AQAttribute attr2 = new AQAttribute(
                "aq2", mockAttrClass, null, displayUnit, 2, true, true, 64.0);
        attr2.setDevice(mockDevice);
        attr2.value = 1.0;
        try {
            attr2.setValue(2.0, displayUnit);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetValue_WithUnit_ValueNull() {
        // 测试设置值时value为null返回future，同时测试转换单位设置值
        AirVolumeUnit displayUnit2 = AirVolumeUnit.PPB;
        AirMassUnit nativeUnit2 = AirMassUnit.MGM3;
        AQAttribute attr2 = new AQAttribute(
                "O3", mockAttrClass, nativeUnit2, displayUnit2, 2, true, true, 48.0);
        attr2.setDevice(mockDevice);
        attr2.value = null;
        assertNotNull(attr2.setValue(2.0, AirVolumeUnit.PPM));
        assertEquals(4.285, attr2.getValue(), 0.001);
    }

    // 跨class单位转换测试
    @Test
    public void testSetDisplayValueImp_CrossClassUnit_VolumeToMass() throws Exception {
        // 测试O3分子量=48，displayUnit为体积单位，nativeUnit为质量单位的跨class转换
        AirVolumeUnit displayUnit2 = AirVolumeUnit.PPM;
        AirMassUnit nativeUnit2 = AirMassUnit.MGM3;
        AQAttribute attrO3 = new AQAttribute(
                "O3", mockAttrClass, nativeUnit2, displayUnit2, 2, true, true, 48.0);
        attrO3.setDevice(mockDevice);
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attrO3.onChangedCallback = mockCallback;

        CompletableFuture<Boolean> future = attrO3.setDisplayValueImp(1.0, attrO3.getDisplayUnit());
        assertTrue(future.get());
        assertNotNull(attrO3.getValue());
        assertEquals(Double.valueOf(2.142), attrO3.getValue(), 0.001);
    }

    @Test
    public void testSetDisplayValueImp_CrossClassUnit_MassToVolume() throws Exception {
        // 测试O3分子量=48，displayUnit为质量单位，nativeUnit为体积单位的跨class转换
        AirVolumeUnit nativeUnit2 = AirVolumeUnit.PPM;
        AirMassUnit displayUnit2 = AirMassUnit.MGM3;
        AQAttribute attrO3 = new AQAttribute(
                "O3", mockAttrClass, nativeUnit2, displayUnit2, 2, true, true, 48.0);
        attrO3.setDevice(mockDevice);
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attrO3.onChangedCallback = mockCallback;

        CompletableFuture<Boolean> future = attrO3.setDisplayValueImp(1.0, attrO3.getDisplayUnit());
        assertTrue(future.get());
        assertNotNull(attrO3.getValue());
        assertEquals(Double.valueOf(0.466), attrO3.getValue(), 0.001);
    }

    @Test
    public void testSetDisplayValueImp_CrossClassUnit_Invalid() {
        // 测试O3分子量=48，displayUnit为体积单位，nativeUnit为重量单位且两者类型不同，无法转换时抛出异常
        AirVolumeUnit displayUnit2 = AirVolumeUnit.PPM;
        // 这里用 WeightUnit.KG 作为 nativeUnit，但 displayUnit2 是体积单位，且没有合法的转换关系
        WeightUnit nativeUnit2 = WeightUnit.KG; // 假设这是一个重量单位
        // 这里模拟一个不支持的转换关系
        AQAttribute attrO3 = new AQAttribute(
                "O3", mockAttrClass, nativeUnit2, displayUnit2, 2, true, true, 48.0);
        attrO3.setDevice(mockDevice);
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attrO3.onChangedCallback = mockCallback;

        try {
            // 这里模拟一种不被支持的跨class转换（如实现里没有对应的转换分支）
            attrO3.setDisplayValueImp(1.0, attrO3.getDisplayUnit()).get();
            fail("Expected RuntimeException for invalid cross-class conversion");
        } catch (Exception e) {
            assertTrue(e instanceof RuntimeException);
        }
    }

    @Test
    public void testDeprecatedConstructorCompatibility() {
        // 测试已弃用的构造函数兼容性
        AQAttribute deprecatedAttr = new AQAttribute(
                "deprecatedAQ",
                "AQ Attribute",
                mockAttrClass,
                nativeUnit,
                displayUnit,
                2,
                true,
                true,
                64.0
        );
        deprecatedAttr.setDevice(mockDevice);

        // 验证基本功能仍然正常工作
        assertEquals("deprecatedAQ", deprecatedAttr.getAttributeID());
        assertEquals(mockAttrClass, deprecatedAttr.attrClass);
        assertEquals(nativeUnit, deprecatedAttr.nativeUnit);
        assertEquals(displayUnit, deprecatedAttr.displayUnit);
        assertEquals(2, deprecatedAttr.displayPrecision);
        assertTrue(deprecatedAttr.canUnitChange());
        assertTrue(deprecatedAttr.canValueChange());
        assertEquals(mockDevice, deprecatedAttr.getDevice());
        assertEquals(Double.valueOf(64.0), deprecatedAttr.molecularWeight);

        // 验证I18n路径正确生成
        assertEquals("state.aq_attr.deprecatedaq", deprecatedAttr.getI18nPrefixPath().withLastSegment("deprecatedaq").getI18nPath());

        // 验证displayName仍然可访问（已弃用）
        assertEquals("AQ Attribute", deprecatedAttr.getDisplayName());

        // 验证基本操作仍然正常工作
        assertTrue(deprecatedAttr.updateValue(123.45));
        assertEquals(Double.valueOf(123.45), deprecatedAttr.getValue());
    }

    // ========== convertValueToUnit 单元测试 ==========

    @Test
    public void testConvertValueToUnit_SameClass_MassToMass() {
        // 测试同单位类转换：质量浓度单位之间转换 mg/m³ → μg/m³
        // SO2 分子量 = 64.06
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirMassUnit.MGM3, AirMassUnit.UGM3, 0, true, false, 64.06);

        Double result = so2Attr.convertValueToUnit(1.0, AirMassUnit.MGM3, AirMassUnit.UGM3);
        // mg/m³ → μg/m³: 1.0 * (1.0/1000.0) = 0.001, but we're using ratio from UnitConverter
        // Using SameUnitClassConverter: result = 1.0 * from.ratio / to.ratio = 1.0 * 1.0 / 1000.0 = 0.001
        // Actually, the conversion is mg/m³ to μg/m³, so 1 mg/m³ = 1000 μg/m³
        // Looking at MGM3.getRatio() and UGM3.getRatio():
        assertEquals(1000.0, result, 1.0);
    }

    @Test
    public void testConvertValueToUnit_SameClass_VolumeToVolume() {
        // 测试同单位类转换：体积浓度单位之间转换 ppm → ppb
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, 64.06);

        Double result = so2Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirVolumeUnit.PPB);
        // 1 ppm = 1000 ppb
        assertEquals(1000.0, result, 1.0);
    }

    @Test
    public void testConvertValueToUnit_CrossClass_VolumeToMass() {
        // 测试跨单位类转换：ppm → μg/m³
        // SO2 分子量 = 64.06 g/mol
        // 公式: result = value × from.ratio × MW / 22.4 / to.ratio
        // result = 1.0 × 1.0 × 64.06 / 22.4 / 1.0 ≈ 2.859 g/m³ = 2859 μg/m³
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, 64.06);

        Double result = so2Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.UGM3);
        assertEquals(2859.0, result, 1.0);
    }

    @Test
    public void testConvertValueToUnit_CrossClass_MassToVolume() {
        // 测试跨单位类转换：μg/m³ → ppm
        // SO2 分子量 = 64.06 g/mol
        // 反向转换验证
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, 64.06);

        // 先获取 ppm → μg/m³ 的转换结果
        Double massValue = so2Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.UGM3);
        // 再反向转换回来，应该得到约 1.0 ppm
        Double volumeValue = so2Attr.convertValueToUnit(massValue, AirMassUnit.UGM3, AirVolumeUnit.PPM);
        assertEquals(1.0, volumeValue, 0.01);
    }

    @Test
    public void testConvertValueToUnit_CrossClass_Ozone() {
        // 测试跨单位类转换：O3 分子量 = 48 g/mol
        // result = 1.0 × 1.0 × 48.0 / 22.4 / 1.0 ≈ 2.14 g/m³ = 2140 μg/m³
        AQAttribute o3Attr = new AQAttribute("o3", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, 48.0);

        // ppm → μg/m³
        Double massValue = o3Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.UGM3);
        assertEquals(2142.0, massValue, 1.0);

        // 反向转换 μg/m³ → ppm
        Double volumeValue = o3Attr.convertValueToUnit(massValue, AirMassUnit.UGM3, AirVolumeUnit.PPM);
        assertEquals(1.0, volumeValue, 0.01);
    }

    @Test
    public void testConvertValueToUnit_CrossClass_NO2() {
        // 测试跨单位类转换：NO2 分子量 = 46 g/mol
        // result = 1.0 × 1.0 × 46.0 / 22.4 / 1.0 ≈ 2.05 g/m³ = 2050 μg/m³
        AQAttribute no2Attr = new AQAttribute("no2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, 46.0);

        // ppm → μg/m³
        Double massValue = no2Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.UGM3);
        assertEquals(2053.0, massValue, 1.0);

        // 反向转换 μg/m³ → ppm
        Double volumeValue = no2Attr.convertValueToUnit(massValue, AirMassUnit.UGM3, AirVolumeUnit.PPM);
        assertEquals(1.0, volumeValue, 0.01);
    }

    @Test
    public void testConvertValueToUnit_CrossClass_CO() {
        // 测试跨单位类转换：CO 分子量 = 28 g/mol
        // result = 1.0 × 1.0 × 28.0 / 22.4 / 1.0 ≈ 1.25
        AQAttribute coAttr = new AQAttribute("co", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.MGM3, 0, true, false, 28.0);

        // ppm → mg/m³
        Double massValue = coAttr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.MGM3);
        assertEquals(1.25, massValue, 0.01);

        // 反向转换 mg/m³ → ppm
        Double volumeValue = coAttr.convertValueToUnit(massValue, AirMassUnit.MGM3, AirVolumeUnit.PPM);
        assertEquals(1.0, volumeValue, 0.01);
    }

    @Test
    public void testConvertValueToUnit_NullHandling() {
        // 测试 null 值处理
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, 64.06);

        assertNull(so2Attr.convertValueToUnit(null, AirVolumeUnit.PPM, AirMassUnit.UGM3));
    }

    @Test(expected = NullPointerException.class)
    public void testConvertValueToUnit_NullFromUnit() {
        // 测试 fromUnit 为 null 时抛出异常
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, 64.06);

        so2Attr.convertValueToUnit(1.0, null, AirMassUnit.UGM3);
    }

    @Test(expected = NullPointerException.class)
    public void testConvertValueToUnit_NullToUnit() {
        // 测试 toUnit 为 null 时抛出异常
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, 64.06);

        so2Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, null);
    }

    @Test(expected = RuntimeException.class)
    public void testConvertValueToUnit_InvalidCrossClassConversion() {
        // 测试不支持的跨单位类转换抛出异常
        // 体积单位不能直接转换为重量单位（没有分子量关联）
        AQAttribute attr = new AQAttribute("test", mockAttrClass,
                AirVolumeUnit.PPM, AirVolumeUnit.PPB, 0, true, false, 64.06);

        // 尝试将体积单位转换为重量单位（应该抛出异常）
        attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, WeightUnit.KG);
    }

    @Test
    public void testConvertValueToUnit_RoundTrip() {
        // 测试往返转换：ppm → μg/m³ → ppm 应该得到原值
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, 64.06);

        Double original = 0.5;
        Double mass = so2Attr.convertValueToUnit(original, AirVolumeUnit.PPM, AirMassUnit.UGM3);
        Double backToVolume = so2Attr.convertValueToUnit(mass, AirMassUnit.UGM3, AirVolumeUnit.PPM);
        assertEquals(original, backToVolume, 0.001);
    }

    @Test
    public void testConvertValueToUnit_DatabaseToDisplayValue() {
        // 测试场景：从数据库读取的值（存储为 ppm）转换为用户显示单位（μg/m³）
        // 模拟 SO2 浓度：数据库存储 0.5 ppm，用户希望以 μg/m³ 显示
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 1, true, false, 64.06);
        so2Attr.updateValue(0.5);  // 数据库值为 0.5 ppm

        // 获取以 μg/m³ 为单位的显示值
        // 0.5 * 64.06 / 22.4 = 1.43 g/m³ = 1430 μg/m³
        Double displayValue = so2Attr.convertValueToUnit(so2Attr.getValue(),
                AirVolumeUnit.PPM, AirMassUnit.UGM3);
        assertNotNull(displayValue);
        assertEquals(1429.0, displayValue, 1.0);
    }

    @Test
    public void testConvertValueToUnit_UserInputToDatabase() {
        // 测试场景：用户输入的值（使用 μg/m³）转换为数据库存储单位
        // 模拟用户输入 SO2 浓度 1430 μg/m³，需要转换为 ppm 存储
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 1, true, false, 64.06);

        Double userInput = 1429.0;  // 用户输入 1430 μg/m³
        Double dbValue = so2Attr.convertValueToUnit(userInput,
                AirMassUnit.UGM3, AirVolumeUnit.PPM);
        assertNotNull(dbValue);
        assertEquals(0.5, dbValue, 0.01);
    }
}
