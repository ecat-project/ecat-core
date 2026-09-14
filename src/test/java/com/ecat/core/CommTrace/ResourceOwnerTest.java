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

package com.ecat.core.CommTrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationLoadOption;
import com.ecat.core.Utils.Mdc.MdcContext;

/**
 * ResourceOwner 身份模型测试。风险锁点：构造校验矩阵（层-字段错位组合必须 fail-fast，
 * 否则账本键与查询契约会静默错位）、mdcEntries 四层派发（归因与日志同一真相源）、
 * DEVICE 层 name 现取（注册期快照会撒谎）、host 引用不构成静态钉扎（账本生命周期外的
 * 泄漏）。全确定性断言，无并发等待。
 */
public class ResourceOwnerTest {

    private static final String COORD = "com.ecat:integration-test";

    // ========== 夹具 ==========

    /** 最小设备桩（entry-backed + 网关子设备两构造形态），带改名钩子供 name 现取断言。 */
    private static final class StubDevice extends DeviceBase {
        StubDevice(ConfigEntry entry) {
            super(entry);
        }

        StubDevice(ConfigEntry gatewayEntry, String uniqueId, Map<String, Object> config) {
            super(gatewayEntry, uniqueId, config);
        }

        void rename(String newName) {
            this.name = newName;
        }

        @Override
        public void init() {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void release() {
        }
    }

    /** 最小集成桩：getCoordinate 不依赖 loadOption（未 onLoad 的测试环境恒 null）。 */
    private static final class StubIntegration extends IntegrationBase {
        private final String coordinate;

        StubIntegration(String coordinate) {
            this.coordinate = coordinate;
        }

        @Override
        public String getCoordinate() {
            return coordinate;
        }

        @Override
        public void onLoad(EcatCore core, IntegrationLoadOption loadOption) {
        }

        @Override
        public void onInit() {
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onPause() {
        }
        // onRelease 由基类 final 收口，无需（也不可）覆写
    }

    private static ConfigEntry entry(String entryId, String coordinate) {
        return new ConfigEntry.Builder().entryId(entryId).coordinate(coordinate).uniqueId("sn-1").build();
    }

    private static ConfigEntry entryWithName(String entryId, String coordinate, String deviceName) {
        ConfigEntry e = entry(entryId, coordinate);
        Map<String, Object> data = new HashMap<>();
        data.put("name", deviceName);
        e.setData(data);
        return e;
    }

    // ========== of(host) 派生（四层身份模型，方案 A 裁定） ==========

    @Test
    public void ofDeviceHostDerivesRealEntryIdAndDeviceId() {
        StubDevice device = new StubDevice(entry("ent-1", COORD));
        ResourceOwner owner = ResourceOwner.of(device);

        assertEquals(OwnerLevel.DEVICE, owner.getLevel());
        assertEquals(COORD, owner.getCoordinate());
        assertEquals("entryId 取 entry 真实主键", "ent-1", owner.getEntryId());
        assertEquals("deviceId 取 core 铸造稳定 UUID", device.getId(), owner.getDeviceId());
        assertNotEquals("四层身份模型下两值不同（折叠已拆）", owner.getEntryId(), owner.getDeviceId());
        assertEquals(Usage.DIRECT, owner.getUsage());
        assertNull(owner.getRawIdentity());
    }

    @Test
    public void ofGatewayChildFoldsToGatewayEntry() {
        // 网关子设备无自身 entry，复用网关 entry 作 1:N back-ref——entryId 折叠到网关 entry
        ConfigEntry gatewayEntry = entry("gw-1", COORD);
        Map<String, Object> childConfig = new HashMap<>();
        childConfig.put("name", "网关子设备1");
        StubDevice child = new StubDevice(gatewayEntry, "child-sn-1", childConfig);

        ResourceOwner owner = ResourceOwner.of(child);

        assertEquals("网关子设备 entryId 取网关 entry 主键（折叠语义）", "gw-1", owner.getEntryId());
        assertEquals("deviceId 取子设备自身铸造 UUID", child.getId(), owner.getDeviceId());
        assertNotEquals(owner.getEntryId(), owner.getDeviceId());
        // 同网关第二台子设备：entryId 相同、deviceId 不同——1:N 各立账目
        StubDevice child2 = new StubDevice(gatewayEntry, "child-sn-2", childConfig);
        ResourceOwner owner2 = ResourceOwner.of(child2);
        assertEquals(owner.getEntryId(), owner2.getEntryId());
        assertNotEquals(owner.getDeviceId(), owner2.getDeviceId());
    }

