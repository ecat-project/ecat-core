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
import com.ecat.core.LogicState.ILogicAttribute;
import com.ecat.core.State.AttributeAbility;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.UnitInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * LogicDeviceRegistry 单元测试
 *
 * <p>测试逻辑设备注册表的核心功能：
 * <ul>
 *   <li>设备的注册、查询、注销</li>
 *   <li>反向索引的构建与查找（物理属性 -> 逻辑属性引用）</li>
 *   <li>注销时反向索引的自动清理</li>
 *   <li>多设备引用同一物理属性的场景</li>
 * </ul>
 *
 * <p>由于 LogicDevice 尚未创建（Task 7），测试中使用匿名 DeviceBase 子类模拟逻辑设备。
 *
 * @author coffee
 */
public class LogicDeviceRegistryTest {

    private LogicDeviceRegistry registry;

    @Before
    public void setUp() {
        registry = new LogicDeviceRegistry();
    }

    // ========== 辅助方法 ==========

    /**
     * 创建测试用的 DeviceBase 子类实例（模拟逻辑设备）
     */
    private DeviceBase createMockDevice(String deviceId) {
        ConfigEntry entry = new ConfigEntry();
        entry.setEntryId(deviceId);
        entry.setUniqueId(deviceId);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test-device-" + deviceId);
        entry.setData(data);
        return new DeviceBase(entry) {
            @Override
            public void init() {}
            @Override
            public void start() {}
            @Override
            public void stop() {}
            @Override
            public void release() {}
        };
    }

    /**
     * 创建测试用的 ILogicAttribute 实现
     */
    private static class TestLogicAttribute implements ILogicAttribute<Double> {
        private String attributeID;
        private final List<AttributeBase<?>> bindedAttrs = new ArrayList<>();

        public TestLogicAttribute(String attributeID) {
            this.attributeID = attributeID;
        }

        public void addBindedAttr(AttributeBase<?> attr) {
            bindedAttrs.add(attr);
        }

        @Override
        public List<AttributeBase<?>> getBindedAttrs() {
            return bindedAttrs;
        }

        @Override
        public void updateBindAttrValue(AttributeBase<?> updatedAttr) {}

        @Override
        public void initAttributeID(String attrID) { this.attributeID = attrID; }

        @Override
        public void initNativeUnit(UnitInfo nativeUnit) {}

        @Override
        public void initValueChangeable(boolean valueChangeable) {}

        @Override
        public void initDisplayUnit(UnitInfo displayUnit) {}

        @Override
        public void initAttrClass(AttributeClass attrClass) {}

        @Override
        public String getI18nValue(UnitInfo toUnit) { return null; }

        @Override
        public boolean publicState() { return true; }

        @Override
        public boolean updateValue(Double newValue, AttributeStatus newStatus) { return true; }

        // --- AttributeAbility 最小化实现 ---

