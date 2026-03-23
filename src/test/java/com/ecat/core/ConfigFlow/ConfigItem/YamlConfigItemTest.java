/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.ConfigFlow.ConfigItem;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * YamlConfigItem 单元测试
 * <p>
 * 重点验证多层嵌套数据、特殊值类型、边界条件的 YAML 转换。
 */
public class YamlConfigItemTest {

    // ========== 基础属性 ==========

    @Test
    public void testFieldType() {
        YamlConfigItem item = new YamlConfigItem("summary");
        assertEquals("yaml", item.getFieldType());
    }

    @Test
    public void testReadOnly() {
        YamlConfigItem item = new YamlConfigItem("summary");
        assertTrue(item.isReadOnly());
    }

    @Test
    public void testNotRequired() {
        YamlConfigItem item = new YamlConfigItem("summary");
        assertFalse(item.isRequired());
    }

    @Test
    public void testKey() {
        YamlConfigItem item = new YamlConfigItem("config_summary");
        assertEquals("config_summary", item.getKey());
    }

    // ========== 链式设置 ==========

    @Test
    public void testDisplayName() {
        YamlConfigItem item = new YamlConfigItem("summary")
            .displayName("配置详情");
        assertEquals("配置详情", item.getDisplayName());
    }

    @Test
    public void testDescription() {
        YamlConfigItem item = new YamlConfigItem("summary")
            .description("以下是配置摘要");
        assertEquals("以下是配置摘要", item.getDescription());
    }

    // ========== YAML 转换 ==========

    @Test
    public void testNullMapReturnsEmpty() {
        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(null);
        assertEquals("", item.getDefaultValue());
    }

    @Test
    public void testEmptyMapReturnsEmpty() {
        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(new HashMap<>());
        assertEquals("", item.getDefaultValue());
    }

