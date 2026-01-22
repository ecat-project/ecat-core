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
}