    @Test
    public void ofDeviceHostWithoutEntryIdFailsFastNamingDeprecatedConstructor() {
        // entry 缺 entryId（@Deprecated Map 构造器的空 ConfigEntry 同路径）：fail-fast 点名，不猜
        StubDevice bare = new StubDevice(entry(null, COORD));
        try {
            ResourceOwner.of(bare);
            fail("entry 缺 entryId 必须 fail-fast");
        } catch (IllegalArgumentException expected) {
            assertTrue("异常消息点名 entryId 与废弃构造路径：" + expected.getMessage(),
                    expected.getMessage().contains("entryId"));
        }
    }

    @Test
    public void ofIntegrationHostDerivesCoordinateLevel() {
        ResourceOwner owner = ResourceOwner.of(new StubIntegration(COORD));
        assertEquals(OwnerLevel.INTEGRATION, owner.getLevel());
        assertEquals(COORD, owner.getCoordinate());
        assertNull(owner.getEntryId());
        assertNull(owner.getDeviceId());
    }

    @Test
    public void ofUnknownHostImplementationThrowsExplicitly() {
        try {
            ResourceOwner.of(action -> {
            });
            fail("未知宿主实现必须明确异常（严格模式不猜）");
        } catch (IllegalArgumentException expected) {
            // 消息嵌入宿主类名（lambda 运行时类名含外围测试类名）供调用方自诊断
            assertTrue("异常消息含宿主类名：" + expected.getMessage(),
                    expected.getMessage().contains("ResourceOwnerTest"));
        }
    }

