package com.ecat.core.State;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.I18n.I18nKeyPath;

/**
 * 测试 StringCommandAttribute 类的功能
 * 
 * @author coffee
 */
public class StringCommandAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;
    @Mock
    private DeviceBase mockDevice;

    private TestStringCommandAttribute attr;
    private List<String> commands;

    // 简单实现一个字符串命令的 StringCommandAttribute
    static class TestStringCommandAttribute extends StringCommandAttribute {
        private boolean sendSuccess = true;

        /**
         * 支持I18n的构造函数
         */
        public TestStringCommandAttribute(String attributeID, AttributeClass attrClass, List<String> commands) {
            super(attributeID, attrClass, commands, null);
        }

        /**
         * @deprecated Use the constructor without displayName instead.
         */
        @Deprecated
        public TestStringCommandAttribute(String attributeID, String displayName, AttributeClass attrClass, List<String> commands) {
            super(attributeID, displayName, attrClass, commands, null);
        }
        @Override
        protected CompletableFuture<Boolean> sendCommandImpl(String cmd) {
            return CompletableFuture.completedFuture(sendSuccess);
        }
        public void setSendSuccess(boolean success) {
            this.sendSuccess = success;
        }

        @Override
        public I18nKeyPath getI18nPrefixPath() {
            // 支持设备分组路径，如果设备有i18n前缀则使用设备的
            if(device != null && device.getI18nPrefix() != null){
                return device.getI18nPrefix();
            }else{
                // 回退到原有路径结构
                return new I18nKeyPath("state.test_string_command_attr.", "");
            }
        }

        @Override
        protected String parseCommandValue(String commandText) {
            // 查找匹配的命令
            if (commands != null) {
                for (String command : commands) {
                    if (command.equalsIgnoreCase(commandText)) {
                        return command;
                    }
                }
            }
            return null;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("命令属性");
        commands = Arrays.asList("ON", "OFF", "RESET");
        attr = new TestStringCommandAttribute("cmd1", mockAttrClass, commands);
        when(mockDevice.getId()).thenReturn("mockDeviceId");
        attr.setDevice(mockDevice);
    }

    @Test
    public void testConstructorAndGetters() {
        assertEquals("cmd1", attr.getAttributeID());
        // 测试时直接验证I18n路径，不依赖具体的资源文件
        assertEquals("state.test_string_command_attr.cmd1", attr.getI18nPrefixPath().withLastSegment("cmd1").getI18nPath());
        assertEquals(mockAttrClass, attr.attrClass);
        assertEquals(commands, attr.getCommands());
        assertTrue(attr.canValueChange());
        assertFalse(attr.canUnitChange());
    }

    @Test
    public void testSetCommands() {
        assertFalse(attr.setCommands(Collections.emptyList()));
        assertTrue(attr.setCommands(Arrays.asList("A", "B")));
        assertEquals(Arrays.asList("A", "B"), attr.getCommands());
    }

    @Test
    public void testGetLastCommand() {
        attr.value = "ON";
        assertEquals("ON", attr.getLastCommand());
    }

    @Test
    public void testSendCommandSuccess() throws Exception {
        attr.setSendSuccess(true);
        CompletableFuture<Boolean> future = attr.sendCommand("ON");
        assertTrue(future.get());
        assertEquals("ON", attr.getValue());
    }

    @Test
    public void testSendCommandFail() throws Exception {
        attr.setSendSuccess(false);
        CompletableFuture<Boolean> future = attr.sendCommand("ON");
        assertFalse(future.get());
        // value 不变
    }

    @Test
    public void testSendCommandNotAllowed() throws Exception {
        CompletableFuture<Boolean> future = attr.sendCommand("INVALID");
        assertFalse(future.get());
    }

    @Test
    public void testSendCommandNotChangeable() throws Exception {
        TestStringCommandAttribute attr2 = new TestStringCommandAttribute("cmd2", mockAttrClass, commands) {
            @Override
            public boolean canValueChange() { return false; }
        };
        attr2.valueChangeable = false; // 强制设置为不可变
        attr2.setDevice(mockDevice);
        CompletableFuture<Boolean> future = attr2.sendCommand("ON");
        // valueChangeable==false时允许返回false，只要不抛异常即可
        assertNotNull(future);
        
        assertFalse(future.get());
    }

    @Test
    public void testSetDisplayValueImp() throws Exception {
        attr.setSendSuccess(true);
        CompletableFuture<Boolean> future = attr.setDisplayValueImp("RESET", attr.getDisplayUnit());
        assertTrue(future.get());
        assertEquals("RESET", attr.getValue());
    }

    @Test
    public void testConvertFromUnitImp() {
        assertEquals("ON", attr.convertFromUnitImp("ON", null));
    }

    @Test
    public void testGetDisplayValue() {
        // 测试getDisplayValue方法，现在使用I18n系统
        attr.value = "ON";
        String onText = attr.getDisplayValue(null);
        assertNotNull(onText);
        assertEquals("state.test_string_command_attr.cmd1_commands.on", onText);

        attr.value = "OFF";
        String offText = attr.getDisplayValue(null);
        assertNotNull(offText);
        assertEquals("state.test_string_command_attr.cmd1_commands.off", offText);
    }

    @Test
    public void testGetI18nValue() {
        attr.value = "ON";
        String i18nValue = attr.getI18nValue(null);
        assertEquals("on", i18nValue);
    }

    @Test
    public void testAddDependencyAttribute() {
        AttributeBase<?> dep = mock(AttributeBase.class);
        attr.addDependencyAttribute(dep);
        assertTrue(attr.getDependencyAttributes().contains(dep));
        // 不重复添加
        attr.addDependencyAttribute(dep);
        assertEquals(1, attr.getDependencyAttributes().size());
    }

    @Test
    public void testDeprecatedConstructorCompatibility() {
        // 测试已弃用的构造函数兼容性
        TestStringCommandAttribute deprecatedAttr = new TestStringCommandAttribute("deprecatedCmd", "命令属性", mockAttrClass, commands);
        deprecatedAttr.setDevice(mockDevice);

        // 验证基本功能仍然正常工作
        assertEquals("deprecatedCmd", deprecatedAttr.getAttributeID());
        assertEquals(mockAttrClass, deprecatedAttr.attrClass);
        assertEquals(commands, deprecatedAttr.getCommands());
        assertTrue(deprecatedAttr.canValueChange());
        assertFalse(deprecatedAttr.canUnitChange());

        // 验证I18n路径正确生成
        assertEquals("state.test_string_command_attr.deprecatedcmd", deprecatedAttr.getI18nPrefixPath().withLastSegment("deprecatedcmd").getI18nPath());

        // 验证displayName仍然可访问（已弃用）
        assertEquals("命令属性", deprecatedAttr.getDisplayName());

        // 验证基本操作仍然正常工作
        deprecatedAttr.setSendSuccess(true);
        try {
            assertTrue(deprecatedAttr.sendCommand("ON").get());
            assertEquals("ON", deprecatedAttr.getValue());
        } catch (Exception e) {
            fail("Command execution should not fail: " + e.getMessage());
        }
    }

    @Test
    public void testStringCommandI18nPathsWithDevice() {
        // 测试commands的i18n路径（with device）
        // 当属性绑定到设备时，应该使用设备分组路径
        when(mockDevice.getI18nPrefix()).thenReturn(new I18nKeyPath("devices.test_device.", ""));

        TestStringCommandAttribute attrWithDevice = new TestStringCommandAttribute("testCmd", mockAttrClass, commands);
        attrWithDevice.setDevice(mockDevice);

        // 验证设备前缀正确应用
        I18nKeyPath devicePrefix = mockDevice.getI18nPrefix();
        assertEquals("devices.test_device.", devicePrefix.getFullPath());

        // 验证commands的i18n命令路径前缀
        I18nKeyPath commandPrefix = attrWithDevice.getI18nCommandPathPrefix();
        assertEquals("devices.test_device.testcmd_commands", commandPrefix.getFullPath());

        // 验证command键路径生成
        String onCommandKey = commandPrefix.getFullPath() + ".ON";
        assertEquals("devices.test_device.testcmd_commands.ON", onCommandKey);

        // 验证commands的i18n名称解析能够正常工作（不依赖具体值）
        String onCommandName = attrWithDevice.getCommandI18nName("ON");
        assertNotNull(onCommandName);
        assertTrue(onCommandName.length() > 0);

        // 验证getOptionDict正确生成（StringCommandAttribute继承此方法）
        java.util.Map<String, String> optionDict = attrWithDevice.getOptionDict();
        assertNotNull(optionDict.get("ON"));
        assertNotNull(optionDict.get("OFF"));
        assertNotNull(optionDict.get("RESET"));
    }

    @Test
    public void testStringCommandI18nPathsWithoutDevice() {
        // 测试commands的i18n路径（without device）
        // 当属性未绑定到设备时，应该使用默认路径
        TestStringCommandAttribute attrWithoutDevice = new TestStringCommandAttribute("testCmd", mockAttrClass, commands);
        // 不设置device

        // 验证默认i18n前缀
        I18nKeyPath defaultPrefix = attrWithoutDevice.getI18nPrefixPath();
        assertEquals("state.test_string_command_attr.", defaultPrefix.getFullPath());

        // 验证commands的i18n命令路径前缀
        I18nKeyPath commandPrefix = attrWithoutDevice.getI18nCommandPathPrefix();
        assertEquals("state.test_string_command_attr.testcmd_commands", commandPrefix.getFullPath());

        // 验证command键路径生成
        String offCommandKey = commandPrefix.getFullPath() + ".OFF";
        assertEquals("state.test_string_command_attr.testcmd_commands.OFF", offCommandKey);

        // 验证commands的i18n名称解析能够正常工作（不依赖具体值）
        String offCommandName = attrWithoutDevice.getCommandI18nName("OFF");
        assertNotNull(offCommandName);
        assertTrue(offCommandName.length() > 0);
    }

    @Test
    public void testStringCommandI18nPathWithDeviceGrouping() {
        // 测试commands的设备分组路径
        when(mockDevice.getI18nPrefix()).thenReturn(new I18nKeyPath("devices.weather_sensor.", ""));

        TestStringCommandAttribute attrWithGrouping = new TestStringCommandAttribute("control_cmd", mockAttrClass, commands);
        attrWithGrouping.setDevice(mockDevice);

        // 验证设备分组路径正确生成
        I18nKeyPath devicePrefix = attrWithGrouping.getI18nPrefixPath();
        assertEquals("devices.weather_sensor.", devicePrefix.getFullPath());

        // 验证commands的i18n命令路径前缀
        I18nKeyPath commandPrefix = attrWithGrouping.getI18nCommandPathPrefix();
        assertEquals("devices.weather_sensor.control_cmd_commands", commandPrefix.getFullPath());

        // 验证command键路径生成
        String resetCommandKey = commandPrefix.getFullPath() + ".RESET";
        assertEquals("devices.weather_sensor.control_cmd_commands.RESET", resetCommandKey);

        // 验证command名称解析能够正常工作（不依赖具体值）
        String resetCommandName = attrWithGrouping.getCommandI18nName("RESET");
        assertNotNull(resetCommandName);
        assertTrue(resetCommandName.length() > 0);
    }
}