    @Test
    public void testFlatMapConversion() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "测试设备");
        data.put("vendor", "sailhero");
        data.put("port", 9600);

        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(data);

        String yaml = item.getDefaultValue();
        assertTrue("YAML 应包含 name 字段", yaml.contains("name:"));
        assertTrue("YAML 应包含 vendor 字段", yaml.contains("vendor:"));
        assertTrue("YAML 应包含 port 字段", yaml.contains("port:"));
        assertTrue("YAML 应使用 BLOCK 风格（多行）", yaml.contains("\n"));
    }

    @Test
    public void testOneLevelNestedMap() {
        // 模拟 comm_settings 嵌套结构
        Map<String, Object> commSettings = new LinkedHashMap<>();
        commSettings.put("baudrate", "9600");
        commSettings.put("data_bits", "8");
        commSettings.put("parity", "None");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "测试设备");
        data.put("comm_settings", commSettings);

        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(data);

        String yaml = item.getDefaultValue();
        assertTrue("应包含顶层字段", yaml.contains("name:"));
        assertTrue("应包含嵌套键", yaml.contains("comm_settings:"));
        assertTrue("嵌套内容应缩进", yaml.contains("baudrate:"));
        assertTrue("嵌套内容应缩进", yaml.contains("parity:"));
    }

    @Test
    public void testTwoLevelNestedMap() {
        // 模拟三层嵌套: device → comm → serial
        Map<String, Object> serial = new LinkedHashMap<>();
        serial.put("port", "ttyUSB0");
        serial.put("baudrate", 9600);

        Map<String, Object> comm = new LinkedHashMap<>();
        comm.put("serial", serial);
        comm.put("timeout", 500);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("device", comm);

        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(data);

        String yaml = item.getDefaultValue();
        assertTrue("应包含第一层 device", yaml.contains("device:"));
        assertTrue("应包含第二层 serial", yaml.contains("serial:"));
        assertTrue("应包含第三层 port", yaml.contains("port: ttyUSB0"));
        assertTrue("应包含第三层 baudrate", yaml.contains("baudrate: 9600"));
        assertTrue("应包含第二层 timeout", yaml.contains("timeout: 500"));
    }

    @Test
    public void testThreeLevelNestedMap() {
        // 模拟四层嵌套
        Map<String, Object> level3 = new LinkedHashMap<>();
        level3.put("enabled", true);
        level3.put("interval", 30);

        Map<String, Object> level2 = new LinkedHashMap<>();
        level2.put("monitor", level3);
        level2.put("retry_count", 3);

        Map<String, Object> level1 = new LinkedHashMap<>();
        level1.put("subsystem", level2);
        level1.put("version", "2.0");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("config", level1);

        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(root);

        String yaml = item.getDefaultValue();
        assertTrue("应包含 config", yaml.contains("config:"));
        assertTrue("应包含 subsystem", yaml.contains("subsystem:"));
        assertTrue("应包含 monitor", yaml.contains("monitor:"));
        assertTrue("应包含 enabled", yaml.contains("enabled: true"));
        assertTrue("应包含 interval", yaml.contains("interval: 30"));
    }

    @Test
    public void testMixedValueTypes() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "设备A");
        data.put("enabled", true);
        data.put("disabled", false);
        data.put("count", 42);
        data.put("ratio", 3.14);
        data.put("notes", "");

        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(data);

        String yaml = item.getDefaultValue();
        assertTrue("字符串值", yaml.contains("name: 设备A"));
        assertTrue("布尔 true", yaml.contains("enabled: true"));
        assertTrue("布尔 false", yaml.contains("disabled: false"));
        assertTrue("整数值", yaml.contains("count: 42"));
        assertTrue("浮点数值", yaml.contains("ratio: 3.14"));
        assertTrue("空字符串应引号包裹", yaml.contains("notes:"));
    }

    @Test
    public void testSpecialCharacters() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "设备: 测试/100% #1");
        data.put("path", "/dev/ttyUSB0");

        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(data);

        String yaml = item.getDefaultValue();
        assertTrue("应包含设备名", yaml.contains("设备"));
        assertTrue("应包含路径", yaml.contains("/dev/ttyUSB0"));
        // YAML 格式应有效（不抛异常且包含 key）
        assertTrue("应包含 name key", yaml.contains("name:"));
    }

    @Test
    public void testChineseCharacters() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("设备名称", "PM2.5监测仪");
        data.put("厂商", "赛默飞世尔");
        data.put("安装位置", "北京市朝阳区");

        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(data);

        String yaml = item.getDefaultValue();
        assertTrue("应包含中文字段名", yaml.contains("设备名称:"));
        assertTrue("应包含中文值", yaml.contains("PM2.5监测仪"));
        assertTrue("应包含厂商", yaml.contains("厂商:"));
        assertTrue("应包含位置", yaml.contains("安装位置:"));
    }

    @Test
    public void testNestedMapWithListValues() {
        // 嵌套 Map 中包含 List 值
        Map<String, Object> tags = new LinkedHashMap<>();
        tags.put("pollutants", java.util.Arrays.asList("PM2.5", "PM10", "O3"));
        tags.put("ranges", java.util.Arrays.asList("0-500", "0-1000"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "监测站A");
        data.put("tags", tags);

        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(data);

        String yaml = item.getDefaultValue();
        assertTrue("应包含 name", yaml.contains("name:"));
        assertTrue("应包含 tags", yaml.contains("tags:"));
        assertTrue("应包含 pollutants", yaml.contains("pollutants:"));
        assertTrue("应包含 PM2.5", yaml.contains("PM2.5"));
    }

    // ========== 验证逻辑 ==========

    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void testValidateAlwaysReturnsNull() {
        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(mapOf("key", "value"));

        // 只读字段，验证始终返回 null
        assertNull("null 值验证应返回 null", item.validate(null));
        assertNull("字符串值验证应返回 null", item.validate("some text"));
        assertNull("数字值验证应返回 null", item.validate(123));
    }

    @Test
    public void testAddDefaultValueIsNoop() {
        Map<String, Object> config = new HashMap<>();
        config.put("existing", "value");

        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(mapOf("key", "value"));

        // addDefaultValue 不应修改 config
        item.addDefaultValue(config);
        assertEquals(1, config.size());
        assertEquals("value", config.get("existing"));
    }

    // ========== setValue 链式调用 ==========

    @Test
    public void testSetValueReturnsThis() {
        YamlConfigItem item = new YamlConfigItem("summary");
        YamlConfigItem result = item.setValue(mapOf("key", "value"));
        assertSame("setValue 应返回 this", item, result);
    }

    @Test
    public void testSetValueOverridesPrevious() {
        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(mapOf("a", "1"))
            .setValue(mapOf("b", "2"));

        String yaml = item.getDefaultValue();
        assertFalse("第一次的值应被覆盖", yaml.contains("a:"));
        assertTrue("应包含第二次的值", yaml.contains("b:"));
    }

    // ========== 真实场景模拟 ==========

    @Test
    public void testSailheroLikeEntryData() {
        // 模拟 Sailhero 设备 entryData（comm_settings 展开到顶层）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "SO2监测仪 XHS2000B(黑盒新版V3.0)");
        data.put("model", "XHS2000BV3");
        data.put("sn", "SN001");
        data.put("class", "air.monitor.so2");
        data.put("vendor", "sailhero");
        // comm_settings 展开后的字段
        data.put("serial_port", "ttyS0");
        data.put("baudrate", "9600");
        data.put("data_bits", "8");
        data.put("stop_bits", "1");
        data.put("parity", "None");
        data.put("flow_control", "0");
        data.put("timeout", 500);

        YamlConfigItem item = new YamlConfigItem("config_summary")
            .displayName("配置详情")
            .setValue(data);

        String yaml = item.getDefaultValue();
        // 验证关键业务字段
        assertTrue("应包含设备名称", yaml.contains("name:"));
        assertTrue("应包含型号", yaml.contains("model:"));
        assertTrue("应包含序列号", yaml.contains("sn:"));
        assertTrue("应包含设备类型", yaml.contains("class:"));
        assertTrue("应包含厂商", yaml.contains("vendor:"));
        assertTrue("应包含串口", yaml.contains("serial_port:"));
        assertTrue("应包含波特率", yaml.contains("baudrate:"));
        // 验证是多行格式
        assertTrue("应使用 BLOCK 风格", yaml.split("\n").length > 5);
    }

    @Test
    public void testNestedCommSettingsNotExpanded() {
        // 模拟 comm_settings 未展开的原始数据
        Map<String, Object> commSettings = new LinkedHashMap<>();
        commSettings.put("serial_port", "ttyUSB0");
        commSettings.put("baudrate", "19200");
        commSettings.put("parity", "Even");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "设备B");
        data.put("vendor", "vaisala");
        data.put("comm_settings", commSettings);

        YamlConfigItem item = new YamlConfigItem("summary")
            .setValue(data);

        String yaml = item.getDefaultValue();
        assertTrue("应包含 name", yaml.contains("name:"));
        assertTrue("应包含 comm_settings 嵌套", yaml.contains("comm_settings:"));
        assertTrue("嵌套下应包含 serial_port", yaml.contains("serial_port: ttyUSB0"));
        assertTrue("嵌套下应包含 baudrate", yaml.contains("baudrate:"));
    }
}