    @Test
    public void ofNullHostThrowsNpeImmediately() {
        try {
            ResourceOwner.of(null);
            fail("null 宿主必须立即 NPE（requireNonNull）");
        } catch (NullPointerException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // ========== 构造校验矩阵（合法全过 / 每层典型非法各一） ==========

    @Test
    public void legalFormsPerLevelPass() {
        assertEquals(OwnerLevel.INTEGRATION, ResourceOwner.integration(COORD).getLevel());
        assertEquals(OwnerLevel.ENTRY, ResourceOwner.entry(COORD, "ent-1").getLevel());
        assertEquals(OwnerLevel.DEVICE, ResourceOwner.device(COORD, "ent-1", "dev-1").getLevel());
        assertEquals(OwnerLevel.LEGACY, ResourceOwner.legacy("SaimosenDevice-e1").getLevel());
        assertEquals("legacy 字段形态：结构字段全 null、串原样", "SaimosenDevice-e1",
                ResourceOwner.legacy("SaimosenDevice-e1").getRawIdentity());
        // device 带 host 变体（展示宿主，DeviceBase 或 null）
        StubDevice device = new StubDevice(entry("ent-2", COORD));
        ResourceOwner withHost = ResourceOwner.device(COORD, "ent-2", device.getId(), device);
        assertEquals(OwnerLevel.DEVICE, withHost.getLevel());
    }

    @Test
    public void illegalFormsPerLevelFailFast() {
        // INTEGRATION 层带 entryId / deviceId
        expectIllegal("INTEGRATION 层 entryId 必为 null",
                () -> new ResourceOwner(OwnerLevel.INTEGRATION, COORD, "ent-1", null, Usage.DIRECT, null, null));
        expectIllegal("INTEGRATION 层 deviceId 必为 null",
                () -> new ResourceOwner(OwnerLevel.INTEGRATION, COORD, null, "dev-1", Usage.DIRECT, null, null));
        // ENTRY 层带 deviceId / 缺 entryId / 缺 coordinate
        expectIllegal("ENTRY 层 deviceId 必为 null",
                () -> new ResourceOwner(OwnerLevel.ENTRY, COORD, "ent-1", "dev-1", Usage.DIRECT, null, null));
        expectIllegal("ENTRY 层 entryId 必填",
                () -> new ResourceOwner(OwnerLevel.ENTRY, COORD, null, null, Usage.DIRECT, null, null));
        expectIllegal("ENTRY 层 coordinate 必填",
                () -> new ResourceOwner(OwnerLevel.ENTRY, null, "ent-1", null, Usage.DIRECT, null, null));
        // DEVICE 层缺 entryId / 缺 deviceId
        expectIllegal("DEVICE 层 entryId 必填",
                () -> new ResourceOwner(OwnerLevel.DEVICE, COORD, null, "dev-1", Usage.DIRECT, null, null));
        expectIllegal("DEVICE 层 deviceId 必填",
                () -> new ResourceOwner(OwnerLevel.DEVICE, COORD, "ent-1", null, Usage.DIRECT, null, null));
        // LEGACY 层带结构字段（coordinate/entryId/deviceId 任一）/ 缺 rawIdentity
        expectIllegal("LEGACY 层 coordinate 必为 null",
                () -> new ResourceOwner(OwnerLevel.LEGACY, COORD, null, null, Usage.DIRECT, "raw-1", null));
        expectIllegal("LEGACY 层 entryId 必为 null",
                () -> new ResourceOwner(OwnerLevel.LEGACY, null, "ent-1", null, Usage.DIRECT, "raw-1", null));
        expectIllegal("LEGACY 层 deviceId 必为 null",
                () -> new ResourceOwner(OwnerLevel.LEGACY, null, null, "dev-1", Usage.DIRECT, "raw-1", null));
        expectIllegal("LEGACY 层 rawIdentity 必填",
                () -> new ResourceOwner(OwnerLevel.LEGACY, null, null, null, Usage.DIRECT, null, null));
        expectIllegal("LEGACY 层 rawIdentity 空串非法",
                () -> new ResourceOwner(OwnerLevel.LEGACY, null, null, null, Usage.DIRECT, "", null));
        // 非 LEGACY 层带 rawIdentity
        expectIllegal("非 LEGACY 层 rawIdentity 必为 null",
                () -> new ResourceOwner(OwnerLevel.DEVICE, COORD, "ent-1", "dev-1", Usage.DIRECT, "raw-1", null));
        // DEVICE 层错型宿主（mdcEntries 把 host 当 DeviceBase 现取名，错型必须构造期暴露）
        expectIllegal("DEVICE 层 host 须为 DeviceBase",
                () -> new ResourceOwner(OwnerLevel.DEVICE, COORD, "ent-1", "dev-1", Usage.DIRECT, null,
                        action -> {
                        }));
    }

    private static void expectIllegal(String message, Runnable construction) {
        try {
            construction.run();
            fail(message);
        } catch (IllegalArgumentException expected) {
            // fail-fast 达成（消息见各构造点）
        }
    }

    // ========== mdcEntries 四层派发矩阵 ==========

    @Test
    public void mdcEntriesDispatchMatrixPerLevel() {
        // DEVICE 层带宿主：设备三键（name 现取）
        StubDevice device = new StubDevice(entryWithName("ent-1", COORD, "一号设备"));
        Map<String, String> deviceEntries = ResourceOwner.of(device).mdcEntries();
        assertEquals(keys(MdcContext.DEVICE_ID_KEY, MdcContext.DEVICE_NAME_KEY,
                MdcContext.INTEGRATION_COORDINATE_KEY), deviceEntries.keySet());
        assertEquals(device.getId(), deviceEntries.get(MdcContext.DEVICE_ID_KEY));
        assertEquals("一号设备", deviceEntries.get(MdcContext.DEVICE_NAME_KEY));
        assertEquals(COORD, deviceEntries.get(MdcContext.INTEGRATION_COORDINATE_KEY));

        // DEVICE 层无宿主（字段构造路径）：name 如实缺省，不伪造
        Map<String, String> hostless = ResourceOwner.device(COORD, "ent-1", "dev-1").mdcEntries();
        assertEquals(keys(MdcContext.DEVICE_ID_KEY, MdcContext.INTEGRATION_COORDINATE_KEY), hostless.keySet());

        // ENTRY 层：entry 键 + coordinate
        Map<String, String> entryEntries = ResourceOwner.entry(COORD, "ent-1").mdcEntries();
        assertEquals(keys(MdcContext.ENTRY_ID_KEY, MdcContext.INTEGRATION_COORDINATE_KEY),
                entryEntries.keySet());
        assertEquals("ent-1", entryEntries.get(MdcContext.ENTRY_ID_KEY));

        // INTEGRATION 层：仅 coordinate
        Map<String, String> integrationEntries = ResourceOwner.integration(COORD).mdcEntries();
        assertEquals(keys(MdcContext.INTEGRATION_COORDINATE_KEY), integrationEntries.keySet());

        // LEGACY 层：rawIdentity 键
        Map<String, String> legacyEntries = ResourceOwner.legacy("ThermoDevice-e1").mdcEntries();
        assertEquals(keys(MdcContext.OWNER_RAW_IDENTITY_KEY), legacyEntries.keySet());
        assertEquals("ThermoDevice-e1", legacyEntries.get(MdcContext.OWNER_RAW_IDENTITY_KEY));
    }

    private static Set<String> keys(String... expected) {
        Set<String> set = new HashSet<>();
        for (String k : expected) {
            set.add(k);
        }
        return set;
    }

    @Test
    public void deviceNameIsLiveFromHostNotSnapshot() {
        StubDevice device = new StubDevice(entryWithName("ent-1", COORD, "旧名"));
        ResourceOwner owner = ResourceOwner.of(device);
        assertEquals("旧名", owner.mdcEntries().get(MdcContext.DEVICE_NAME_KEY));

        device.rename("新名"); // 设备改名后

        assertEquals("name 从 host 现取，改名实时反映（非注册期快照）",
                "新名", owner.mdcEntries().get(MdcContext.DEVICE_NAME_KEY));
    }

    // ========== asAdapter / ownerKey / 等值 ==========

    @Test
    public void asAdapterMarksUsageAndPreservesIdentityPlusHost() {
        StubDevice device = new StubDevice(entryWithName("ent-1", COORD, "一号设备"));
        ResourceOwner direct = ResourceOwner.of(device);
        ResourceOwner adapter = direct.asAdapter();

        assertEquals(Usage.ADAPTER, adapter.getUsage());
        assertEquals(direct.getCoordinate(), adapter.getCoordinate());
        assertEquals(direct.getEntryId(), adapter.getEntryId());
        assertEquals(direct.getDeviceId(), adapter.getDeviceId());
        assertEquals("host 引用原样（设备名现取继续可用）",
                "一号设备", adapter.mdcEntries().get(MdcContext.DEVICE_NAME_KEY));
        assertNotEquals("DIRECT 与 ADAPTER 是不同账目条目", direct, adapter);
        assertSame("已 ADAPTER 再转发幂等返回自身", adapter, adapter.asAdapter());
    }

    @Test
    public void ownerKeyAndEqualsIdentityOnlyHostExcluded() {
        StubDevice deviceA = new StubDevice(entry("ent-1", COORD));
        StubDevice deviceB = new StubDevice(entry("ent-1", COORD));
        ResourceOwner a = ResourceOwner.of(deviceA);
        // 同身份（coordinate/entryId/deviceId 同）、不同宿主对象：字段构造 + 换 deviceB 当展示宿主
        ResourceOwner b = ResourceOwner.device(COORD, "ent-1", deviceA.getId(), deviceB);

        assertEquals("同身份不同宿主实例等值（host 不入 equals）", a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals("同身份同 ownerKey（账本 Map 键稳定）", a.ownerKey(), b.ownerKey());

        // 反证：两台不同设备（各自铸造 UUID 不同）即不同身份、不同键
        ResourceOwner other = ResourceOwner.of(deviceB);
        assertNotEquals("不同 deviceId 即不同身份", a, other);
        assertNotEquals(a.ownerKey(), other.ownerKey());

        // LEGACY 区分度唯一来自 rawIdentity：同口多集成共线（常态）不得撞键
        assertNotEquals(ResourceOwner.legacy("AoganDevice-e1").ownerKey(),
                ResourceOwner.legacy("SaimosenDevice-e2").ownerKey());
        assertEquals("同串 LEGACY 同键", ResourceOwner.legacy("AoganDevice-e1").ownerKey(),
                ResourceOwner.legacy("AoganDevice-e1").ownerKey());

        // usage 入键：同一设备 DIRECT/ADAPTER 两笔条目可共存
        assertNotEquals(a.ownerKey(), a.asAdapter().ownerKey());

        assertFalse(a.equals(ResourceOwner.device(COORD, "ent-1", "other-device")));
        assertFalse(a.equals(null));
    }

    // ========== host 引用不构成静态钉扎（记账法） ==========

    @Test
    public void droppedOwnerReleasesHostNoStaticPinning() {
        StubDevice device = new StubDevice(entry("ent-1", COORD));
        WeakReference<StubDevice> hostRef = new WeakReference<>(device);

        ResourceOwner owner = ResourceOwner.of(device);
        assertNotNull(hostRef.get());
        owner.mdcEntries(); // 走一遍派发路径再丢

        owner = null;
        device = null;
        // 记账法：ResourceOwner 只持 owner→host 方向引用，无静态注册表；owner 丢弃后宿主可回收。
        // 有限轮 GC 促收（分配压力 + System.gc），不用 sleep 轮询。
        boolean cleared = false;
        for (int i = 0; i < 50 && !cleared; i++) {
            byte[] pressure = new byte[256 * 1024];
            pressure[0] = 1;
            System.gc();
            cleared = hostRef.get() == null;
        }
        assertTrue("owner 丢弃后宿主须可回收（无静态钉扎）", cleared);
    }
}
