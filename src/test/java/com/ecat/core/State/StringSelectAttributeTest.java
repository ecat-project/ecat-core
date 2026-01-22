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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 测试 StringSelectAttribute 类的功能
 * 
 * @author coffee
 */
public class StringSelectAttributeTest {

    @Mock 
    private EcatCore mockEcatCore;
    @Mock
    private DeviceBase mockDevice;
    @Mock 
    private BusRegistry mockBusRegistry;

    @Mock
    private AttributeClass mockAttrClass;
    @Mock
    private Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> mockCallback;

    private TestStringSelectAttribute attr;
    private List<String> options;

    // 测试用 StringSelectAttribute 子类
    static class TestStringSelectAttribute extends StringSelectAttribute {

        /**
         * 支持I18n的构造函数
         */
        public TestStringSelectAttribute(String attributeID, AttributeClass attrClass, boolean valueChangeable, List<String> options, Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
            super(attributeID, attrClass, null, null, valueChangeable, options, onChangedCallback);
        }

        /**
         * @deprecated Use the constructor without displayName instead.
         */
        @Deprecated
        public TestStringSelectAttribute(String attributeID, String displayName, AttributeClass attrClass, boolean valueChangeable, List<String> options, Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
            super(attributeID, displayName, attrClass, null, null, valueChangeable, options, onChangedCallback);
        }

