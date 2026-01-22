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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 测试 I18nTextAttribute 类的功能
 *
 * @author coffee
 */
public class I18nTextAttributeTest {

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

    private TestI18nTextAttribute attr;
    private List<String> options;

    // 测试用 I18nTextAttribute 子类
    static class TestI18nTextAttribute extends I18nTextAttribute {

        /**
         * 支持I18n的构造函数
         */
        public TestI18nTextAttribute(String attributeID, AttributeClass attrClass, boolean valueChangeable, List<String> options, Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
            super(attributeID, attrClass, null, null, valueChangeable, options, onChangedCallback);
        }

        /**
         * @deprecated Use the constructor without displayName instead.
         */
        @Deprecated
        public TestI18nTextAttribute(String attributeID, String displayName, AttributeClass attrClass, boolean valueChangeable, List<String> options, Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
            super(attributeID, displayName, attrClass, null, null, valueChangeable, options, onChangedCallback);
        }

        @Override
        public I18nKeyPath getI18nPrefixPath() {
            // 支持设备分组路径，如果设备有i18n前缀则使用设备的
            if(device != null && device.getI18nPrefix() != null){
                return device.getI18nPrefix();
            }else{
                // 回退到原有路径结构
                return new I18nKeyPath("state.test_i18n_text_attr.", "");
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("设备状态");
        options = Arrays.asList("normal", "warning", "error");
        attr = new TestI18nTextAttribute("status", mockAttrClass, true, options, mockCallback);

        TestTools.setPrivateField(attr, "device", mockDevice);
        when(mockDevice.getCore()).thenReturn(mockEcatCore);
        when(mockEcatCore.getBusRegistry()).thenReturn(mockBusRegistry);
        doNothing().when(mockBusRegistry).publish(anyString(), any());
    }

    @Test
    public void testConstructorAndGetters() {
        // 测试构造函数和各个getter方法的基本功能
        // 验证属性ID、国际化路径、属性类、选项列表等基本信息是否正确初始化
        assertEquals("status", attr.getAttributeID());
        assertEquals("state.test_i18n_text_attr.status", attr.getI18nPrefixPath().withLastSegment("status").getI18nPath());
        assertEquals(mockAttrClass, attr.attrClass);
        assertEquals(options, attr.getOptions());
        assertFalse(attr.isCaseSensitive());
        assertTrue(attr.canValueChange());
        assertFalse(attr.canUnitChange());
    }

    @Test
    public void testUpdateValue_ValidOption() {
        // 测试更新有效选项值的功能
        // 验证当设置一个在选项列表中的有效值时，更新是否成功
        assertTrue(attr.updateValue("normal"));
        assertEquals("normal", attr.getValue());
    }

    @Test
    public void testUpdateValue_InvalidOption() {
        // 测试更新无效选项值的功能
        // 验证当设置一个不在选项列表中的无效值时，更新是否被拒绝且原值保持不变
        assertFalse(attr.updateValue("invalid"));
        assertNull(attr.getValue()); // 值不应被更新
    }

    @Test
    public void testUpdateValue_WithStatus_ValidOption() {
        // 测试同时更新值和状态的功能
        // 验证当同时设置有效值和状态时，两者都能正确更新
        assertTrue(attr.updateValue("warning", AttributeStatus.NORMAL));
        assertEquals("warning", attr.getValue());
        assertEquals(AttributeStatus.NORMAL, attr.getStatus());
    }

    @Test
    public void testUpdateValue_WithStatus_InvalidOption() {
        // 测试同时更新值和状态时传入无效值的情况
        // 验证当设置无效值时，即使状态有效，整个更新操作也应该被拒绝
        assertFalse(attr.updateValue("invalid", AttributeStatus.NORMAL));
        assertNull(attr.getValue()); // 值不应被更新
        assertNotEquals(AttributeStatus.NORMAL, attr.getStatus()); // 状态也不应被更新
    }

    @Test
    public void testUpdateValue_NullValue() {
        // 测试更新null值的功能
        // 验证当设置为null时，更新是否成功（null被认为是有效值）
        assertTrue(attr.updateValue(null));
        assertNull(attr.getValue());
    }

    @Test
    public void testGetDisplayValue() {
        // 测试获取国际化显示值的功能
        // 验证getDisplayValue方法能正确返回选项对应的国际化路径
        attr.value = "normal";
        String displayValue = attr.getDisplayValue(null);
        assertNotNull(displayValue);
        assertEquals("state.test_i18n_text_attr.status_options.normal", displayValue);

        attr.value = "warning";
        displayValue = attr.getDisplayValue(null);
        assertNotNull(displayValue);
        assertEquals("state.test_i18n_text_attr.status_options.warning", displayValue);
    }

    @Test
    public void testGetDisplayValue_NullValue() {
        // 测试当值为null时获取显示值的功能
        // 验证当当前值为null时，getDisplayValue是否返回null
        attr.value = null;
        String displayValue = attr.getDisplayValue(null);
        assertNull(displayValue);
    }

    @Test
    public void testGetI18nValue() {
        // 测试获取国际化键值的功能
        // 验证getI18nValue方法能正确返回选项的小写形式作为i18n键
        attr.value = "Normal";
        String i18nValue = attr.getI18nValue(null);
        assertEquals("normal", i18nValue);
    }

    @Test
    public void testGetI18nValue_NullValue() {
        // 测试当值为null时获取国际化键值的功能
        // 验证当当前值为null时，getI18nValue是否返回空字符串
        attr.value = null;
        String i18nValue = attr.getI18nValue(null);
        assertEquals("", i18nValue);
    }

    @Test
    public void testFindOption_CaseSensitive() throws Exception {
        // 测试大小写敏感模式下查找选项的功能
        // 验证在大小写敏感模式下，只有完全匹配的选项才能被找到
        TestI18nTextAttribute caseSensitiveAttr = new TestI18nTextAttribute("test", mockAttrClass, true, options, mockCallback);
        TestTools.setPrivateField(caseSensitiveAttr, "caseSensitive", true);

        assertEquals("normal", caseSensitiveAttr.findOption("normal"));
        assertNull(caseSensitiveAttr.findOption("Normal"));
        assertNull(caseSensitiveAttr.findOption("NORMAL"));
        assertNull(caseSensitiveAttr.findOption("invalid"));
    }

    @Test
    public void testFindOption_CaseInsensitive() {
        // 测试大小写不敏感模式下查找选项的功能
        // 验证在大小写不敏感模式下，不同大小写形式的选项都能被正确找到
        assertEquals("normal", attr.findOption("normal"));
        assertEquals("normal", attr.findOption("Normal"));
        assertEquals("normal", attr.findOption("NORMAL"));
        assertNull(attr.findOption("invalid"));
    }

    @Test
    public void testFindOption_NullOption() {
        // 测试查找null选项的功能
        // 验证当传入null时，findOption方法是否正确返回null
        assertNull(attr.findOption(null));
    }

    @Test
    public void testIsValidOption() {
        // 测试验证选项有效性的功能
        // 验证isValidOption方法能正确判断选项是否在有效列表中（默认不区分大小写）
        assertTrue(attr.isValidOption("normal"));
        assertTrue(attr.isValidOption("NORMAL")); // 不区分大小写
        assertFalse(attr.isValidOption("invalid"));
        assertFalse(attr.isValidOption(null));
    }

    @Test
    public void testNormalizeOption() {
        // 测试选项值规范化的功能
        // 验证normalizeOption方法能返回规范化的选项值（不区分大小写时返回标准形式）
        assertEquals("normal", attr.normalizeOption("normal"));
        assertEquals("normal", attr.normalizeOption("Normal"));
        assertEquals("normal", attr.normalizeOption("NORMAL"));
        assertNull(attr.normalizeOption("invalid"));
    }

    @Test
    public void testGetOptionDict() {
        // 测试获取选项字典的功能
        // 验证getOptionDict方法能正确返回选项值到国际化显示名称的映射
        Map<String, String> optionDict = attr.getOptionDict();
        assertEquals(3, optionDict.size());
        assertNotNull(optionDict.get("normal"));
        assertNotNull(optionDict.get("warning"));
        assertNotNull(optionDict.get("error"));

        // 验证缓存机制 - 检查内容相同而不是对象相同
        Map<String, String> optionDict2 = attr.getOptionDict();
        assertEquals(optionDict, optionDict2); // 内容应该相同
        assertEquals(optionDict.size(), optionDict2.size());
    }

    @Test
    public void testGetOptionI18nName() {
        // 测试获取单个选项国际化名称的功能
        // 验证getOptionI18nName方法能正确返回指定选项的国际化路径
        String optionName = attr.getOptionI18nName("normal");
        assertNotNull(optionName);
        assertEquals("state.test_i18n_text_attr.status_options.normal", optionName);
    }

    @Test
    public void testGetOptionByDisplayName() {
        // 测试根据显示名称查找选项值的功能
        // 验证能够通过国际化显示名称反向查找到对应的选项值
        Map<String, String> optionDict = attr.getOptionDict();
        String displayName = optionDict.get("normal");

        assertEquals("normal", attr.getOptionByDisplayName(displayName));
        assertNull(attr.getOptionByDisplayName("invalid"));
        assertNull(attr.getOptionByDisplayName(null));
    }

    @Test
    public void testGetSortedOptionDisplayNames() {
        // 测试获取排序后的选项显示名称列表功能
        // 验证返回的显示名称列表是按字母顺序正确排序的
        List<String> displayNames = attr.getSortedOptionDisplayNames();
        assertEquals(3, displayNames.size());
        // 验证排序
        for (int i = 1; i < displayNames.size(); i++) {
            assertTrue(displayNames.get(i-1).compareTo(displayNames.get(i)) <= 0);
        }
    }

    @Test
    public void testGetI18nOptionPathPrefix() {
        // 测试获取选项国际化路径前缀的功能
        // 验证getI18nOptionPathPrefix方法返回正确的国际化路径前缀
        I18nKeyPath optionPrefix = attr.getI18nOptionPathPrefix();
        assertEquals("state.test_i18n_text_attr.status_options", optionPrefix.getFullPath());
    }

    @Test
    public void testGetValueDefinition() {
        // 测试获取值验证定义的功能
        // 验证getValueDefinition方法返回的验证器能正确验证选项值的有效性
        ConfigDefinition valueDef = attr.getValueDefinition();
        assertNotNull(valueDef);

        // 验证验证器能够正常工作
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("value", "normal");
        assertTrue(valueDef.validateConfig(config));

        config.put("value", "invalid");
        assertFalse(valueDef.validateConfig(config));
    }

    @Test
    public void testConstructorWithCaseSensitive() {
        // 测试构造函数设置大小写敏感参数的功能
        // 验证通过反射设置caseSensitive字段后，isCaseSensitive方法返回正确值
        TestI18nTextAttribute caseSensitiveAttr = new TestI18nTextAttribute("test", mockAttrClass, true, options, mockCallback);
        try {
            TestTools.setPrivateField(caseSensitiveAttr, "caseSensitive", true);
        } catch (Exception e) {
            fail("Failed to set caseSensitive field: " + e.getMessage());
        }

        assertTrue(caseSensitiveAttr.isCaseSensitive());
    }

    @Test
    public void testDeprecatedConstructorCompatibility() {
        // 测试已弃用构造函数的向后兼容性
        // 验证使用已弃用的带displayName参数的构造函数时，所有功能仍能正常工作
        TestI18nTextAttribute deprecatedAttr = new TestI18nTextAttribute("deprecatedStatus", "设备状态", mockAttrClass, true, options, mockCallback);
        try {
            TestTools.setPrivateField(deprecatedAttr, "device", mockDevice);
        } catch (Exception e) {
            fail("Failed to set device field: " + e.getMessage());
        }

        // 验证基本功能仍然正常工作
        assertEquals("deprecatedStatus", deprecatedAttr.getAttributeID());
        assertEquals(mockAttrClass, deprecatedAttr.attrClass);
        assertEquals(options, deprecatedAttr.getOptions());
        assertTrue(deprecatedAttr.canValueChange());
        assertFalse(deprecatedAttr.canUnitChange());

        // 验证I18n路径正确生成
        assertEquals("state.test_i18n_text_attr.deprecatedstatus", deprecatedAttr.getI18nPrefixPath().withLastSegment("deprecatedstatus").getI18nPath());

        // 验证displayName仍然可访问（已弃用）
        assertEquals("设备状态", deprecatedAttr.getDisplayName());

        // 验证基本操作仍然正常工作
        assertTrue(deprecatedAttr.updateValue("normal"));
        assertEquals("normal", deprecatedAttr.getValue());
    }

    @Test
    public void testI18nTextOptionI18nPathsWithDevice() throws Exception {
        // 测试绑定设备时的国际化路径生成功能
        // 验证当属性绑定到设备时，国际化路径使用设备分组前缀
        when(mockDevice.getI18nPrefix()).thenReturn(new I18nKeyPath("devices.test_device.", ""));

        TestI18nTextAttribute attrWithDevice = new TestI18nTextAttribute("testStatus", mockAttrClass, true, options, mockCallback);
        TestTools.setPrivateField(attrWithDevice, "device", mockDevice);

        // 验证设备前缀正确应用
        I18nKeyPath devicePrefix = mockDevice.getI18nPrefix();
        assertEquals("devices.test_device.", devicePrefix.getFullPath());

        // 验证options的i18n选项路径前缀
        I18nKeyPath optionPrefix = attrWithDevice.getI18nOptionPathPrefix();
        assertEquals("devices.test_device.teststatus_options", optionPrefix.getFullPath());

        // 验证option键路径生成
        String normalOptionKey = optionPrefix.getFullPath() + ".normal";
        assertEquals("devices.test_device.teststatus_options.normal", normalOptionKey);

        // 验证option名称解析能够正常工作（不依赖具体值）
        String normalOptionName = attrWithDevice.getOptionI18nName("normal");
        assertNotNull(normalOptionName);
        assertTrue(normalOptionName.length() > 0);

        // 验证getOptionDict正确生成
        java.util.Map<String, String> optionDict = attrWithDevice.getOptionDict();
        assertNotNull(optionDict.get("normal"));
        assertNotNull(optionDict.get("warning"));
        assertNotNull(optionDict.get("error"));
    }

    @Test
    public void testI18nTextOptionI18nPathsWithoutDevice() {
        // 测试未绑定设备时的国际化路径生成功能
        // 验证当属性未绑定到设备时，国际化路径使用默认的前缀结构
        TestI18nTextAttribute attrWithoutDevice = new TestI18nTextAttribute("testStatus", mockAttrClass, true, options, mockCallback);
        // 不设置device

        // 验证默认i18n前缀
        I18nKeyPath defaultPrefix = attrWithoutDevice.getI18nPrefixPath();
        assertEquals("state.test_i18n_text_attr.", defaultPrefix.getFullPath());

        // 验证options的i18n选项路径前缀
        I18nKeyPath optionPrefix = attrWithoutDevice.getI18nOptionPathPrefix();
        assertEquals("state.test_i18n_text_attr.teststatus_options", optionPrefix.getFullPath());

        // 验证option键路径生成
        String warningOptionKey = optionPrefix.getFullPath() + ".warning";
        assertEquals("state.test_i18n_text_attr.teststatus_options.warning", warningOptionKey);

        // 验证option名称解析能够正常工作（不依赖具体值）
        String warningOptionName = attrWithoutDevice.getOptionI18nName("warning");
        assertNotNull(warningOptionName);
        assertTrue(warningOptionName.length() > 0);

        // 验证getOptionDict仍然正常工作
        java.util.Map<String, String> optionDict = attrWithoutDevice.getOptionDict();
        assertNotNull(optionDict.get("normal"));
        assertNotNull(optionDict.get("warning"));
        assertNotNull(optionDict.get("error"));
    }

    @Test
    public void testI18nTextOptionI18nPathWithDeviceGrouping() throws Exception {
        // 测试设备分组路径的国际化功能
        // 验证当设备具有特定分组前缀时，国际化路径正确包含分组信息
        when(mockDevice.getI18nPrefix()).thenReturn(new I18nKeyPath("devices.weather_sensor.", ""));

        TestI18nTextAttribute attrWithGrouping = new TestI18nTextAttribute("device_status", mockAttrClass, true, options, mockCallback);
        TestTools.setPrivateField(attrWithGrouping, "device", mockDevice);

        // 验证设备分组路径正确生成
        I18nKeyPath devicePrefix = attrWithGrouping.getI18nPrefixPath();
        assertEquals("devices.weather_sensor.", devicePrefix.getFullPath());

        // 验证options的i18n选项路径前缀
        I18nKeyPath optionPrefix = attrWithGrouping.getI18nOptionPathPrefix();
        assertEquals("devices.weather_sensor.device_status_options", optionPrefix.getFullPath());

        // 验证option键路径生成
        String errorOptionKey = optionPrefix.getFullPath() + ".error";
        assertEquals("devices.weather_sensor.device_status_options.error", errorOptionKey);

        // 验证当前选项的i18n名称解析
        attrWithGrouping.value = "error";
        String displayValue = attrWithGrouping.getDisplayValue(null);
        assertNotNull(displayValue);
        assertTrue(displayValue.length() > 0);
    }

    @Test
    public void testEmptyOptionsList() {
        // 测试空选项列表的边界情况
        // 验证当选项列表为空时，相关方法仍能正常工作且不会出错
        List<String> emptyOptions = Arrays.asList();
        TestI18nTextAttribute emptyAttr = new TestI18nTextAttribute("empty", mockAttrClass, true, emptyOptions, mockCallback);

        assertEquals(0, emptyAttr.getOptions().size());
        assertEquals(0, emptyAttr.getOptionDict().size());
        assertFalse(emptyAttr.isValidOption("any"));
    }

    @Test
    public void testSingleOption() {
        // 测试单个选项的边界情况
        // 验证当选项列表只包含一个选项时，所有功能仍能正常工作
        List<String> singleOption = Arrays.asList("single");
        TestI18nTextAttribute singleAttr = new TestI18nTextAttribute("single", mockAttrClass, true, singleOption, mockCallback);

        assertEquals(1, singleAttr.getOptions().size());
        assertTrue(singleAttr.isValidOption("single"));
        assertFalse(singleAttr.isValidOption("other"));
    }
}
