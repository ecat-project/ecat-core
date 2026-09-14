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

package com.ecat.core.Utils.Mdc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.CommTrace.CommTraceBuffer;
import com.ecat.core.CommTrace.ResourceOwner;
import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Device.RemovalHost;
import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationLoadOption;

/**
 * 设备维度 MDC 上下文 scope 测试：注入键集合/异常安全恢复/非设备宿主 no-op。
 *
 * <p>风险锁点：scope 漏恢复会把设备键泄漏到起链线程（后续无关帧误归属）；
 * null 值直接 MDC.put 会抛异常。均为确定性断言，无并发等待。
 */
public class DeviceMdcContextTest {

    private ConfigEntry entry;
    private DeviceBase device;

    @Before
    public void setUp() {
        entry = new ConfigEntry();
        Map<String, Object> config = new HashMap<>();
        config.put("name", "测试设备-01");
        device = new FixtureDevice(entry, "fixture-unique-id", config);
        assertNotNull("id 在构造期即铸造", device.getId());
    }

    @Before
    @After
    public void cleanMdc() {
        MDC.clear();
    }

    @Test
    public void scopePutsDeviceIdNameAndCoordinate() {
        entry.setCoordinate("com.ecat:integration-demo");
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scope(device)) {
            assertEquals("device.id 注入", device.getId(), MDC.get(CommTraceBuffer.MDC_DEVICE_ID_KEY));
            assertEquals("device.name 注入", "测试设备-01", MDC.get(CommTraceBuffer.MDC_DEVICE_NAME_KEY));
            assertEquals("coordinate 随设备注入", "com.ecat:integration-demo",
                    MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY));
        }
    }

    @Test
    public void scopeRestoresPreviousMdcOnClose() {
        MDC.put("traceId", "before-scope");
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scope(device)) {
            assertEquals(device.getId(), MDC.get(CommTraceBuffer.MDC_DEVICE_ID_KEY));
        }
        assertNull("close 后设备键清除", MDC.get(CommTraceBuffer.MDC_DEVICE_ID_KEY));
        assertNull(MDC.get(CommTraceBuffer.MDC_DEVICE_NAME_KEY));
        assertEquals("close 后恢复先前置", "before-scope", MDC.get("traceId"));
    }

    @Test
    public void scopeRestoresPreviousMdcWhenBodyThrows() {
        MDC.put("traceId", "before-throw");
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scope(device)) {
            throw new IllegalStateException("起链中途异常");
        } catch (IllegalStateException expected) {
            // try-with-resources 必须先执行 close——设备键不得因异常残留在起链线程
        }
        assertNull("异常路径同样清除设备键", MDC.get(CommTraceBuffer.MDC_DEVICE_ID_KEY));
        assertEquals("异常路径恢复先前置", "before-throw", MDC.get("traceId"));
    }

    @Test
    public void scopeToleratesMissingNameAndCoordinate() {
        DeviceBase bareDevice = new FixtureDevice(new ConfigEntry(), "bare-unique-id", new HashMap<>());
        assertNull(bareDevice.getName());
        assertNull(bareDevice.getCoordinate());
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scope(bareDevice)) {
            assertEquals("id 恒有（core 构造铸造）", bareDevice.getId(),
                    MDC.get(CommTraceBuffer.MDC_DEVICE_ID_KEY));
            assertNull("name 缺失不 put（MDC 不接受 null 值）", MDC.get(CommTraceBuffer.MDC_DEVICE_NAME_KEY));
            assertNull("coordinate 缺失不 put", MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY));
        }
    }

    @Test
    public void scopeOfNonDeviceHostIsNoOp() {
        RemovalHost plainHost = action -> {
        };
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scopeOf(plainHost)) {
            assertNull("非设备宿主（测试假宿主）不注入任何键",
                    MDC.get(CommTraceBuffer.MDC_DEVICE_ID_KEY));
        }
    }

    @Test
    public void scopeOfDeviceHostDelegatesToScope() {
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scopeOf(device)) {
            assertEquals(device.getId(), MDC.get(CommTraceBuffer.MDC_DEVICE_ID_KEY));
        }
    }

    // ========== scopeOf(ResourceOwner)：权威注入源（D1'，键集整体取 mdcEntries 派发） ==========

    @Test
    public void scopeOfOwnerInjectsKeysetPerLevel() {
        // DEVICE 层：三键整体注入（id/name/coordinate 同刻同源）
        Map<String, Object> config = new HashMap<>();
        config.put("name", "测试设备-01");
        ConfigEntry namedEntry = new ConfigEntry();
        namedEntry.setEntryId("ent-1");
        namedEntry.setCoordinate("com.ecat:integration-demo");
        DeviceBase namedDevice = new FixtureDevice(namedEntry, "fixture-unique-id", config);
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scopeOf(ResourceOwner.of(namedDevice))) {
            assertEquals(namedDevice.getId(), MDC.get(MdcContext.DEVICE_ID_KEY));
            assertEquals("测试设备-01", MDC.get(MdcContext.DEVICE_NAME_KEY));
            assertEquals("com.ecat:integration-demo", MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY));
        }

        // ENTRY 层：entry.id + coordinate
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scopeOf(
                ResourceOwner.entry("com.ecat:integration-demo", "ent-1"))) {
            assertEquals("ent-1", MDC.get(MdcContext.ENTRY_ID_KEY));
            assertEquals("com.ecat:integration-demo", MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY));
            assertNull("ENTRY 层不注入设备键", MDC.get(MdcContext.DEVICE_ID_KEY));
        }

        // INTEGRATION 层：仅 coordinate
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scopeOf(
                ResourceOwner.integration("com.ecat:integration-demo"))) {
            assertEquals("com.ecat:integration-demo", MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY));
            assertNull(MDC.get(MdcContext.DEVICE_ID_KEY));
            assertNull(MDC.get(MdcContext.ENTRY_ID_KEY));
        }

        // LEGACY 层：rawIdentity 键（旧签名自由串，不伪造结构身份）
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scopeOf(ResourceOwner.legacy("SaimosenDevice-e1"))) {
            assertEquals("SaimosenDevice-e1", MDC.get(MdcContext.OWNER_RAW_IDENTITY_KEY));
            assertNull("LEGACY 无结构身份可注入", MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY));
        }
    }

    @Test
    public void scopeOfNullOwnerIsNoOp() {
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scopeOf((ResourceOwner) null)) {
            assertNull("null owner 防御显式 no-op", MDC.get(MdcContext.DEVICE_ID_KEY));
        }
    }

    @Test
    public void scopeOfOwnerRestoresPreviousMdcOnClose() {
        MDC.put("traceId", "before-owner-scope");
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scopeOf(
                ResourceOwner.entry("com.ecat:integration-demo", "ent-1"))) {
            assertEquals("ent-1", MDC.get(MdcContext.ENTRY_ID_KEY));
        }
        assertNull("close 后 owner 键清除（复用线程不得残留）", MDC.get(MdcContext.ENTRY_ID_KEY));
        assertEquals("close 后恢复先前置", "before-owner-scope", MDC.get("traceId"));
    }

    // ========== scopeOf(RemovalHost) 宿主路径：IntegrationBase 升级收益 ==========

    @Test
    public void scopeOfIntegrationHostInjectsCoordinate() {
        StubIntegration integration = new StubIntegration("com.ecat:integration-demo");
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scopeOf((RemovalHost) integration)) {
            assertEquals("集成宿主（推送/上报链路）注入 coordinate",
                    "com.ecat:integration-demo", MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY));
            assertNull("不注入设备键", MDC.get(MdcContext.DEVICE_ID_KEY));
        }
    }

    @Test
    public void scopeOfIntegrationHostWithoutCoordinateIsNoOp() {
        StubIntegration bare = new StubIntegration(null);
        try (DeviceMdcContext.Scope scope = DeviceMdcContext.scopeOf((RemovalHost) bare)) {
            assertNull("onLoad 前无 coordinate：容忍不注入（MDC 路径不适用账本级严格失败）",
                    MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY));
        }
    }

    /** 最小设备桩：网关子设备构造形态（entry + 自备 config），不碰生命周期。 */
    private static final class FixtureDevice extends DeviceBase {
        FixtureDevice(ConfigEntry gatewayEntry, String uniqueId, Map<String, Object> config) {
            super(gatewayEntry, uniqueId, config);
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

    /** 最小集成桩：getCoordinate 不依赖 loadOption（未 onLoad 恒 null），支持 null 形态。 */
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
}