        @Override
        protected CompletableFuture<Boolean> selectOptionImp(String option) {
            if (onChangedCallback != null) {
                return onChangedCallback.apply(new AttrChangedCallbackParams<String>(this, option));
            }
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public I18nKeyPath getI18nPrefixPath() {
            // 支持设备分组路径，如果设备有i18n前缀则使用设备的
            if(device != null && device.getI18nPrefix() != null){
                return device.getI18nPrefix();
            }else{
                // 回退到原有路径结构
                return new I18nKeyPath("state.test_string_select_attr.", "");
            }
        }

        @Override
        public java.util.Map<String, String> getOptionDict() {
            java.util.Map<String, String> dict = new java.util.HashMap<>();
            if (options != null) {
                for (String option : options) {
                    dict.put(option, getOptionI18nName(option));
                }
            }
            return dict;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("模式选择");
        options = Arrays.asList("heigh", "medium", "low");
        attr = new TestStringSelectAttribute("mode", mockAttrClass, true, options, mockCallback);

        TestTools.setPrivateField(attr, "device", mockDevice);
        when(mockDevice.getCore()).thenReturn(mockEcatCore);
        when(mockEcatCore.getBusRegistry()).thenReturn(mockBusRegistry);
        doNothing().when(mockBusRegistry).publish(anyString(), any());
    }

    @Test
    public void testConstructorAndGetters() {
        assertEquals("mode", attr.getAttributeID());
        // 测试时直接验证I18n路径，不依赖具体的资源文件
        assertEquals("state.test_string_select_attr.mode", attr.getI18nPrefixPath().withLastSegment("mode").getI18nPath());
        assertEquals(mockAttrClass, attr.attrClass);
        assertEquals(options, attr.getOptions());
        assertTrue(attr.canValueChange());
        assertFalse(attr.canUnitChange());
    }

    @Test
    public void testGetCurrentOption() {
        attr.value = "heigh";
        assertEquals("heigh", attr.getCurrentOption());
    }

    @Test
    public void testSetDisplayValueImp_Success() throws Exception {
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        CompletableFuture<Boolean> future = attr.setDisplayValueImp("medium", attr.getDisplayUnit());
        assertTrue(future.get());
        assertEquals("medium", attr.getValue());
    }

    @Test
    public void testSetDisplayValueImp_NotAllowed() throws Exception {
        CompletableFuture<Boolean> future = attr.setDisplayValueImp("超heigh", attr.getDisplayUnit());
        assertFalse(future.get());
    }

    @Test
    public void testSetDisplayValueImp_ValueChangeableFalse() throws Exception {
        TestStringSelectAttribute attr2 = new TestStringSelectAttribute("mode2", mockAttrClass, false, options, mockCallback);
        CompletableFuture<Boolean> future = attr2.setDisplayValueImp("heigh", attr.getDisplayUnit());
        assertFalse(future.get());
    }

    @Test
    public void testConvertFromUnitImp() {
        assertEquals("heigh", attr.convertFromUnitImp("heigh", null));
    }

    @Test
    public void testGetDisplayValue() {
        // 测试getDisplayValue方法，现在使用I18n系统
        attr.value = "heigh";
        String onText = attr.getDisplayValue(null);
        assertNotNull(onText);
        assertEquals("state.test_string_select_attr.mode_options.heigh", onText);
        // assertTrue(onText.contains("ON") || onText.contains("on"));
        attr.value = "low";
        String offText = attr.getDisplayValue(null);
        assertNotNull(offText);
        assertEquals("state.test_string_select_attr.mode_options.low", offText);
    }

    @Test
    public void testGetI18nValue() {
        attr.value = "heigh";
        String i18nValue = attr.getI18nValue(null);
        assertEquals("heigh", i18nValue);
    }

    @Test
    public void testSelectOption_Success() throws Exception {
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        CompletableFuture<Boolean> future = attr.selectOption("low");
        assertTrue(future.get());
        assertEquals("low", attr.getValue());
    }

    @Test
    public void testSelectOption_NotAllowed() throws Exception {
        CompletableFuture<Boolean> future = attr.selectOption("超低");
        assertFalse(future.get());
    }

    @Test
    public void testSelectOption_SameValue() throws Exception {
        attr.value = "medium";
        CompletableFuture<Boolean> future = attr.selectOption("medium");
        assertTrue(future.get());
        assertEquals("medium", attr.getI18nValue(null));
    }

    @Test
    public void testSelectOption_ValueChangeableFalse() throws Exception {
        TestStringSelectAttribute attr2 = new TestStringSelectAttribute("mode2", mockAttrClass, false, options, mockCallback);
        CompletableFuture<Boolean> future = attr2.selectOption("heigh");
        assertFalse(future.get());
        assertNull(attr2.getValue());
        assertNull(attr2.getDisplayValue());
    }

    @Test
    public void testDeprecatedConstructorCompatibility() throws Exception {
        // 测试已弃用的构造函数兼容性
        TestStringSelectAttribute deprecatedAttr = new TestStringSelectAttribute("deprecatedMode", "模式选择", mockAttrClass, true, options, mockCallback);
        TestTools.setPrivateField(deprecatedAttr, "device", mockDevice);

        // 验证基本功能仍然正常工作
        assertEquals("deprecatedMode", deprecatedAttr.getAttributeID());
        assertEquals(mockAttrClass, deprecatedAttr.attrClass);
        assertEquals(options, deprecatedAttr.getOptions());
        assertTrue(deprecatedAttr.canValueChange());
        assertFalse(deprecatedAttr.canUnitChange());

        // 验证I18n路径正确生成
        assertEquals("state.test_string_select_attr.deprecatedmode", deprecatedAttr.getI18nPrefixPath().withLastSegment("deprecatedmode").getI18nPath());

        // 验证displayName仍然可访问（已弃用）
        assertEquals("模式选择", deprecatedAttr.getDisplayName());

        // 验证基本操作仍然正常工作
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        try {
            CompletableFuture<Boolean> future = deprecatedAttr.selectOption("low");
            assertTrue(future.get());
            assertEquals("low", deprecatedAttr.getValue());
        } catch (Exception e) {
            fail("Option selection should not fail: " + e.getMessage());
        }
    }

    @Test
    public void testStringSelectOptionI18nPathsWithDevice() throws Exception {
        // 测试options的i18n路径（with device）
        // 当属性绑定到设备时，应该使用设备分组路径
        when(mockDevice.getI18nPrefix()).thenReturn(new I18nKeyPath("devices.test_device.", ""));

        TestStringSelectAttribute attrWithDevice = new TestStringSelectAttribute("testMode", mockAttrClass, true, options, mockCallback);
        TestTools.setPrivateField(attrWithDevice, "device", mockDevice);

        // 验证设备前缀正确应用
        I18nKeyPath devicePrefix = mockDevice.getI18nPrefix();
        assertEquals("devices.test_device.", devicePrefix.getFullPath());

        // 验证options的i18n选项路径前缀
        I18nKeyPath optionPrefix = attrWithDevice.getI18nOptionPathPrefix();
        assertEquals("devices.test_device.testmode_options", optionPrefix.getFullPath());

        // 验证option键路径生成
        String highOptionKey = optionPrefix.getFullPath() + ".heigh";
        assertEquals("devices.test_device.testmode_options.heigh", highOptionKey);

        // 验证option名称解析能够正常工作（不依赖具体值）
        String highOptionName = attrWithDevice.getOptionI18nName("heigh");
        assertNotNull(highOptionName);
        assertTrue(highOptionName.length() > 0);

        // 验证getOptionDict正确生成
        java.util.Map<String, String> optionDict = attrWithDevice.getOptionDict();
        assertNotNull(optionDict.get("heigh"));
        assertNotNull(optionDict.get("medium"));
        assertNotNull(optionDict.get("low"));
    }

    @Test
    public void testStringSelectOptionI18nPathsWithoutDevice() {
        // 测试options的i18n路径（without device）
        // 当属性未绑定到设备时，应该使用默认路径
        TestStringSelectAttribute attrWithoutDevice = new TestStringSelectAttribute("testMode", mockAttrClass, true, options, mockCallback);
        // 不设置device

        // 验证默认i18n前缀
        I18nKeyPath defaultPrefix = attrWithoutDevice.getI18nPrefixPath();
        assertEquals("state.test_string_select_attr.", defaultPrefix.getFullPath());

        // 验证options的i18n选项路径前缀
        I18nKeyPath optionPrefix = attrWithoutDevice.getI18nOptionPathPrefix();
        assertEquals("state.test_string_select_attr.testmode_options", optionPrefix.getFullPath());

        // 验证option键路径生成
        String mediumOptionKey = optionPrefix.getFullPath() + ".medium";
        assertEquals("state.test_string_select_attr.testmode_options.medium", mediumOptionKey);

        // 验证option名称解析能够正常工作（不依赖具体值）
        String mediumOptionName = attrWithoutDevice.getOptionI18nName("medium");
        assertNotNull(mediumOptionName);
        assertTrue(mediumOptionName.length() > 0);

        // 验证getOptionDict仍然正常工作
        java.util.Map<String, String> optionDict = attrWithoutDevice.getOptionDict();
        assertNotNull(optionDict.get("heigh"));
        assertNotNull(optionDict.get("medium"));
        assertNotNull(optionDict.get("low"));
    }

    @Test
    public void testStringSelectOptionI18nPathWithDeviceGrouping() throws Exception {
        // 测试options的设备分组路径
        when(mockDevice.getI18nPrefix()).thenReturn(new I18nKeyPath("devices.weather_sensor.", ""));

        TestStringSelectAttribute attrWithGrouping = new TestStringSelectAttribute("fan_mode", mockAttrClass, true, options, mockCallback);
        TestTools.setPrivateField(attrWithGrouping, "device", mockDevice);

        // 验证设备分组路径正确生成
        I18nKeyPath devicePrefix = attrWithGrouping.getI18nPrefixPath();
        assertEquals("devices.weather_sensor.", devicePrefix.getFullPath());

        // 验证options的i18n选项路径前缀
        I18nKeyPath optionPrefix = attrWithGrouping.getI18nOptionPathPrefix();
        assertEquals("devices.weather_sensor.fan_mode_options", optionPrefix.getFullPath());

        // 验证option键路径生成
        String lowOptionKey = optionPrefix.getFullPath() + ".低";
        assertEquals("devices.weather_sensor.fan_mode_options.低", lowOptionKey);

        // 验证当前选项的i18n名称解析
        attrWithGrouping.value = "low";
        String currentOptionName = attrWithGrouping.getCurrentOptionI18nName();
        assertNotNull(currentOptionName);
        assertTrue(currentOptionName.length() > 0);
    }
}
