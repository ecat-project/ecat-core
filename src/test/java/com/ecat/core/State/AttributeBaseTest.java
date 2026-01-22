package com.ecat.core.State;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.State.Unit.NoConversionUnit;

/**
 * AttributeBaseTest - Unit tests for AttributeBase class
 *
 * This test class verifies the functionality of AttributeBase including:
 * - Basic attribute operations
 * - I18n support with dynamic path generation
 * - Validation mechanisms
 * - Value conversion and setting
 * - Display value formatting
 * - Unit conversion capabilities
 *
 * @author coffee
 */
// 该测试类使用 Mockito 模拟依赖项，确保 AttributeBase 的基本功能
public class AttributeBaseTest {

    // Minimal concrete subclass for testing
    static class MinimalAttribute extends AttributeBase<Integer> {
        /**
         * 新构造函数，适合固定名称的设备参数国际化支持
         */
        public MinimalAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
                               UnitInfo displayUnit, int displayPrecision, boolean unitChangeable, boolean valueChangeable,
                               Function<AttrChangedCallbackParams<Integer>, CompletableFuture<Boolean>> onChangedCallback) {
            super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable, valueChangeable, onChangedCallback);
        }

        /**
         * 测试用户强制定义显示名称的构造函数，适合名称不固定的设备参数国际化支持
         */
        public MinimalAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
                               UnitInfo displayUnit, int displayPrecision, boolean unitChangeable, boolean valueChangeable,
                               Function<AttrChangedCallbackParams<Integer>, CompletableFuture<Boolean>> onChangedCallback) {
            super(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable, valueChangeable, onChangedCallback);
        }

        @Override
        public String getDisplayValue(UnitInfo toUnit) {
            // For test, just return value as string
            return value == null ? "" : String.valueOf(value);
        }

        @Override
        protected Integer convertFromUnitImp(Integer value, UnitInfo fromUnit) {
            // For test, just return value directly
            return value;
        }

        @Override
        public ConfigDefinition getValueDefinition() {
            return valueDef;
        }

        @Override
        public I18nKeyPath getI18nPrefixPath() {
            return new I18nKeyPath("state.test_attr.", "");
        }

        @Override
        public AttributeType getAttributeType() {
            return AttributeType.UNKNOWN;
        }
    }

    @Mock
    private AttributeClass mockAttrClass;
    @Mock
    private UnitInfo mockNativeUnit;
    @Mock
    private UnitInfo mockDisplayUnit;
    @Mock
    private DeviceBase mockDevice;
    @Mock
    private Function<AttrChangedCallbackParams<Integer>, CompletableFuture<Boolean>> mockCallback;

    private MinimalAttribute attr;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("TestAttrClass");
        when(mockAttrClass.isValidUnit(any())).thenReturn(true);
        when(mockDisplayUnit.getDisplayName()).thenReturn("unit");
        when(mockDevice.getId()).thenReturn("mockDeviceId");
        attr = new MinimalAttribute(
                "attr1",
                mockAttrClass,
                mockNativeUnit,
                mockDisplayUnit,
                2,
                true,
                true,
                mockCallback
        );
        attr.setDevice(mockDevice);
    }

    @Test
    public void testConstructorAndGetters() {
        // 测试构造函数和getter方法，确保属性初始化正确
        assertEquals("attr1", attr.getAttributeID());
        // 测试时直接验证I18n路径，不依赖具体的资源文件
        assertEquals("state.test_attr.attr1", attr.getI18nPrefixPath().withLastSegment("attr1").getI18nPath());
        assertEquals(mockAttrClass, attr.attrClass);
        assertEquals(mockNativeUnit, attr.nativeUnit);
        assertEquals(mockDisplayUnit, attr.displayUnit);
        assertEquals(2, attr.displayPrecision);
        assertTrue(attr.canUnitChange());
        assertTrue(attr.canValueChange());
        assertEquals(mockDevice, attr.getDevice());
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
        UnitInfo newUnit = mock(UnitInfo.class);
        when(mockAttrClass.isValidUnit(newUnit)).thenReturn(true);
        assertTrue(attr.changeDisplayUnit(newUnit));
        assertEquals(newUnit, attr.getDisplayUnit());
    }

    @Test
    public void testChangeDisplayUnit_NotAllowed() {
        // 测试不允许切换显示单位的情况
        MinimalAttribute attr2 = new MinimalAttribute(
                "attr2", "Test2", mockAttrClass, mockNativeUnit, mockDisplayUnit, 2, false, true, null);
        attr2.setDevice(mockDevice);
        UnitInfo newUnit = mock(UnitInfo.class);
        assertFalse(attr2.changeDisplayUnit(newUnit));
    }

    @Test
    public void testChangeDisplayPrecision() {
        // 测试切换显示精度
        assertTrue(attr.changeDisplayPrecision(5));
        // No getter, but no exception means success
    }

    @Test
    public void testGetDisplayUnitStr() {
        // 测试获取显示单位字符串
        assertEquals("unit", attr.getDisplayUnitStr());
        MinimalAttribute attr2 = new MinimalAttribute(
                "attr2", "Test2", mockAttrClass, mockNativeUnit, null, 2, true, true, null);
        attr2.setDevice(mockDevice);
        assertEquals("", attr2.getDisplayUnitStr());
    }

    @Test
    public void testSetDisplayValue_Success() throws Exception {
        // 测试通过字符串设置显示值并成功回调
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        when(mockDevice.getId()).thenReturn("mockDeviceId");
        attr.setDevice(mockDevice);
        CompletableFuture<Boolean> future = attr.setDisplayValue("123");
        assertTrue(future.get());
        assertEquals(Integer.valueOf(123), attr.getValue());
    }

    @Test
    public void testSetDisplayValue_TypeConversionFail() {
        // 测试设置显示值时类型转换失败
        CompletableFuture<Boolean> future = attr.setDisplayValue("notAnInt");
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    public void testSetDisplayValueImpAndSetValue() throws Exception {
        // 测试直接设置显示值并回调
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        when(mockDevice.getId()).thenReturn("mockDeviceId");
        attr.setDevice(mockDevice);
        CompletableFuture<Boolean> future = attr.setDisplayValueImp(456, attr.getDisplayUnit());
        assertTrue(future.get());
        assertEquals(Integer.valueOf(456), attr.getValue());
    }

    @Test
    public void testSetValue_ValueChangeableFalse() throws Exception {
        // 测试属性不可更改值时setValue返回false
        MinimalAttribute attr2 = new MinimalAttribute(
                "attr2", "Test2", mockAttrClass, mockNativeUnit, mockDisplayUnit, 2, true, false, null);
        attr2.setDevice(mockDevice);
        CompletableFuture<Boolean> future = attr2.setValue(789);
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
        assertTrue(attr.updateValue(111));
        assertEquals(Integer.valueOf(111), attr.getValue());
        assertTrue(attr.updateValue(222, AttributeStatus.NORMAL));
        assertEquals(Integer.valueOf(222), attr.getValue());
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
        when(mockDevice.getId()).thenReturn("mockDeviceId");
        attr.setDevice(mockDevice);
        CompletableFuture<Boolean> future = attr.setDisplayValue("321", mockDisplayUnit);
        assertTrue(future.get());
        assertEquals(Integer.valueOf(321), attr.getValue());
    }

    @Test
    public void testSetValue_WithUnit_NullUnit() {
        // 测试设置值时单位为null抛出异常
        MinimalAttribute attr2 = new MinimalAttribute(
                "attr2", "Test2", mockAttrClass, mockNativeUnit, mockDisplayUnit, 2, true, true, null);
        attr2.setDevice(mockDevice);
        attr2.value = 1;
        try {
            attr2.setValue(2, null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetValue_WithUnit_NativeUnitNull() {
        // 测试设置值时nativeUnit为null抛出异常
        MinimalAttribute attr2 = new MinimalAttribute(
                "attr2", "Test2", mockAttrClass, null, mockDisplayUnit, 2, true, true, null);
        attr2.setDevice(mockDevice);
        attr2.value = 1;
        try {
            attr2.setValue(2, mockDisplayUnit);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSetValue_WithUnit_ValueNull() {
        // 测试设置值时value为null返回正常数据
        MinimalAttribute attr2 = new MinimalAttribute(
                "attr2", "Test2", mockAttrClass, mockNativeUnit, mockDisplayUnit, 2, true, true, null);
        attr2.setDevice(mockDevice);
        attr2.value = null;
        assertNotNull(attr2.setValue(2, mockDisplayUnit));
        assertEquals(Integer.valueOf(2), attr2.getValue());
    }

    @Test
    public void testSetDisplayValueImp_WithNUllUnit() {
        // 测试参数创建是某一个单位为null抛出异常
        MinimalAttribute attr2 = new MinimalAttribute(
                "attr2", "Test2", mockAttrClass, mockNativeUnit, null, 2, true, true, null);
        attr2.setDevice(mockDevice);
        attr2.value = 1;
        try {
            attr2.setDisplayValueImp(2, attr2.getDisplayUnit());
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // 测试参数创建时2个单位为null可正确执行
        MinimalAttribute attr3 = new MinimalAttribute(
                "attr3", "Test3", mockAttrClass, null, null, 2, true, true, null);
        attr3.setDevice(mockDevice);
        attr3.value = 1;
        try {
            attr3.setDisplayValueImp(2, attr3.getDisplayUnit());
        } catch (IllegalArgumentException e) {
            // expected
            fail("Should not throw IllegalArgumentException");
        }

        assertEquals(Integer.valueOf(2), attr3.getValue());
    }

    @Test
    public void testI18nSupport() {
        // Test I18n path generation
        I18nKeyPath path = attr.getI18nPrefixPath();
        assertEquals("state.test_attr.", path.getI18nPath());

        // Test getI18nDisplayName - 测试时直接验证I18n路径，不依赖具体的资源文件
        assertEquals("state.test_attr.attr1", attr.getI18nPrefixPath().withLastSegment("attr1").getI18nPath());
    }

    @Test
    public void testValidationMechanism() {
        // Initially no validation
        assertNull(attr.getValueDefinition());
        assertFalse(attr.hasValidation());

        // Test removing validator (null case)
        attr.setValidator(null);
        assertNull(attr.getValueDefinition());
        assertFalse(attr.hasValidation());

        // TODO: Fix type mismatch between NumericRangeValidator<Double> and AttributeBase<Integer>
        // Set validator with range 0-1000 for Integer values
        // NumericRangeValidator validator = new NumericRangeValidator(0.0, 1000.0);
        // attr.setValidator(validator);
        //
        // // Verify validation is set
        // assertNotNull(attr.getValueDefinition());
        // assertTrue(attr.hasValidation());
        //
        // // Test validation with valid value
        // assertTrue(attr.updateValue(500));
        // assertTrue(attr.getValidationErrors().isEmpty());
    }

    @Test
    public void testValidationWithInvalidValues() {
        // Test with null validator - should always pass
        attr.setValidator(null);

        // Test with valid value
        assertTrue(attr.updateValue(50));
        assertTrue(attr.getValidationErrors().isEmpty());

        // Test with null value
        attr.updateValue(null);
        assertTrue(attr.getValidationErrors().isEmpty());

        // TODO: Fix type mismatch between NumericRangeValidator<Double> and AttributeBase<Integer>
        // // Set validator with range 0-100 for Integer values
        // NumericRangeValidator validator = new NumericRangeValidator(0.0, 100.0);
        // attr.setValidator(validator);
        //
        // // Test validation error for value outside range
        // attr.updateValue(150);
        // assertFalse(attr.getValidationErrors().isEmpty());
        //
        // // Test validation error for null value
        // attr.updateValue(null);
        // assertFalse(attr.getValidationErrors().isEmpty());
    }

    @Test
    public void testTypeDetection() {
        // Test value type detection
        assertEquals(AttrValueType.INT, attr.getValueType());
        assertEquals("int", attr.getValueTypeName());
    }

    @Test
    public void testStringConversionWithoutValidation() {
        // Test without validation - set valueDef to null to disable validation
        attr.valueDef = null;

        // Configure mock callback to return success for this test
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));

        // Test valid string conversion
        CompletableFuture<Boolean> result = attr.setDisplayValue("500");
        assertNotNull(result);
        // Don't use join() directly as it might throw, use get() with try-catch
        try {
            Boolean success = result.get();
            assertTrue(success);
            assertEquals(Integer.valueOf(500), attr.getValue());
        } catch (Exception e) {
            fail("Valid conversion should not fail: " + e.getMessage());
        }

        // Test invalid string conversion (type error)
        CompletableFuture<Boolean> typeErrorResult = attr.setDisplayValue("invalid");
        try {
            Boolean success = typeErrorResult.get();
            fail("Expected type conversion to fail");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }

        // TODO: Fix type mismatch between IntegerRangeValidator<Double> and AttributeBase<Integer>
        // // Set validator
        // IntegerRangeValidator validator = new IntegerRangeValidator(0, 1000);
        // attr.setValidator(validator);
        //
        // // Test invalid string conversion (validation error)
        // CompletableFuture<Boolean> invalidResult = attr.setDisplayValue("1500");
        // try {
        //     invalidResult.join();
        //     fail("Expected validation to fail");
        // } catch (Exception e) {
        //     assertTrue(e.getCause() instanceof IllegalArgumentException);
        // }
    }

    @Test
    public void testGetDisplayName() {
        // Test getDisplayName method (should be same as getI18nDisplayName)
        // 测试时直接验证I18n路径，不依赖具体的资源文件
        assertEquals("state.test_attr.attr1", attr.getI18nPrefixPath().withLastSegment("attr1").getI18nPath());

        assertEquals("state.test_attr.attr1", attr.getDisplayName());
        assertEquals("state.test_attr.attr1", attr.getI18nDisplayName());
        

    }

    @Test
    public void testGetValueTypeName() {
        // Test getting value type name
        assertEquals("int", attr.getValueTypeName());
    }

    @Test
    public void testDeprecatedConstructorCompatibility() {
        // Test deprecated constructor compatibility
        MinimalAttribute deprecatedAttr = new MinimalAttribute(
                "deprecatedAttr",
                "Deprecated Display Name",
                mockAttrClass,
                mockNativeUnit,
                mockDisplayUnit,
                2,
                true,
                true,
                mockCallback
        );
        deprecatedAttr.setDevice(mockDevice);

        // Verify basic functionality still works
        assertEquals("deprecatedAttr", deprecatedAttr.getAttributeID());
        assertEquals(mockAttrClass, deprecatedAttr.attrClass);
        assertEquals(mockNativeUnit, deprecatedAttr.nativeUnit);
        assertEquals(mockDisplayUnit, deprecatedAttr.displayUnit);
        assertEquals(2, deprecatedAttr.displayPrecision);
        assertTrue(deprecatedAttr.canUnitChange());
        assertTrue(deprecatedAttr.canValueChange());
        assertEquals(mockDevice, deprecatedAttr.getDevice());

        // Verify I18n still works
        assertEquals("state.test_attr.deprecatedattr", deprecatedAttr.getI18nPrefixPath().withLastSegment("deprecatedattr").getI18nPath());

        // Verify displayName is still accessible (deprecated)
        assertEquals("Deprecated Display Name", deprecatedAttr.getDisplayName());

        // Verify getI18nDisplayName still works (should use attributeID for I18n key)
        assertEquals("state.test_attr.deprecatedattr", deprecatedAttr.getI18nDisplayName());
    }

    @Test
    public void testGetNativeUnitFullStr_WithEnumUnit() {
        // 测试枚举类型单位返回完整字符串
        MinimalAttribute attrWithEnumUnit = new MinimalAttribute(
                "testAttr",
                mockAttrClass,
                AirVolumeUnit.PPM,
                AirVolumeUnit.PPB,
                2,
                true,
                true,
                null
        );
        attrWithEnumUnit.setDevice(mockDevice);

        String fullUnitStr = attrWithEnumUnit.getNativeUnitFullStr();
        assertEquals("AirVolumeUnit.PPM", fullUnitStr);
    }

    @Test
    public void testGetNativeUnitFullStr_WithNonEnumUnit() {
        // 测试非枚举类型单位返回NoConversionUnit.xxxx格式
        NoConversionUnit nonEnumUnit = NoConversionUnit.of("custom_unit");
        MinimalAttribute attrWithNonEnumUnit = new MinimalAttribute(
                "testAttr",
                mockAttrClass,
                nonEnumUnit,
                nonEnumUnit,
                2,
                true,
                true,
                null
        );
        attrWithNonEnumUnit.setDevice(mockDevice);

        String fullUnitStr = attrWithNonEnumUnit.getNativeUnitFullStr();
        assertEquals("NoConversionUnit.custom_unit", fullUnitStr);
    }

    @Test
    public void testGetNativeUnitFullStr_WithNullUnit() {
        // 测试nativeUnit为null时返回空字符串
        MinimalAttribute attrWithNullUnit = new MinimalAttribute(
                "testAttr",
                mockAttrClass,
                null,
                mockDisplayUnit,
                2,
                true,
                true,
                null
        );
        attrWithNullUnit.setDevice(mockDevice);

        String fullUnitStr = attrWithNullUnit.getNativeUnitFullStr();
        assertEquals("", fullUnitStr);
    }

    @Test
    public void testGetFullUnitString_OnEnumUnit() {
        // 测试枚举单位的getFullUnitString方法
        String fullUnitStr = AirVolumeUnit.PPM.getFullUnitString();
        assertEquals("AirVolumeUnit.PPM", fullUnitStr);

        String fullUnitStr2 = AirVolumeUnit.PPB.getFullUnitString();
        assertEquals("AirVolumeUnit.PPB", fullUnitStr2);
    }

    @Test
    public void testGetFullUnitString_OnNonEnumUnit() {
        // 测试非枚举单位的getFullUnitString方法
        NoConversionUnit nonEnumUnit = NoConversionUnit.of("test_unit");
        String fullUnitStr = nonEnumUnit.getFullUnitString();
        assertEquals("NoConversionUnit.test_unit", fullUnitStr);
    }

    @Test
    public void testGetNativeUnitFullStr_DifferentEnumTypes() {
        // 测试不同枚举类型的单位
        MinimalAttribute attrWithAirVolume = new MinimalAttribute(
                "testAttr1",
                mockAttrClass,
                AirVolumeUnit.UMOL_PER_MOL,
                AirVolumeUnit.PPM,
                2,
                true,
                true,
                null
        );
        attrWithAirVolume.setDevice(mockDevice);

        MinimalAttribute attrWithAirVolume2 = new MinimalAttribute(
                "testAttr2",
                mockAttrClass,
                AirVolumeUnit.NMOL_PER_MOL,
                AirVolumeUnit.PPM,
                2,
                true,
                true,
                null
        );
        attrWithAirVolume2.setDevice(mockDevice);

        assertEquals("AirVolumeUnit.UMOL_PER_MOL", attrWithAirVolume.getNativeUnitFullStr());
        assertEquals("AirVolumeUnit.NMOL_PER_MOL", attrWithAirVolume2.getNativeUnitFullStr());
    }
}