        @Override public String getAttributeID() { return attributeID; }
        @Override public boolean canUnitChange() { return false; }
        @Override public boolean changeDisplayUnit(UnitInfo newDisplayUnit) { return false; }
        @Override public boolean changeDisplayPrecision(int newPrecision) { return true; }
        @Override public int getDisplayPrecision() { return 0; }
        @Override public boolean canValueChange() { return false; }
        @Override public boolean setStatus(AttributeStatus newStatus) { return false; }
        @Override public AttributeStatus getStatus() { return AttributeStatus.EMPTY; }
        @Override public Double getValue() { return null; }
        @Override public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue) { return CompletableFuture.completedFuture(false); }
        @Override public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) { return CompletableFuture.completedFuture(false); }
        @Override public String getDisplayValue() { return null; }
        @Override public String getDisplayValue(UnitInfo toUnit) { return null; }
        @Override public String getDisplayUnitStr() { return ""; }
        @Override public UnitInfo getDisplayUnit() { return null; }
        @Override public UnitInfo getNativeUnit() { return null; }
        @Override public AttributeType getAttributeType() { return AttributeType.NUMERIC; }
        @Override public boolean isPersistable() { return false; }
        @Override public void setPersistable(boolean persistable) {}
        @Override public Double getDefaultValue() { return null; }
        @Override public void setDefaultValue(Double defaultValue) {}
    }

    /**
     * 创建测试用的 AttributeBase 匿名子类（模拟物理属性）
     */
    private AttributeBase<Double> createMockPhyAttr(String deviceId, String attrId) {
        // 创建一个简单的物理属性，绑定到指定设备
        ConfigEntry entry = new ConfigEntry();
        entry.setEntryId(deviceId);
        entry.setUniqueId(deviceId);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "phy-device-" + deviceId);
        entry.setData(data);

        DeviceBase phyDevice = new DeviceBase(entry) {
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
        };

        final AttributeBase<Double> phyAttr = new AttributeBase<Double>(
                attrId, AttributeClass.VALUE, null, null, 0, false, false) {
            @Override
            public String getDisplayValue(UnitInfo toUnit) { return null; }
            @Override
            protected Double convertFromUnitImp(Double value, UnitInfo fromUnit) { return value; }
            @Override
            public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) { return value; }
            @Override
            public com.ecat.core.Utils.DynamicConfig.ConfigDefinition getValueDefinition() { return null; }
            @Override
            protected com.ecat.core.I18n.I18nKeyPath getI18nPrefixPath() {
                return new com.ecat.core.I18n.I18nKeyPath("test.", "test");
            }
            @Override
            public AttributeType getAttributeType() { return AttributeType.NUMERIC; }
        };

        // 将物理属性绑定到物理设备，使 phyAttr.getDevice() 返回正确的设备
        phyDevice.setAttribute(phyAttr);
        return phyAttr;
    }

    // ========== 测试用例 ==========

    @Test
    public void testRegisterAndGetDeviceByID() {
        DeviceBase device = createMockDevice("logic-device-001");
        registry.register("logic-device-001", device);

        DeviceBase found = registry.getDeviceByID("logic-device-001");
        assertNotNull(found);
        assertSame(device, found);
    }

    @Test
    public void testGetDeviceByIDNotFound() {
        DeviceBase found = registry.getDeviceByID("non-existent");
        assertNull(found);
    }

    @Test
    public void testUnregister() {
        DeviceBase device = createMockDevice("logic-device-001");
        registry.register("logic-device-001", device);

        registry.unregister("logic-device-001");

        DeviceBase found = registry.getDeviceByID("logic-device-001");
        assertNull(found);
    }

    @Test
    public void testUnregisterNonExistent() {
        // 注销不存在的设备不应抛出异常
        registry.unregister("non-existent");
        // 验证注册表仍为空
        assertTrue(registry.getAllDevices().isEmpty());
    }

    @Test
    public void testGetAllDevices() {
        DeviceBase device1 = createMockDevice("logic-device-001");
        DeviceBase device2 = createMockDevice("logic-device-002");
        DeviceBase device3 = createMockDevice("logic-device-003");

        registry.register("logic-device-001", device1);
        registry.register("logic-device-002", device2);
        registry.register("logic-device-003", device3);

        List<DeviceBase> allDevices = registry.getAllDevices();
        assertEquals(3, allDevices.size());
        assertTrue(allDevices.contains(device1));
        assertTrue(allDevices.contains(device2));
        assertTrue(allDevices.contains(device3));
    }

    @Test
    public void testGetAllDevicesEmpty() {
        List<DeviceBase> allDevices = registry.getAllDevices();
        assertNotNull(allDevices);
        assertTrue(allDevices.isEmpty());
    }

    @Test
    public void testGetAllDevicesReturnsCopy() {
        DeviceBase device = createMockDevice("logic-device-001");
        registry.register("logic-device-001", device);

        List<DeviceBase> allDevices = registry.getAllDevices();
        // 修改返回的列表不应影响注册表内部状态
        allDevices.clear();
        assertEquals(1, registry.getAllDevices().size());
    }

    @Test
    public void testBuildReverseIndex() {
        // 创建逻辑设备和逻辑属性
        DeviceBase logicDevice = createMockDevice("logic-device-001");
        TestLogicAttribute logicAttr = new TestLogicAttribute("l_so2");

        // 创建物理属性（注意：需要手动设置 device 到 AttributeBase 上，
        // 但由于 AttributeBase.setDevice 是 protected/package-access 的，
        // 我们需要通过 DeviceBase.setAttribute 来绑定）
        AttributeBase<Double> phyAttr = createMockPhyAttr("phy-device-001", "so2");

        // 绑定物理属性到逻辑属性
        logicAttr.addBindedAttr(phyAttr);

        // 注册逻辑设备
        registry.register("logic-device-001", logicDevice);

        // 构建反向索引
        Map<String, ILogicAttribute<?>> attrMap = new HashMap<>();
        attrMap.put("l_so2", logicAttr);
        registry.buildReverseIndex("logic-device-001", attrMap);

        // 通过物理属性查找逻辑属性引用
        List<LogicDeviceAttrRef> refs = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNotNull(refs);
        assertEquals(1, refs.size());
        assertSame(logicDevice, refs.get(0).getLogicDevice());
        assertSame(logicAttr, refs.get(0).getLogicAttr());
    }

    @Test
    public void testBuildReverseIndexWithNullAttrMap() {
        DeviceBase device = createMockDevice("logic-device-001");
        registry.register("logic-device-001", device);

        // null attrMap 不应抛出异常
        registry.buildReverseIndex("logic-device-001", null);

        List<LogicDeviceAttrRef> refs = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNull(refs);
    }

    @Test
    public void testBuildReverseIndexForNonRegisteredDevice() {
        // 为未注册的设备构建反向索引不应抛出异常
        Map<String, ILogicAttribute<?>> attrMap = new HashMap<>();
        registry.buildReverseIndex("non-existent", attrMap);

        // 不应有任何反向索引条目
        List<LogicDeviceAttrRef> refs = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNull(refs);
    }

    @Test
    public void testFindByPhysicalAttrNotFound() {
        // 注册一个设备但未构建反向索引
        DeviceBase device = createMockDevice("logic-device-001");
        registry.register("logic-device-001", device);

        // 查找不存在的物理属性
        List<LogicDeviceAttrRef> refs = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNull(refs);
    }

    @Test
    public void testRemoveReverseIndexOnUnregister() {
        // 创建逻辑设备和逻辑属性
        DeviceBase logicDevice = createMockDevice("logic-device-001");
        TestLogicAttribute logicAttr = new TestLogicAttribute("l_so2");
        AttributeBase<Double> phyAttr = createMockPhyAttr("phy-device-001", "so2");
        logicAttr.addBindedAttr(phyAttr);

        // 注册并构建反向索引
        registry.register("logic-device-001", logicDevice);
        Map<String, ILogicAttribute<?>> attrMap = new HashMap<>();
        attrMap.put("l_so2", logicAttr);
        registry.buildReverseIndex("logic-device-001", attrMap);

        // 验证反向索引存在
        List<LogicDeviceAttrRef> refs = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNotNull(refs);
        assertEquals(1, refs.size());

        // 注销设备
        registry.unregister("logic-device-001");

        // 验证反向索引已被清理
        refs = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNull(refs);
    }

    @Test
    public void testMultipleDevicesSamePhysicalAttr() {
        // 创建两个逻辑设备，都引用同一个物理属性
        DeviceBase logicDevice1 = createMockDevice("logic-device-001");
        DeviceBase logicDevice2 = createMockDevice("logic-device-002");

        TestLogicAttribute logicAttr1 = new TestLogicAttribute("l_so2_1");
        TestLogicAttribute logicAttr2 = new TestLogicAttribute("l_so2_2");

        // 两个逻辑属性都绑定到同一个物理属性
        AttributeBase<Double> phyAttr = createMockPhyAttr("phy-device-001", "so2");
        logicAttr1.addBindedAttr(phyAttr);
        logicAttr2.addBindedAttr(phyAttr);

        // 注册两个设备并构建反向索引
        registry.register("logic-device-001", logicDevice1);
        registry.register("logic-device-002", logicDevice2);

        Map<String, ILogicAttribute<?>> attrMap1 = new HashMap<>();
        attrMap1.put("l_so2_1", logicAttr1);
        registry.buildReverseIndex("logic-device-001", attrMap1);

        Map<String, ILogicAttribute<?>> attrMap2 = new HashMap<>();
        attrMap2.put("l_so2_2", logicAttr2);
        registry.buildReverseIndex("logic-device-002", attrMap2);

        // 查找物理属性，应返回两个逻辑设备属性引用
        List<LogicDeviceAttrRef> refs = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNotNull(refs);
        assertEquals(2, refs.size());

        // 验证两个引用分别指向不同的逻辑设备和逻辑属性
        boolean foundDevice1 = false, foundDevice2 = false;
        for (LogicDeviceAttrRef ref : refs) {
            if (ref.getLogicDevice() == logicDevice1 && ref.getLogicAttr() == logicAttr1) {
                foundDevice1 = true;
            }
            if (ref.getLogicDevice() == logicDevice2 && ref.getLogicAttr() == logicAttr2) {
                foundDevice2 = true;
            }
        }
        assertTrue("应包含逻辑设备1的引用", foundDevice1);
        assertTrue("应包含逻辑设备2的引用", foundDevice2);
    }

    @Test
    public void testRebuildReverseIndexReplacesOldEntries() {
        // 创建逻辑设备
        DeviceBase logicDevice = createMockDevice("logic-device-001");

        // 第一次构建：绑定 phy-device-001:so2
        TestLogicAttribute logicAttr1 = new TestLogicAttribute("l_so2");
        AttributeBase<Double> phyAttr1 = createMockPhyAttr("phy-device-001", "so2");
        logicAttr1.addBindedAttr(phyAttr1);

        registry.register("logic-device-001", logicDevice);
        Map<String, ILogicAttribute<?>> attrMap1 = new HashMap<>();
        attrMap1.put("l_so2", logicAttr1);
        registry.buildReverseIndex("logic-device-001", attrMap1);

        // 验证第一次构建
        List<LogicDeviceAttrRef> refs1 = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNotNull(refs1);
        assertEquals(1, refs1.size());

        // 第二次构建：绑定到 phy-device-002:no2（替换旧的绑定）
        TestLogicAttribute logicAttr2 = new TestLogicAttribute("l_no2");
        AttributeBase<Double> phyAttr2 = createMockPhyAttr("phy-device-002", "no2");
        logicAttr2.addBindedAttr(phyAttr2);

        Map<String, ILogicAttribute<?>> attrMap2 = new HashMap<>();
        attrMap2.put("l_no2", logicAttr2);
        registry.buildReverseIndex("logic-device-001", attrMap2);

        // 旧的绑定应被清除
        List<LogicDeviceAttrRef> refsOld = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNull("旧的绑定应被清除", refsOld);

        // 新的绑定应存在
        List<LogicDeviceAttrRef> refsNew = registry.findByPhysicalAttr("phy-device-002", "no2");
        assertNotNull(refsNew);
        assertEquals(1, refsNew.size());
        assertSame(logicDevice, refsNew.get(0).getLogicDevice());
        assertSame(logicAttr2, refsNew.get(0).getLogicAttr());
    }

    @Test
    public void testUnregisterOneDeviceKeepsOtherReverseIndex() {
        // 两个逻辑设备引用同一个物理属性
        DeviceBase logicDevice1 = createMockDevice("logic-device-001");
        DeviceBase logicDevice2 = createMockDevice("logic-device-002");

        TestLogicAttribute logicAttr1 = new TestLogicAttribute("l_so2_1");
        TestLogicAttribute logicAttr2 = new TestLogicAttribute("l_so2_2");

        AttributeBase<Double> phyAttr = createMockPhyAttr("phy-device-001", "so2");
        logicAttr1.addBindedAttr(phyAttr);
        logicAttr2.addBindedAttr(phyAttr);

        registry.register("logic-device-001", logicDevice1);
        registry.register("logic-device-002", logicDevice2);

        Map<String, ILogicAttribute<?>> attrMap1 = new HashMap<>();
        attrMap1.put("l_so2_1", logicAttr1);
        registry.buildReverseIndex("logic-device-001", attrMap1);

        Map<String, ILogicAttribute<?>> attrMap2 = new HashMap<>();
        attrMap2.put("l_so2_2", logicAttr2);
        registry.buildReverseIndex("logic-device-002", attrMap2);

        // 注销其中一个设备
        registry.unregister("logic-device-001");

        // 另一个设备的反向索引应保留
        List<LogicDeviceAttrRef> refs = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNotNull(refs);
        assertEquals(1, refs.size());
        assertSame(logicDevice2, refs.get(0).getLogicDevice());
        assertSame(logicAttr2, refs.get(0).getLogicAttr());
    }

    @Test
    public void testLogicAttrWithNoBindedAttrs() {
        // 逻辑属性没有绑定任何物理属性
        DeviceBase logicDevice = createMockDevice("logic-device-001");
        TestLogicAttribute logicAttr = new TestLogicAttribute("l_status");
        // 不添加任何 bindedAttrs

        registry.register("logic-device-001", logicDevice);

        Map<String, ILogicAttribute<?>> attrMap = new HashMap<>();
        attrMap.put("l_status", logicAttr);
        registry.buildReverseIndex("logic-device-001", attrMap);

        // 应无反向索引条目
        List<LogicDeviceAttrRef> refs = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNull(refs);
    }

    @Test
    public void testLogicAttrWithMultipleBindedAttrs() {
        // 一个逻辑属性绑定多个物理属性
        DeviceBase logicDevice = createMockDevice("logic-device-001");
        TestLogicAttribute logicAttr = new TestLogicAttribute("l_mix");

        AttributeBase<Double> phyAttr1 = createMockPhyAttr("phy-device-001", "so2");
        AttributeBase<Double> phyAttr2 = createMockPhyAttr("phy-device-001", "no2");
        logicAttr.addBindedAttr(phyAttr1);
        logicAttr.addBindedAttr(phyAttr2);

        registry.register("logic-device-001", logicDevice);

        Map<String, ILogicAttribute<?>> attrMap = new HashMap<>();
        attrMap.put("l_mix", logicAttr);
        registry.buildReverseIndex("logic-device-001", attrMap);

        // 查找 phy-device-001:so2
        List<LogicDeviceAttrRef> refs1 = registry.findByPhysicalAttr("phy-device-001", "so2");
        assertNotNull(refs1);
        assertEquals(1, refs1.size());
        assertSame(logicAttr, refs1.get(0).getLogicAttr());

        // 查找 phy-device-001:no2
        List<LogicDeviceAttrRef> refs2 = registry.findByPhysicalAttr("phy-device-001", "no2");
        assertNotNull(refs2);
        assertEquals(1, refs2.size());
        assertSame(logicAttr, refs2.get(0).getLogicAttr());
    }
}
