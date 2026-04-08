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

package com.ecat.core.LogicDevice;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.LogicMapping.IDeviceMapping;
import com.ecat.core.LogicMapping.LogicMappingManager;
import com.ecat.core.LogicState.ILogicAttribute;
import com.ecat.core.LogicState.LogicAttributeDefine;
import com.ecat.core.LogicState.LNumericAttribute;
import com.ecat.core.State.AttributeAbility;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.NumericAttribute;
import com.ecat.core.State.UnitInfo;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.I18n.I18nKeyPath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * LogicDevice 抽象类单元测试
 *
 * <p>测试逻辑设备的核心功能：
 * <ul>
 *   <li>getAttrDefs() 返回正确的属性定义列表</li>
 *   <li>getMappingType() 返回正确的映射类型</li>
 *   <li>genAttrMap 处理 null/空 mappings 返回空 map</li>
 *   <li>init() 正确创建逻辑属性并设置设备引用</li>
 *   <li>LogicMappingManager 静态引用的设置和获取</li>
 * </ul>
 *
 * <p>由于 LogicDevice 依赖 EcatCore、DeviceRegistry 等框架类，
 * 完整的集成测试需要集成环境。本测试聚焦于可独立测试的部分。
 *
 * @author coffee
 */
public class LogicDeviceTest {

    private LogicMappingManager testMappingManager;

    @Before
    public void setUp() {
        testMappingManager = new LogicMappingManager();
        // 设置静态 LogicMappingManager 引用，测试结束后恢复
        LogicDevice.setLogicMappingManager(testMappingManager);
    }

    @After
    public void tearDown() {
        // 清除静态引用，避免影响其他测试
        LogicDevice.setLogicMappingManager(null);
    }

    // ========== 辅助方法 ==========

    /**
     * 创建测试用的 ConfigEntry，包含指定的 data
     */
    private ConfigEntry createTestEntry(String entryId, Map<String, Object> data) {
        ConfigEntry entry = new ConfigEntry();
        entry.setEntryId(entryId);
        entry.setUniqueId(entryId);
        if (data == null) {
            data = new HashMap<>();
        }
        data.putIfAbsent("name", "test-device-" + entryId);
        entry.setData(data);
        return entry;
    }

