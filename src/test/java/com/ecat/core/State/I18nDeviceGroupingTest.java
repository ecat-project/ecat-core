package com.ecat.core.State;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.I18n.I18nKeyPath;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 测试国际化设备分组功能
 * 验证新的设备分组路径结构和向后兼容性
 */
public class I18nDeviceGroupingTest {

    // 模拟设备类
    private static class TestDevice extends DeviceBase {
        public TestDevice(Map<String, Object> config) {
            super(config);
        }

        @Override
        public void init() {
            // 空实现
        }

        @Override
        public void start() {
            // 空实现
        }

        @Override
        public void stop() {
            // 空实现
        }

        @Override
        public void release() {
            // 空实现
        }
    }

    private TestDevice device;
    private BinaryAttribute binaryAttr;
    private CommandAttribute<String> commandAttr;
    private SelectAttribute<String> selectAttr;

    @Before
    public void setUp() throws Exception {
        // 创建测试设备
        Map<String, Object> deviceConfig = new HashMap<>();
        deviceConfig.put("id", "test-device-001");
        deviceConfig.put("name", "Test Device");
        device = new TestDevice(deviceConfig);

        // 创建测试属性
        binaryAttr = new BinaryAttribute("binary_alarm", AttributeClass.GENERAL_ALARM, true);
        commandAttr = new StringCommandAttribute("device_command", AttributeClass.COMMAND,
            Arrays.asList("start", "stop", "reset"), null) {
            @Override
            protected CompletableFuture<Boolean> sendCommandImpl(String cmd) {
                return CompletableFuture.completedFuture(true);
            }
        };
        selectAttr = new StringSelectAttribute("start_option", AttributeClass.MODE, null, null, true,
            Arrays.asList("full", "partial"), null);

        // 绑定属性到设备
        device.setAttribute(binaryAttr);
        device.setAttribute(commandAttr);
        device.setAttribute(selectAttr);
    }

    @Test
    public void testDeviceTypeName() {
        assertEquals("test_device", device.getTypeName());
    }

    @Test
    public void testDeviceI18nPrefix() {
        I18nKeyPath prefix = device.getI18nPrefix();
        assertNotNull(prefix);
        assertEquals("devices.test_device.", prefix.getPathPrefix());
        assertEquals("", prefix.getLastSegment());
        assertEquals("devices.test_device.", prefix.getFullPath());
    }

    @Test
    public void testBinaryAttributeNewPath() {
        I18nKeyPath path = binaryAttr.getI18nDispNamePath();
        assertNotNull(path);
        assertEquals("devices.test_device.", path.getPathPrefix());
        assertEquals("binary_alarm", path.getLastSegment());
        assertEquals("devices.test_device.binary_alarm", path.getFullPath());
    }

    @Test
    public void testBinaryAttributeOptionPath() {
        I18nKeyPath optionPath = binaryAttr.getI18nOptionPathPrefix();
        assertNotNull(optionPath);
        assertEquals("devices.test_device.", optionPath.getPathPrefix());
        assertEquals("binary_alarm_options", optionPath.getLastSegment());
        assertEquals("devices.test_device.binary_alarm_options", optionPath.getFullPath());
    }

    @Test
    public void testCommandAttributeNewPath() {
        I18nKeyPath path = commandAttr.getI18nDispNamePath();
        assertNotNull(path);
        assertEquals("devices.test_device.", path.getPathPrefix());
        assertEquals("device_command", path.getLastSegment());
        assertEquals("devices.test_device.device_command", path.getFullPath());
    }

    @Test
    public void testCommandAttributeCommandPath() {
        I18nKeyPath commandPath = commandAttr.getI18nCommandPathPrefix();
        assertNotNull(commandPath);
        assertEquals("devices.test_device.", commandPath.getPathPrefix());
        assertEquals("device_command_commands", commandPath.getLastSegment());
        assertEquals("devices.test_device.device_command_commands", commandPath.getFullPath());
    }

    @Test
    public void testSelectAttributeNewPath() {
        I18nKeyPath path = selectAttr.getI18nDispNamePath();
        assertNotNull(path);
        assertEquals("devices.test_device.", path.getPathPrefix());
        assertEquals("start_option", path.getLastSegment());
        assertEquals("devices.test_device.start_option", path.getFullPath());
    }

    @Test
    public void testSelectAttributeOptionPath() {
        I18nKeyPath optionPath = selectAttr.getI18nOptionPathPrefix();
        assertNotNull(optionPath);
        assertEquals("devices.test_device.", optionPath.getPathPrefix());
        assertEquals("start_option_options", optionPath.getLastSegment());
        assertEquals("devices.test_device.start_option_options", optionPath.getFullPath());
    }

    @Test
    public void testBackwardCompatibility() {
        // 测试旧的路径仍然可用
        I18nKeyPath binaryOldPath = binaryAttr.getI18nPrefixPath();
        assertEquals("state.binary_attr.", binaryOldPath.getPathPrefix());
        assertEquals("", binaryOldPath.getLastSegment());

        I18nKeyPath commandOldPath = commandAttr.getI18nPrefixPath();
        assertEquals("state.string_command_attr.", commandOldPath.getPathPrefix());
        assertEquals("", commandOldPath.getLastSegment());

        I18nKeyPath selectOldPath = selectAttr.getI18nPrefixPath();
        assertEquals("state.string_select_attr.", selectOldPath.getPathPrefix());
        assertEquals("", selectOldPath.getLastSegment());
    }

    @Test
    public void testUnboundAttributeFallback() {
        // 测试未绑定设备的属性回退到旧路径
        BinaryAttribute unboundAttr = new BinaryAttribute("test_attr", AttributeClass.GENERAL_ALARM, false);
        I18nKeyPath path = unboundAttr.getI18nDispNamePath();

        // 应该回退到旧的路径结构
        assertEquals("state.binary_attr.", path.getPathPrefix());
        assertEquals("test_attr", path.getLastSegment());
    }

    @Test
    public void testOptionInternationalization() {
        // 测试选项国际化名称获取
        String onText = binaryAttr.getOnDisplayText();
        String offText = binaryAttr.getOffDisplayText();

        // 这些应该调用新的国际化路径
        assertNotNull(onText);
        assertNotNull(offText);
    }

    @Test
    public void testCommandInternationalization() {
        // 测试命令国际化名称获取
        String startCommand = commandAttr.getCommandI18nName("start");
        String stopCommand = commandAttr.getCommandI18nName("stop");

        // 这些应该调用新的国际化路径
        assertNotNull(startCommand);
        assertNotNull(stopCommand);
        assertEquals("devices.test_device.device_command_commands.start", startCommand);
        assertEquals("devices.test_device.device_command_commands.stop", stopCommand);
        
    }

    @Test
    public void testSelectInternationalization() {
        // 测试选择项国际化名称获取
        String fullOption = selectAttr.getOptionI18nName("full");
        String partialOption = selectAttr.getOptionI18nName("partial");

        // 这些应该调用新的国际化路径
        assertNotNull(fullOption);
        assertNotNull(partialOption);
        assertEquals("devices.test_device.start_option_options.full", fullOption);
        assertEquals("devices.test_device.start_option_options.partial", partialOption);
    }
}
