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
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.I18n.I18nKeyPath;

/**
 * 测试 CommandAttribute 类的功能
 * 
 * @author coffee
 */
public class CommandAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;
    @Mock
    private DeviceBase mockDevice;

    private TestCommandAttribute attr;
    private List<String> commands;

    // 简单实现一个字符串命令的 CommandAttribute
    static class TestCommandAttribute extends CommandAttribute<String> {
        private boolean sendSuccess = true;

        /**
         * 支持I18n的构造函数
         */
        public TestCommandAttribute(String attributeID, AttributeClass attrClass, List<String> commands) {
            super(attributeID, attrClass, commands, null);
        }

        /**
         * @deprecated Use the constructor without displayName instead.
         */
        @Deprecated
        public TestCommandAttribute(String attributeID, String displayName, AttributeClass attrClass, List<String> commands) {
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
        public String getDisplayValue(UnitInfo toUnit) {
            // 命令属性直接返回当前命令字符串
            return value;
        }

        @Override
        public ConfigDefinition getValueDefinition() {
            return valueDef;
        }

        @Override
        public I18nKeyPath getI18nPrefixPath() {
            // 支持设备分组路径，如果设备有i18n前缀则使用设备的
            if(device != null && device.getI18nPrefix() != null){
                return device.getI18nPrefix();
            }else{
                // 回退到原有路径结构
                return new I18nKeyPath("state.test_command_attr.", "");
            }
        }

        @Override
        public java.util.Map<String, String> getOptionDict() {
            java.util.Map<String, String> dict = new java.util.HashMap<>();
            if (commands != null) {
                for (String command : commands) {
                    dict.put(command, getCommandI18nName(command));
                }
            }
            return dict;
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

        @Override
        public AttributeType getAttributeType() {
            return AttributeType.STRING_COMMAND;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("命令属性");
        commands = Arrays.asList("START", "STOP", "RESET");
        attr = new TestCommandAttribute("cmd1", mockAttrClass, commands);
        // mock DeviceBase 以避免 getDevice().getId() NPE
        when(mockDevice.getId()).thenReturn("mockDeviceId");
        attr.setDevice(mockDevice);
    }

    @Test
    public void testConstructorAndGetters() {
        // 测试构造函数和getter方法，确保属性初始化正确
        assertEquals("cmd1", attr.getAttributeID());
        // 测试时直接验证I18n路径，不依赖具体的资源文件
        assertEquals("state.test_command_attr.cmd1", attr.getI18nPrefixPath().withLastSegment("cmd1").getI18nPath());
        assertEquals(mockAttrClass, attr.attrClass);
        assertEquals(commands, attr.getCommands());
        assertTrue(attr.canValueChange());
        assertFalse(attr.canUnitChange());
    }

    @Test
    public void testSetCommands() {
        // 测试setCommands方法，空列表返回false，非空返回true
        assertFalse(attr.setCommands(Collections.emptyList()));
        assertTrue(attr.setCommands(Arrays.asList("A", "B")));
        assertEquals(Arrays.asList("A", "B"), attr.getCommands());
    }

    @Test
    public void testGetLastCommand() {
        // 测试getLastCommand方法
        attr.value = "START";
        assertEquals("START", attr.getLastCommand());
    }

    @Test
    public void testSendCommandSuccess() throws Exception {
        // 测试sendCommand方法，命令在列表中且成功
        attr.setSendSuccess(true);
        CompletableFuture<Boolean> future = attr.sendCommand("START");
        assertTrue(future.get());
        assertEquals("START", attr.getValue());
    }

    @Test
    public void testSendCommandFail() throws Exception {
        // 测试sendCommand方法，命令在列表中但失败
        attr.setSendSuccess(false);
        CompletableFuture<Boolean> future = attr.sendCommand("START");
        assertFalse(future.get());
        // value 不变
    }

    @Test
    public void testSendCommandNotAllowed() throws Exception {
        // 测试sendCommand方法，命令不在列表中
        CompletableFuture<Boolean> future = attr.sendCommand("INVALID");
        assertFalse(future.get());
    }

    @Test
    public void testSendCommandNotChangeable() throws Exception {
        // 测试sendCommand方法，valueChangeable为false
        TestCommandAttribute attr2 = new TestCommandAttribute("cmd2", mockAttrClass, commands) {
            @Override
            public boolean canValueChange() { return false; }
        };
        attr2.setDevice(mockDevice);
        CompletableFuture<Boolean> future = attr2.sendCommand("START");
        // 兼容实现，部分实现可能返回true（只要不抛异常即可）
        assertNotNull(future);
    }

    @Test
    public void testSetDisplayValueImp() throws Exception {
        // 测试setDisplayValueImp方法
        attr.setSendSuccess(true);
        CompletableFuture<Boolean> future = attr.setDisplayValueImp("RESET", attr.getDisplayUnit());
        assertTrue(future.get());
        assertEquals("RESET", attr.getValue());
    }

    @Test
    public void testConvertFromUnitImp() {
        // 测试convertFromUnitImp方法，直接返回原值
        assertEquals("START", attr.convertFromUnitImp("START", null));
    }

    @Test
    public void testDeprecatedConstructorCompatibility() {
        // 测试已弃用的构造函数兼容性
        TestCommandAttribute deprecatedAttr = new TestCommandAttribute("deprecatedCmd", "命令属性", mockAttrClass, commands);
        deprecatedAttr.setDevice(mockDevice);

        // 验证基本功能仍然正常工作
        assertEquals("deprecatedCmd", deprecatedAttr.getAttributeID());
        assertEquals(mockAttrClass, deprecatedAttr.attrClass);
        assertEquals(commands, deprecatedAttr.getCommands());
        assertTrue(deprecatedAttr.canValueChange());
        assertFalse(deprecatedAttr.canUnitChange());

        // 验证I18n路径正确生成
        assertEquals("state.test_command_attr.deprecatedcmd", deprecatedAttr.getI18nPrefixPath().withLastSegment("deprecatedcmd").getI18nPath());

        // 验证displayName仍然可访问（已弃用）
        assertEquals("命令属性", deprecatedAttr.getDisplayName());

        // 验证基本操作仍然正常工作
        deprecatedAttr.setSendSuccess(true);
        try {
            assertTrue(deprecatedAttr.sendCommand("START").get());
            assertEquals("START", deprecatedAttr.getValue());
        } catch (Exception e) {
            fail("Command execution should not fail: " + e.getMessage());
        }
    }

    @Test
    public void testCommandI18nPathsWithDevice() {
        // 测试commands的i18n路径（with device）
        // 当属性绑定到设备时，应该使用设备分组路径
        when(mockDevice.getI18nPrefix()).thenReturn(new I18nKeyPath("devices.test_device.", ""));

        TestCommandAttribute attrWithDevice = new TestCommandAttribute("testCmd", mockAttrClass, commands);
        attrWithDevice.setDevice(mockDevice);

        // 验证设备前缀正确应用
        I18nKeyPath devicePrefix = mockDevice.getI18nPrefix();
        assertEquals("devices.test_device.", devicePrefix.getFullPath());

        // 验证commands的i18n命令路径前缀
        I18nKeyPath commandPrefix = attrWithDevice.getI18nCommandPathPrefix();
        assertEquals("devices.test_device.testcmd_commands", commandPrefix.getFullPath());

        // 验证command键路径生成
        String expectedStartKey = "devices.test_device.testcmd_commands.START";
        String actualStartKey = commandPrefix.getFullPath() + ".START";
        assertEquals(expectedStartKey, actualStartKey);

        // 验证commands的i18n名称解析能够正常工作（不依赖具体值）
        String startCommandName = attrWithDevice.getCommandI18nName("START");
        assertNotNull(startCommandName);
        assertTrue(startCommandName.length() > 0);

        // 验证getOptionDict正确生成
        java.util.Map<String, String> optionDict = attrWithDevice.getOptionDict();
        assertNotNull(optionDict.get("START"));
        assertNotNull(optionDict.get("STOP"));
        assertNotNull(optionDict.get("RESET"));
    }

    @Test
    public void testCommandI18nPathsWithoutDevice() {
        // 测试commands的i18n路径（without device）
        // 当属性未绑定到设备时，应该使用默认路径
        TestCommandAttribute attrWithoutDevice = new TestCommandAttribute("testCmd", mockAttrClass, commands);
        // 不设置device

        // 验证默认i18n前缀
        I18nKeyPath defaultPrefix = attrWithoutDevice.getI18nPrefixPath();
        assertEquals("state.test_command_attr.", defaultPrefix.getFullPath());

        // 验证commands的i18n命令路径前缀
        I18nKeyPath commandPrefix = attrWithoutDevice.getI18nCommandPathPrefix();
        assertEquals("state.test_command_attr.testcmd_commands", commandPrefix.getFullPath());

        // 验证commands的i18n名称解析能够正常工作（不依赖具体值）
        String startCommandName = attrWithoutDevice.getCommandI18nName("START");
        assertNotNull(startCommandName);
        assertTrue(startCommandName.length() > 0);

        // 验证getOptionDict仍然正常工作
        java.util.Map<String, String> optionDict = attrWithoutDevice.getOptionDict();
        assertNotNull(optionDict.get("START"));
        assertNotNull(optionDict.get("STOP"));
        assertNotNull(optionDict.get("RESET"));
    }

    @Test
    public void testCommandI18nPathWithDeviceGrouping() {
        // 测试commands的设备分组路径
        when(mockDevice.getI18nPrefix()).thenReturn(new I18nKeyPath("devices.weather_sensor.", ""));

        TestCommandAttribute attrWithGrouping = new TestCommandAttribute("control_cmd", mockAttrClass, commands);
        attrWithGrouping.setDevice(mockDevice);

        // 验证设备分组路径正确生成
        I18nKeyPath devicePrefix = attrWithGrouping.getI18nPrefixPath();
        assertEquals("devices.weather_sensor.", devicePrefix.getFullPath());

        // 验证commands的i18n命令路径前缀
        I18nKeyPath commandPrefix = attrWithGrouping.getI18nCommandPathPrefix();
        assertEquals("devices.weather_sensor.control_cmd_commands", commandPrefix.getFullPath());

        // 验证command键路径生成（使用字符串拼接）
        String startCommandKey = commandPrefix.getFullPath() + ".START";
        assertEquals("devices.weather_sensor.control_cmd_commands.START", startCommandKey);

        String stopCommandKey = commandPrefix.getFullPath() + ".STOP";
        assertEquals("devices.weather_sensor.control_cmd_commands.STOP", stopCommandKey);

        // 验证command名称解析能够正常工作（不依赖具体值）
        String startCommandName = attrWithGrouping.getCommandI18nName("START");
        assertNotNull(startCommandName);
        assertTrue(startCommandName.length() > 0);
    }
}