    /**
     * 创建带 mappings 配置的 ConfigEntry
     */
    private ConfigEntry createEntryWithMappings(String entryId, Map<String, Object> mappings) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test-logic-device-" + entryId);
        if (mappings != null) {
            data.put("mappings", mappings);
        }
        return createTestEntry(entryId, data);
    }

    /**
     * 创建测试用的具体 LogicDevice 子类实例
     */
    private TestLogicDevice createTestLogicDevice(ConfigEntry entry) {
        return new TestLogicDevice(entry);
    }

    /**
     * 创建一个模拟的物理设备，注册到 testMappingManager 中
     */
    private DeviceBase createMockPhysicalDevice(String deviceId, String coordinate, String model) {
        ConfigEntry phyEntry = new ConfigEntry();
        phyEntry.setEntryId(deviceId);
        phyEntry.setUniqueId(deviceId);
        phyEntry.setCoordinate(coordinate);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "phy-device-" + deviceId);
        data.put("model", model);
        phyEntry.setData(data);

        return new DeviceBase(phyEntry) {
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
        };
    }

    /**
     * 创建一个测试用的 IDeviceMapping 实现
     */
    private static class TestDeviceMapping implements IDeviceMapping {
        private final String mappingType;
        private final String coordinate;
        private final String model;
        private final Map<String, ILogicAttribute<?>> attrCache = new HashMap<>();

        public TestDeviceMapping(String mappingType, String coordinate, String model) {
            this.mappingType = mappingType;
            this.coordinate = coordinate;
            this.model = model;
        }

        @Override
        public String getMappingType() { return mappingType; }

        @Override
        public String getDeviceCoordinate() { return coordinate; }

        @Override
        public String getDeviceModel() { return model; }

        @Override
        public ILogicAttribute<?> getAttr(String logicAttrId, DeviceBase phyDevice, com.ecat.core.LogicDevice.LogicDevice logicDevice) {
            // Return cached attribute if available
            ILogicAttribute<?> cached = attrCache.get(logicAttrId);
            if (cached != null) {
                return cached;
            }

            // Create a dummy AttributeBase for binding
            AttributeBase<Double> dummyAttr = new AttributeBase<Double>(
                logicAttrId, AttributeClass.VALUE, null, null, 2, false, false) {
                @Override public String getDisplayValue(UnitInfo toUnit) { return null; }
                @Override protected Double convertFromUnitImp(Double value, UnitInfo fromUnit) { return value; }
                @Override public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) { return value; }
                @Override public ConfigDefinition getValueDefinition() { return null; }
                @Override protected I18nKeyPath getI18nPrefixPath() { return new I18nKeyPath("test.", "test"); }
                @Override public AttributeType getAttributeType() { return AttributeType.NUMERIC; }
            };
            LNumericAttribute lAttr = new LNumericAttribute(dummyAttr);
            attrCache.put(logicAttrId, lAttr);
            return lAttr;
        }
    }

    // ========== 测试用例 ==========

    @Test
    public void testGetAttrDefsReturnsDefinitions() {
        ConfigEntry entry = createTestEntry("test-001", null);
        TestLogicDevice device = createTestLogicDevice(entry);

        List<LogicAttributeDefine> defs = device.getAttrDefs();
        assertNotNull(defs);
        assertEquals(1, defs.size());
        assertEquals("test_attr", defs.get(0).getAttrId());
        assertEquals(AttributeClass.VALUE, defs.get(0).getAttrClass());
    }

    @Test
    public void testGetMappingType() {
        ConfigEntry entry = createTestEntry("test-001", null);
        TestLogicDevice device = createTestLogicDevice(entry);

        assertEquals("TEST", device.getMappingType());
    }

    @Test
    public void testGenAttrMapWithNoMappingsKey() {
        // ConfigEntry with data but no "mappings" key
        // (DeviceBase constructor requires non-null data, so we provide minimal data)
        ConfigEntry entry = createTestEntry("test-001", null);
        // entry data has "name" but no "mappings" key
        Map<String, Object> data = entry.getData();
        data.remove("mappings"); // ensure no mappings key

        TestLogicDevice device = createTestLogicDevice(entry);
        device.init();

        Map<String, ILogicAttribute<?>> attrMap = device.getAttrMap();
        assertNotNull(attrMap);
        assertTrue("No mappings key should result in empty attrMap", attrMap.isEmpty());
    }

    @Test
    public void testGenAttrMapWithNullMappings() {
        // ConfigEntry with data but no "mappings" key
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test-device");
        ConfigEntry entry = createTestEntry("test-001", data);

        TestLogicDevice device = createTestLogicDevice(entry);
        device.init();

        Map<String, ILogicAttribute<?>> attrMap = device.getAttrMap();
        assertNotNull(attrMap);
        assertTrue("Null mappings should result in empty attrMap", attrMap.isEmpty());
    }

    @Test
    public void testGenAttrMapWithEmptyMappings() {
        // ConfigEntry with empty mappings map
        Map<String, Object> mappings = new HashMap<>();
        ConfigEntry entry = createEntryWithMappings("test-001", mappings);

        TestLogicDevice device = createTestLogicDevice(entry);
        device.init();

        Map<String, ILogicAttribute<?>> attrMap = device.getAttrMap();
        assertNotNull(attrMap);
        assertTrue("Empty mappings should result in empty attrMap", attrMap.isEmpty());
    }

    @Test
    public void testGenAttrMapWithMissingDeviceId() {
        // mapping entry without device_id should be skipped
        Map<String, Object> mappingConfig = new HashMap<>();
        // no "device_id" key
        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("test_attr", mappingConfig);
        ConfigEntry entry = createEntryWithMappings("test-001", mappings);

        TestLogicDevice device = createTestLogicDevice(entry);
        device.init();

        Map<String, ILogicAttribute<?>> attrMap = device.getAttrMap();
        assertNotNull(attrMap);
        assertTrue("Mapping without device_id should be skipped", attrMap.isEmpty());
    }

    @Test
    public void testGenAttrMapWithEmptyDeviceId() {
        // mapping entry with empty device_id should be skipped
        Map<String, Object> mappingConfig = new HashMap<>();
        mappingConfig.put("device_id", "");
        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("test_attr", mappingConfig);
        ConfigEntry entry = createEntryWithMappings("test-001", mappings);

        TestLogicDevice device = createTestLogicDevice(entry);
        device.init();

        Map<String, ILogicAttribute<?>> attrMap = device.getAttrMap();
        assertNotNull(attrMap);
        assertTrue("Mapping with empty device_id should be skipped", attrMap.isEmpty());
    }

    @Test
    public void testGenAttrMapWithNullAttrConfig() {
        // mapping entry with null config should be skipped
        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("test_attr", null);
        ConfigEntry entry = createEntryWithMappings("test-001", mappings);

        TestLogicDevice device = createTestLogicDevice(entry);
        device.init();

        Map<String, ILogicAttribute<?>> attrMap = device.getAttrMap();
        assertNotNull(attrMap);
        assertTrue("Mapping with null config should be skipped", attrMap.isEmpty());
    }

    @Test
    public void testSetLogicMappingManager() {
        LogicMappingManager customManager = new LogicMappingManager();
        LogicDevice.setLogicMappingManager(customManager);

        // Verify via a test device that the manager is accessible
        // We can't directly access the static field, but we can verify
        // by checking behavior: if the manager is null, init() should not throw
        LogicDevice.setLogicMappingManager(null);

        ConfigEntry entry = createEntryWithMappings("test-001", new HashMap<>());
        TestLogicDevice device = createTestLogicDevice(entry);
        // Should not throw even with null mapping manager (because empty mappings)
        device.init();
        assertTrue(device.getAttrMap().isEmpty());
    }

    @Test
    public void testInitCreatesAttributesFromMapping() {
        // 验证：配置了 device_id 但物理设备不存在时，init() 应抛出异常，
        // 确保配置错误能被立即暴露而不是静默跳过。
        String phyDeviceId = "phy-001";
        String coordinate = "com.ecat:test-integration";
        String model = "TestModel";

        // Create and register a mapping
        TestDeviceMapping mapping = new TestDeviceMapping("TEST", coordinate, model);
        testMappingManager.registerMapping(mapping);

        // Create mapping config pointing to the physical device
        Map<String, Object> mappingConfig = new HashMap<>();
        mappingConfig.put("device_id", phyDeviceId);
        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("test_attr", mappingConfig);

        // core 为 null，物理设备无法找到
        ConfigEntry entry = createEntryWithMappings("test-001", mappings);
        TestLogicDevice device = createTestLogicDevice(entry);

        try {
            device.init();
            fail("Should throw when mapped device_id cannot be resolved (core is null)");
        } catch (RuntimeException e) {
            // init() wraps the original exception
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            assertTrue("Exception should mention core is null",
                msg.contains("core is null"));
        }
    }

    @Test
    public void testAttrMapIsLinkedHashMap() {
        // Verify that attrMap preserves insertion order
        ConfigEntry entry = createEntryWithMappings("test-001", new HashMap<>());
        TestLogicDevice device = createTestLogicDevice(entry);
        device.init();

        Map<String, ILogicAttribute<?>> attrMap = device.getAttrMap();
        // LinkedHashMap preserves insertion order
        assertTrue("attrMap should be a LinkedHashMap for order preservation",
            attrMap instanceof LinkedHashMap);
    }

    @Test
    public void testGetEntryReturnsCorrectEntry() {
        ConfigEntry entry = createTestEntry("test-001", null);
        TestLogicDevice device = createTestLogicDevice(entry);

        assertSame(entry, device.getEntry());
    }

    @Test
    public void testGetIdReturnsEntryId() {
        ConfigEntry entry = createTestEntry("test-entry-id-123", null);
        TestLogicDevice device = createTestLogicDevice(entry);

        assertEquals("test-entry-id-123", device.getId());
    }

    // ========== 测试用具体子类 ==========

    /**
     * 测试用 LogicDevice 具体子类，提供固定的属性定义和映射类型。
     */
    private static class TestLogicDevice extends LogicDevice {
        private static final List<LogicAttributeDefine> TEST_DEFS = Arrays.asList(
            new LogicAttributeDefine("test_attr", AttributeClass.VALUE, null, null, 2, false, LNumericAttribute.class)
        );

        public TestLogicDevice(ConfigEntry entry) {
            super(entry);
        }

        @Override
        public List<LogicAttributeDefine> getAttrDefs() {
            return TEST_DEFS;
        }

        @Override
        protected String getMappingType() {
            return "TEST";
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void release() {}
    }
}
