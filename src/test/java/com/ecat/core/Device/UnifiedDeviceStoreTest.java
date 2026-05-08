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

package com.ecat.core.Device;

import com.ecat.core.LogicDevice.LogicDeviceRegistry;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * UnifiedDeviceStore 单元测试
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>addRegistry — 添加单个和多个注册表</li>
 *   <li>getDeviceByID — 从首个/第二个注册表查找、未找到、空 store、ID 重复（先添加者优先）</li>
 *   <li>getAllDevices — 空结果、无注册表、多注册表合并、返回可变副本</li>
 *   <li>动态添加注册表 — 初始查询后追加注册表</li>
 * </ul>
 *
 * @author coffee
 */
public class UnifiedDeviceStoreTest {

    // ========== addRegistry ==========

    @Test
    public void testAddSingleRegistry() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry registry = new DeviceRegistry();
        store.addRegistry(registry);

        // 注册设备后可以通过 store 查到
        DeviceBase device = TestPhyDeviceHelper.createDevice("dev-1");
        registry.register("dev-1", device);

        assertSame(device, store.getDeviceByID("dev-1"));
    }

    @Test
    public void testAddMultipleRegistries() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry phyRegistry = new DeviceRegistry();
        LogicDeviceRegistry logicRegistry = new LogicDeviceRegistry();

        store.addRegistry(phyRegistry);
        store.addRegistry(logicRegistry);

        DeviceBase phyDevice = TestPhyDeviceHelper.createDevice("phy-1");
        DeviceBase logicDevice = TestPhyDeviceHelper.createDevice("logic-1");

        phyRegistry.register("phy-1", phyDevice);
        logicRegistry.register("logic-1", logicDevice);

        assertSame(phyDevice, store.getDeviceByID("phy-1"));
        assertSame(logicDevice, store.getDeviceByID("logic-1"));
    }

    // ========== getDeviceByID ==========

    @Test
    public void testGetDeviceByIDFoundInFirstRegistry() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry first = new DeviceRegistry();
        DeviceRegistry second = new DeviceRegistry();

        store.addRegistry(first);
        store.addRegistry(second);

        DeviceBase device = TestPhyDeviceHelper.createDevice("dev-A");
        first.register("dev-A", device);

        assertSame(device, store.getDeviceByID("dev-A"));
    }

    @Test
    public void testGetDeviceByIDFoundInSecondRegistry() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry first = new DeviceRegistry();
        DeviceRegistry second = new DeviceRegistry();

        store.addRegistry(first);
        store.addRegistry(second);

        DeviceBase device = TestPhyDeviceHelper.createDevice("dev-B");
        second.register("dev-B", device);

        assertSame(device, store.getDeviceByID("dev-B"));
    }

    @Test
    public void testGetDeviceByIDNotFound() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry registry = new DeviceRegistry();
        store.addRegistry(registry);

        assertNull(store.getDeviceByID("nonexistent"));
    }

    @Test
    public void testGetDeviceByIDEmptyStore() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        // 没有添加任何注册表
        assertNull(store.getDeviceByID("any-id"));
    }

    @Test
    public void testGetDeviceByIDSameIdInBothRegistries_FirstWins() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry first = new DeviceRegistry();
        DeviceRegistry second = new DeviceRegistry();

        store.addRegistry(first);
        store.addRegistry(second);

        DeviceBase deviceFromFirst = TestPhyDeviceHelper.createDevice("dup-id");
        DeviceBase deviceFromSecond = TestPhyDeviceHelper.createDevice("dup-id");

        first.register("dup-id", deviceFromFirst);
        second.register("dup-id", deviceFromSecond);

        // 两个注册表都有相同 ID 的设备，应该返回第一个注册表中的
        DeviceBase result = store.getDeviceByID("dup-id");
        assertSame(deviceFromFirst, result);
        assertNotSame(deviceFromSecond, result);
    }

    // ========== getAllDevices ==========

    @Test
    public void testGetAllDevicesEmptyRegistries() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry registry = new DeviceRegistry();
        store.addRegistry(registry);

        List<DeviceBase> all = store.getAllDevices();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    public void testGetAllDevicesNoRegistries() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        // 没有添加任何注册表

        List<DeviceBase> all = store.getAllDevices();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    public void testGetAllDevicesFromMultipleRegistries() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry phyRegistry = new DeviceRegistry();
        LogicDeviceRegistry logicRegistry = new LogicDeviceRegistry();

        store.addRegistry(phyRegistry);
        store.addRegistry(logicRegistry);

        DeviceBase phyDevice = TestPhyDeviceHelper.createDevice("phy-1");
        DeviceBase logicDevice = TestPhyDeviceHelper.createDevice("logic-1");

        phyRegistry.register("phy-1", phyDevice);
        logicRegistry.register("logic-1", logicDevice);

        List<DeviceBase> all = store.getAllDevices();
        assertEquals(2, all.size());
        assertTrue(all.contains(phyDevice));
        assertTrue(all.contains(logicDevice));
    }

    @Test
    public void testGetAllDevicesReturnsMutableCopy() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry registry = new DeviceRegistry();
        store.addRegistry(registry);

        DeviceBase device = TestPhyDeviceHelper.createDevice("dev-1");
        registry.register("dev-1", device);

        // 第一次获取
        List<DeviceBase> all1 = store.getAllDevices();
        assertEquals(1, all1.size());

        // 修改返回的列表不应影响 store 内部状态
        all1.clear();

        // 第二次获取应该仍然返回设备
        List<DeviceBase> all2 = store.getAllDevices();
        assertEquals(1, all2.size());
        assertSame(device, all2.get(0));
    }

    // ========== 动态添加注册表 ==========

    @Test
    public void testDynamicRegistryAddition() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry first = new DeviceRegistry();
        store.addRegistry(first);

        DeviceBase device1 = TestPhyDeviceHelper.createDevice("dev-1");
        first.register("dev-1", device1);

        // 初始只有 first 注册表
        assertEquals(1, store.getAllDevices().size());
        assertSame(device1, store.getDeviceByID("dev-1"));

        // 动态添加第二个注册表
        LogicDeviceRegistry second = new LogicDeviceRegistry();
        store.addRegistry(second);

        DeviceBase device2 = TestPhyDeviceHelper.createDevice("logic-1");
        second.register("logic-1", device2);

        // 现在应该能查到两个注册表中的设备
        assertEquals(2, store.getAllDevices().size());
        assertSame(device1, store.getDeviceByID("dev-1"));
        assertSame(device2, store.getDeviceByID("logic-1"));
    }

    @Test
    public void testDynamicRegistryAddition_DeviceRegisteredBeforeAdding() {
        UnifiedDeviceStore store = new UnifiedDeviceStore();
        DeviceRegistry first = new DeviceRegistry();
        store.addRegistry(first);

        // 先创建第二个注册表并注册设备
        LogicDeviceRegistry second = new LogicDeviceRegistry();
        DeviceBase preRegisteredDevice = TestPhyDeviceHelper.createDevice("pre-reg");
        second.register("pre-reg", preRegisteredDevice);

        // 此时 store 中没有 second，查不到
        assertNull(store.getDeviceByID("pre-reg"));

        // 动态添加后可以查到
        store.addRegistry(second);
        assertSame(preRegisteredDevice, store.getDeviceByID("pre-reg"));
    }
}
