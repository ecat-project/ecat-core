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
import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.State.Unit.WeightUnit;
import com.ecat.core.Science.AirQuality.Consts.MolecularWeights;

/**
 * Unit tests for AQAttribute class functionality.
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
        // Test constructor and getter methods to ensure proper initialization
        assertEquals("aq1", attr.getAttributeID());
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
        // Test setting and getting attribute status
        assertTrue(attr.setStatus(AttributeStatus.NORMAL));
        assertEquals(AttributeStatus.NORMAL, attr.getStatus());
    }

    @Test
    public void testChangeDisplayUnit() {
        // Test changing display unit
        AirMassUnit newUnit = AirMassUnit.UGM3;
        when(mockAttrClass.isValidUnit(newUnit)).thenReturn(true);
        assertTrue(attr.changeDisplayUnit(newUnit));
        assertEquals(newUnit, attr.getDisplayUnit());
    }

    @Test
    public void testChangeDisplayUnit_NotAllowed() {
        // Test changing display unit when not allowed
        AQAttribute attr2 = new AQAttribute(
                "aq2", mockAttrClass, nativeUnit, displayUnit, 2, false, true, 64.0);
        attr2.setDevice(mockDevice);
        AirMassUnit newUnit = AirMassUnit.UGM3;
        assertFalse(attr2.changeDisplayUnit(newUnit));
    }

    @Test
    public void testChangeDisplayPrecision() {
        // Test changing display precision
        assertTrue(attr.changeDisplayPrecision(5));
    }

    @Test
    public void testGetDisplayUnitStr() {
        // Test getting display unit string
        assertEquals("mg/m3", attr.getDisplayUnitStr());
        AQAttribute attr2 = new AQAttribute(
                "aq2", mockAttrClass, nativeUnit, null, 2, true, true, 64.0);
        attr2.setDevice(mockDevice);
        assertEquals("", attr2.getDisplayUnitStr());
    }

    @Test
    public void testSetDisplayValue_Success() throws Exception {
        // Test setting display value with string and successful callback
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attr.onChangedCallback = mockCallback;
        CompletableFuture<Boolean> future = attr.setDisplayValue("123.45");
        assertTrue(future.get());
        assertEquals(Double.valueOf(123.45), attr.getValue());
    }

    @Test
    public void testSetDisplayValue_TypeConversionFail() {
        // Test setting display value with type conversion failure
        CompletableFuture<Boolean> future = attr.setDisplayValue("notADouble");
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    public void testSetDisplayValueImpAndSetValue() throws Exception {
        // Test setting display value directly with callback
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attr.onChangedCallback = mockCallback;
        CompletableFuture<Boolean> future = attr.setDisplayValueImp(456.78, attr.getDisplayUnit());
        assertTrue(future.get());
        assertEquals(Double.valueOf(456.78), attr.getValue());
    }

    @Test
    public void testSetValue_ValueChangeableFalse() throws Exception {
        // Test setValue when value is not changeable
        AQAttribute attr2 = new AQAttribute(
                "aq2", mockAttrClass, nativeUnit, displayUnit, 2, true, false, 64.0);
        attr2.setDevice(mockDevice);
        CompletableFuture<Boolean> future = attr2.setValue(789.01);
        assertFalse(future.get());
    }

    @Test
    public void testSetValueUpdatedAndUpdateTime() {
        // Test setting value updated and getting update time
        attr.setValueUpdated(true);
        assertTrue(attr.isValueUpdated());
        assertNotNull(attr.getUpdateTime());
    }

    @Test
    public void testUpdateValueAndStatus() {
        // Test updating attribute value and status
        assertTrue(attr.updateValue(111.11));
        assertEquals(Double.valueOf(111.11), attr.getValue());
        assertTrue(attr.updateValue(222.22, AttributeStatus.NORMAL));
        assertEquals(Double.valueOf(222.22), attr.getValue());
        assertEquals(AttributeStatus.NORMAL, attr.getStatus());
    }

    @Test
    public void testPublicState_Success() {
        // Test successful attribute state publication
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
        // Test failed attribute state publication
        attr.setValueUpdated(true);
        DeviceBase device = mock(DeviceBase.class);
        attr.setDevice(device);
        when(device.getCore()).thenThrow(new RuntimeException("fail"));
        assertFalse(attr.publicState());
    }

    @Test
    public void testSetDisplayValue_WithUnit() throws Exception {
        // Test setting display value with unit
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attr.onChangedCallback = mockCallback;
        CompletableFuture<Boolean> future = attr.setDisplayValue("321.12", displayUnit);
        assertTrue(future.get());
        assertEquals(Double.valueOf(321.12), attr.getValue());
    }

    @Test
    public void testSetValue_WithUnit_NullUnit() {
        // Test setValue with null unit throws exception
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
        // Test setValue with null nativeUnit throws exception
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
        // Test setValue with null value and unit conversion
        AirVolumeUnit displayUnit2 = AirVolumeUnit.PPB;
        AirMassUnit nativeUnit2 = AirMassUnit.MGM3;
        AQAttribute attr2 = new AQAttribute(
                "O3", mockAttrClass, nativeUnit2, displayUnit2, 2, true, true, MolecularWeights.O3);
        attr2.setDevice(mockDevice);
        attr2.value = null;
        assertNotNull(attr2.setValue(2.0, AirVolumeUnit.PPM));
        assertEquals(3.923, attr2.getValue(), 0.001);
    }

    // Cross-class unit conversion tests
    @Test
    public void testSetDisplayValueImp_CrossClassUnit_VolumeToMass() throws Exception {
        // Test O3 with MW=47.998, cross-class conversion from volume to mass
        AirVolumeUnit displayUnit2 = AirVolumeUnit.PPM;
        AirMassUnit nativeUnit2 = AirMassUnit.MGM3;
        AQAttribute attrO3 = new AQAttribute(
                "O3", mockAttrClass, nativeUnit2, displayUnit2, 2, true, true, MolecularWeights.O3);
        attrO3.setDevice(mockDevice);
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attrO3.onChangedCallback = mockCallback;

        CompletableFuture<Boolean> future = attrO3.setDisplayValueImp(1.0, attrO3.getDisplayUnit());
        assertTrue(future.get());
        assertNotNull(attrO3.getValue());
        assertEquals(Double.valueOf(1.962), attrO3.getValue(), 0.001);
    }

    @Test
    public void testSetDisplayValueImp_CrossClassUnit_MassToVolume() throws Exception {
        // Test O3 with MW=47.998, cross-class conversion from mass to volume
        AirVolumeUnit nativeUnit2 = AirVolumeUnit.PPM;
        AirMassUnit displayUnit2 = AirMassUnit.MGM3;
        AQAttribute attrO3 = new AQAttribute(
                "O3", mockAttrClass, nativeUnit2, displayUnit2, 2, true, true, MolecularWeights.O3);
        attrO3.setDevice(mockDevice);
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attrO3.onChangedCallback = mockCallback;

        CompletableFuture<Boolean> future = attrO3.setDisplayValueImp(1.0, attrO3.getDisplayUnit());
        assertTrue(future.get());
        assertNotNull(attrO3.getValue());
        assertEquals(Double.valueOf(0.510), attrO3.getValue(), 0.001);
    }

    @Test
    public void testSetDisplayValueImp_CrossClassUnit_Invalid() {
        // Test invalid cross-class conversion throws exception
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
        // Test deprecated constructor compatibility
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

    // ========== Constructor without molecularWeight tests ==========

    @Test
    public void testConstructor_WithoutMolecularWeight_I18n() {
        // Test constructor without molecularWeight for particulate matter
        AQAttribute pm25Attr = new AQAttribute(
                "pm25_value",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                1,
                false,
                false
        );
        pm25Attr.setDevice(mockDevice);

        assertEquals("pm25_value", pm25Attr.getAttributeID());
        assertNull(pm25Attr.molecularWeight);
        assertFalse(pm25Attr.supportsCrossClassConversion());
    }

    @Test
    public void testConstructor_WithoutMolecularWeight_WithDisplayName() {
        // Test constructor with displayName but without molecularWeight
        AQAttribute pm10Attr = new AQAttribute(
                "pm10_value",
                "PM10 Value",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                1,
                false,
                false
        );
        pm10Attr.setDevice(mockDevice);

        assertEquals("pm10_value", pm10Attr.getAttributeID());
        assertEquals("PM10 Value", pm10Attr.getDisplayName());
        assertNull(pm10Attr.molecularWeight);
        assertFalse(pm10Attr.supportsCrossClassConversion());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_InvalidMolecularWeight_Zero() {
        // Test constructor with zero molecularWeight throws exception
        new AQAttribute(
                "test",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                1,
                false,
                false,
                0.0
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_InvalidMolecularWeight_Negative() {
        // Test constructor with negative molecularWeight throws exception
        new AQAttribute(
                "test",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                1,
                false,
                false,
                -46.0
        );
    }

    @Test
    public void testConstructor_NullMolecularWeight() {
        // Test constructor with explicit null molecularWeight
        AQAttribute pmAttr = new AQAttribute(
                "pm25",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                1,
                false,
                false,
                null
        );

        assertNull(pmAttr.molecularWeight);
        assertFalse(pmAttr.supportsCrossClassConversion());
    }

    // ========== supportsCrossClassConversion tests ==========

    @Test
    public void testSupportsCrossClassConversion_GasPollutant() {
        // Test that gas pollutants with molecularWeight support cross-class conversion
        AQAttribute no2Attr = new AQAttribute(
                "no2",
                mockAttrClass,
                AirVolumeUnit.PPM,
                AirVolumeUnit.PPM,
                2,
                true,
                true,
                46.006
        );

        assertTrue(no2Attr.supportsCrossClassConversion());
    }

    @Test
    public void testSupportsCrossClassConversion_ParticulateMatter() {
        // Test that particulate matter without molecularWeight does not support cross-class conversion
        AQAttribute pm25Attr = new AQAttribute(
                "pm25",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                1,
                false,
                false
        );

        assertFalse(pm25Attr.supportsCrossClassConversion());
    }

    @Test
    public void testSupportsCrossClassConversion_WithNullMolecularWeight() {
        // Test with explicit null molecularWeight
        AQAttribute pm10Attr = new AQAttribute(
                "pm10",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                1,
                false,
                false,
                null
        );

        assertFalse(pm10Attr.supportsCrossClassConversion());
    }

    // ========== Cross-class conversion rejection tests ==========

    @Test(expected = IllegalStateException.class)
    public void testConvertValueToUnit_CrossClass_RejectedForParticulateMatter() {
        // Test that cross-class conversion is rejected for particulate matter
        AQAttribute pm25Attr = new AQAttribute(
                "pm25",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                1,
                false,
                false
        );

        // Try to convert µg/m³ to ppm (should throw IllegalStateException)
        pm25Attr.convertValueToUnit(86.0, AirMassUnit.UGM3, AirVolumeUnit.PPM);
    }

    @Test(expected = IllegalStateException.class)
    public void testConvertValueToUnit_CrossClass_RejectedForParticulateMatter_Reverse() {
        // Test that cross-class conversion is rejected in reverse direction
        AQAttribute pm10Attr = new AQAttribute(
                "pm10",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                1,
                false,
                false
        );

        // Try to convert ppm to µg/m³ (should throw IllegalStateException)
        pm10Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.UGM3);
    }

    @Test(expected = IllegalStateException.class)
    public void testGetDisplayValue_CrossClass_RejectedForParticulateMatter() {
        // Test that getDisplayValue rejects cross-class conversion for particulate matter
        AQAttribute pm25Attr = new AQAttribute(
                "pm25",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                1,
                false,
                false
        );
        pm25Attr.value = 86.0;

        // Try to get display value in ppm (should throw IllegalStateException)
        pm25Attr.getDisplayValue(AirVolumeUnit.PPM);
    }

    @Test
    public void testGetDisplayValue_SameClass_AllowedForParticulateMatter() {
        // Test that same-class conversion is allowed for particulate matter
        AQAttribute pm25Attr = new AQAttribute(
                "pm25",
                mockAttrClass,
                AirMassUnit.UGM3,
                AirMassUnit.UGM3,
                3,
                false,
                false
        );
        pm25Attr.value = 86.0;

        // Should be able to convert to mg/m³ (same class)
        String displayValue = pm25Attr.getDisplayValue(AirMassUnit.MGM3);
        assertNotNull(displayValue);
        // 86 µg/m³ = 0.086 mg/m³
        assertEquals("0.086", displayValue);
    }

    @Test
    public void testSameClassConversion_WithMolecularWeight() {
        // Test that same-class conversion works even when molecularWeight is set
        AQAttribute no2Attr = new AQAttribute(
                "no2",
                mockAttrClass,
                AirVolumeUnit.PPM,
                AirVolumeUnit.PPM,
                2,
                true,
                true,
                46.006
        );
        no2Attr.value = 1.0;

        // Should be able to convert to ppb (same class)
        String displayValue = no2Attr.getDisplayValue(AirVolumeUnit.PPB);
        assertNotNull(displayValue);
        // 1 ppm = 1000 ppb
        assertTrue(displayValue.startsWith("1000"));
    }

    // ========== convertValueToUnit unit tests ==========

    @Test
    public void testConvertValueToUnit_SameClass_MassToMass() {
        // Test same-class conversion: mg/m³ to μg/m³
        // SO2 分子量 = 64.066 g/mol
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirMassUnit.MGM3, AirMassUnit.UGM3, 0, true, false, MolecularWeights.SO2);

        Double result = so2Attr.convertValueToUnit(1.0, AirMassUnit.MGM3, AirMassUnit.UGM3);
        // mg/m³ → μg/m³: 1.0 * (1.0/1000.0) = 0.001, but we're using ratio from UnitConverter
        // Using SameUnitClassConverter: result = 1.0 * from.ratio / to.ratio = 1.0 * 1.0 / 1000.0 = 0.001
        // Actually, the conversion is mg/m³ to μg/m³, so 1 mg/m³ = 1000 μg/m³
        // Looking at MGM3.getRatio() and UGM3.getRatio():
        assertEquals(1000.0, result, 1.0);
    }

    @Test
    public void testConvertValueToUnit_SameClass_VolumeToVolume() {
        // Test same-class conversion: ppm to ppb
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, MolecularWeights.SO2);

        Double result = so2Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirVolumeUnit.PPB);
        // 1 ppm = 1000 ppb
        assertEquals(1000.0, result, 1.0);
    }

    @Test
    public void testConvertValueToUnit_CrossClass_VolumeToMass() {
        // Test cross-class conversion: ppm to μg/m³
        // SO2 MW = 64.066 g/mol
        // 公式: result = value × from.ratio × MW / MOLAR_VOLUME_25C / to.ratio
        // result = 1.0 × 1.0 × 64.066 / 24.465 / 1.0 ≈ 2.619 g/m³ = 2619 μg/m³
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, MolecularWeights.SO2);

        Double result = so2Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.UGM3);
        assertEquals(2619.0, result, 1.0);
    }

    @Test
    public void testConvertValueToUnit_CrossClass_MassToVolume() {
        // Test cross-class conversion: μg/m³ to ppm
        // SO2 分子量 = 64.066 g/mol
        // 反向转换验证
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, MolecularWeights.SO2);

        // 先获取 ppm → μg/m³ 的转换结果
        Double massValue = so2Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.UGM3);
        // 再反向转换回来，应该得到约 1.0 ppm
        Double volumeValue = so2Attr.convertValueToUnit(massValue, AirMassUnit.UGM3, AirVolumeUnit.PPM);
        assertEquals(1.0, volumeValue, 0.01);
    }

    @Test
    public void testConvertValueToUnit_CrossClass_Ozone() {
        // Test cross-class conversion: O3 MW = 47.998 g/mol
        // result = 1.0 × 1.0 × 47.998 / 24.465 / 1.0 ≈ 1.962 g/m³ = 1962 μg/m³
        AQAttribute o3Attr = new AQAttribute("o3", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, MolecularWeights.O3);

        // ppm → μg/m³
        Double massValue = o3Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.UGM3);
        assertEquals(1962.0, massValue, 1.0);

        // 反向转换 μg/m³ → ppm
        Double volumeValue = o3Attr.convertValueToUnit(massValue, AirMassUnit.UGM3, AirVolumeUnit.PPM);
        assertEquals(1.0, volumeValue, 0.01);
    }

    @Test
    public void testConvertValueToUnit_CrossClass_NO2() {
        // Test cross-class conversion: NO2 MW = 46.006 g/mol
        // result = 1.0 × 1.0 × 46.006 / 24.465 / 1.0 ≈ 1.880 g/m³ = 1880 μg/m³
        AQAttribute no2Attr = new AQAttribute("no2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, MolecularWeights.NO2);

        // ppm → μg/m³
        Double massValue = no2Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.UGM3);
        assertEquals(1880.0, massValue, 1.0);

        // 反向转换 μg/m³ → ppm
        Double volumeValue = no2Attr.convertValueToUnit(massValue, AirMassUnit.UGM3, AirVolumeUnit.PPM);
        assertEquals(1.0, volumeValue, 0.01);
    }

    @Test
    public void testConvertValueToUnit_CrossClass_CO() {
        // Test cross-class conversion: CO MW = 28.010 g/mol
        // result = 1.0 × 1.0 × 28.010 / 24.465 / 1.0 ≈ 1.145
        AQAttribute coAttr = new AQAttribute("co", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.MGM3, 0, true, false, MolecularWeights.CO);

        // ppm → mg/m³
        Double massValue = coAttr.convertValueToUnit(1.0, AirVolumeUnit.PPM, AirMassUnit.MGM3);
        assertEquals(1.145, massValue, 0.01);

        // 反向转换 mg/m³ → ppm
        Double volumeValue = coAttr.convertValueToUnit(massValue, AirMassUnit.MGM3, AirVolumeUnit.PPM);
        assertEquals(1.0, volumeValue, 0.01);
    }

    @Test
    public void testConvertValueToUnit_NullHandling() {
        // Test null value handling
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, MolecularWeights.SO2);

        assertNull(so2Attr.convertValueToUnit(null, AirVolumeUnit.PPM, AirMassUnit.UGM3));
    }

    @Test(expected = NullPointerException.class)
    public void testConvertValueToUnit_NullFromUnit() {
        // Test exception when fromUnit is null
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, MolecularWeights.SO2);

        so2Attr.convertValueToUnit(1.0, null, AirMassUnit.UGM3);
    }

    @Test(expected = NullPointerException.class)
    public void testConvertValueToUnit_NullToUnit() {
        // Test exception when toUnit is null
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, MolecularWeights.SO2);

        so2Attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, null);
    }

    @Test(expected = RuntimeException.class)
    public void testConvertValueToUnit_InvalidCrossClassConversion() {
        // Test unsupported cross-class conversion throws exception
        // 体积单位不能直接转换为重量单位（没有分子量关联）
        AQAttribute attr = new AQAttribute("test", mockAttrClass,
                AirVolumeUnit.PPM, AirVolumeUnit.PPB, 0, true, false, MolecularWeights.SO2);

        // 尝试将体积单位转换为重量单位（应该抛出异常）
        attr.convertValueToUnit(1.0, AirVolumeUnit.PPM, WeightUnit.KG);
    }

    @Test
    public void testConvertValueToUnit_RoundTrip() {
        // Test round-trip conversion: ppm → μg/m³ → ppm
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 0, true, false, MolecularWeights.SO2);

        Double original = 0.5;
        Double mass = so2Attr.convertValueToUnit(original, AirVolumeUnit.PPM, AirMassUnit.UGM3);
        Double backToVolume = so2Attr.convertValueToUnit(mass, AirMassUnit.UGM3, AirVolumeUnit.PPM);
        assertEquals(original, backToVolume, 0.001);
    }

    @Test
    public void testConvertValueToUnit_DatabaseToDisplayValue() {
        // Test scenario: database value in ppm to display in μg/m³
        // 模拟 SO2 浓度：数据库存储 0.5 ppm，用户希望以 μg/m³ 显示
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 1, true, false, MolecularWeights.SO2);
        so2Attr.updateValue(0.5);  // 数据库值为 0.5 ppm

        // 获取以 μg/m³ 为单位的显示值
        // 0.5 * 64.066 / 24.465 = 1.309 g/m³ = 1309 μg/m³
        Double displayValue = so2Attr.convertValueToUnit(so2Attr.getValue(),
                AirVolumeUnit.PPM, AirMassUnit.UGM3);
        assertNotNull(displayValue);
        assertEquals(1309.0, displayValue, 1.0);
    }

    @Test
    public void testConvertValueToUnit_UserInputToDatabase() {
        // Test scenario: user input in μg/m³ to database in ppm
        // 模拟用户输入 SO2 浓度 1309 μg/m³，需要转换为 ppm 存储
        AQAttribute so2Attr = new AQAttribute("so2", mockAttrClass,
                AirVolumeUnit.PPM, AirMassUnit.UGM3, 1, true, false, MolecularWeights.SO2);

        Double userInput = 1309.0;
        Double dbValue = so2Attr.convertValueToUnit(userInput,
                AirMassUnit.UGM3, AirVolumeUnit.PPM);
        assertNotNull(dbValue);
        assertEquals(0.5, dbValue, 0.01);
    }
}
