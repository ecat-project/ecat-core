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

package com.ecat.core.Integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Log.ClassLoaderCoordinateFilter;

/**
 * onRelease final 模板的结构测试（bug-record-20260828-171500 根治）：
 * 子类只覆写 {@code onReleaseImpl()}/{@code onDeviceReleaseImpl()} 钩子、零 super 调用，
 * 框架收尾必达——「覆写漏 super」在签名层面不可能。
 *
 * <p>锁定三点：① 模板顺序 钩子 → 基类收尾 → sweep（设备层为 钩子 → 设备收尾 → 基类收尾）；
 * ② 钩子抛异常时异常上抛不吞，但收尾与 sweep 经 finally 必达；③ 入口/设备层钩子的
 * final 修饰本身不被意外拆除（反射断言，防结构回退）。
 *
 * <p>顺序观测用 {@link ClassLoaderCoordinateFilter} 注册计数做基类收尾的可见侧效
 * （onRelease 的 finally 注销本类包名前缀），与 IntegrationBaseRemovalHostTest 同一手法。</p>
 */
public class IntegrationOnReleaseTemplateTest {

    @Before
    public void unregisterOwnPackagePrefixBefore() {
        // static 注册表全 JVM 共享且 register 是 put 覆盖语义（同 key 不增计数）：单 fork
        // 顺序跑全模块测试时，先跑的类若残留同包前缀（本包所有集成夹具共用
        // com.ecat.core.Integration），before/before+1 计数算术即被击穿；类顺序=目录枚举
        // 顺序（Windows/Linux 不同）→ 预清本类要用的 key 恢复确定性（bug-record-20260831-115342）
        ClassLoaderCoordinateFilter.unregisterPackagePrefix(getClass().getPackage().getName());
    }

    @After
    public void unregisterOwnPackagePrefixAfter() {
        // 用例中途失败时不向后续类泄漏本包前缀注册
        ClassLoaderCoordinateFilter.unregisterPackagePrefix(getClass().getPackage().getName());
    }

    /** 基类层替身：只覆写钩子（结构性证明——零 super 调用），记录钩子执行瞬间的收尾进度。 */
    static class HookIntegration extends IntegrationBase {
        final AtomicInteger countDuringHook = new AtomicInteger(-1);
        final AtomicInteger countAtSweep = new AtomicInteger(-1);

        @Override public void onInit() { }
        @Override public void onStart() { }
        @Override public void onPause() { }

        @Override
        protected void onReleaseImpl() {
            countDuringHook.set(ClassLoaderCoordinateFilter.getRegisteredPackagePrefixCount());
            onRemove(() -> countAtSweep.set(ClassLoaderCoordinateFilter.getRegisteredPackagePrefixCount()));
        }
    }

    /** 基类层替身：钩子崩溃——异常上抛（不吞）但收尾与 sweep 必达（finally 语义）。 */
    static class ThrowingHookIntegration extends IntegrationBase {
        final AtomicBoolean sweepRan = new AtomicBoolean(false);

        @Override public void onInit() { }
        @Override public void onStart() { }
        @Override public void onPause() { }

        @Override
        protected void onReleaseImpl() {
            onRemove(() -> sweepRan.set(true));
            throw new IllegalStateException("hook-boom");
        }
    }

    /** ① 模板顺序：钩子（自有清理）→ 基类收尾（注销包名前缀）→ sweep（尾部）。 */
    @Test(timeout = 15000)
    public void baseTier_hookBeforeBaseCleanup_sweepAfterCleanup() {
        String prefix = HookIntegration.class.getPackage().getName();
        int before = ClassLoaderCoordinateFilter.getRegisteredPackagePrefixCount();
        ClassLoaderCoordinateFilter.registerPackagePrefix(prefix, "com.ecat:test-tpl-order");

        HookIntegration integration = new HookIntegration();

        integration.onRelease();

        assertEquals("钩子应先于基类收尾（执行时前缀尚未注销）", before + 1,
                integration.countDuringHook.get());
        assertEquals("基类收尾应先于 sweep（动作执行时前缀已注销）", before,
                integration.countAtSweep.get());
        assertEquals("onRelease 后前缀应被基类收尾注销", before,
                ClassLoaderCoordinateFilter.getRegisteredPackagePrefixCount());
    }

    /** ② 基类层 finally 语义：钩子异常上抛不吞，基类收尾与 sweep 仍必达。 */
    @Test(timeout = 15000)
    public void baseTier_hookThrow_stillCleansUpAndSweeps() {
        String prefix = ThrowingHookIntegration.class.getPackage().getName();
        int before = ClassLoaderCoordinateFilter.getRegisteredPackagePrefixCount();
        ClassLoaderCoordinateFilter.registerPackagePrefix(prefix, "com.ecat:test-tpl-throw");

        ThrowingHookIntegration integration = new ThrowingHookIntegration();

        try {
            integration.onRelease();
            fail("钩子异常应照原语义上抛（模板不吞）");
        } catch (IllegalStateException expected) {
            assertEquals("hook-boom", expected.getMessage());
        }

        assertTrue("钩子崩溃后 sweep 仍必达", integration.sweepRan.get());
        assertEquals("钩子崩溃后基类收尾仍必达（前缀已注销）", before,
                ClassLoaderCoordinateFilter.getRegisteredPackagePrefixCount());
    }

    /** ②' 设备层 finally 语义：设备钩子先跑，钩子崩溃时设备收尾（release+清映射）仍必达且异常上抛。 */
    @Test(timeout = 15000)
    public void deviceTier_hookThrow_stillSweepsDevices() {
        final List<String> journal = new CopyOnWriteArrayList<>();
        IntegrationDeviceBase integration = new IntegrationDeviceBase() {
            @Override
            protected DeviceBase createDeviceFromEntry(ConfigEntry entry) {
                return null;
            }

            @Override
            protected void onDeviceReleaseImpl() {
                journal.add("hook");
                throw new IllegalStateException("device-hook-boom");
            }
        };
        DeviceBase device = new DeviceBase(newEntry()) {
            @Override public void init() { }
            @Override public void start() { }
            @Override public void stop() { }
            @Override public void release() { journal.add("device-release:" + getId()); }
        };
        integration.devices.put(device.getId(), device);

        try {
            integration.onRelease();
            fail("设备层钩子异常应上抛（模板不吞）");
        } catch (IllegalStateException expected) {
            assertEquals("device-hook-boom", expected.getMessage());
        }

        assertEquals("钩子先于设备收尾，设备收尾在钩子崩溃后必达", journal,
                Arrays.asList("hook", "device-release:" + device.getId()));
        assertTrue("设备映射应被设备收尾清空", integration.devices.isEmpty());
    }

    /** ③ 结构防回退：入口与设备层钩子的 final 修饰不得被拆除（拆除即 reopening 漏 super 缺口）。 */
    @Test(timeout = 15000)
    public void templateMethods_areFinal() throws Exception {
        Method entry = IntegrationBase.class.getMethod("onRelease");
        assertTrue("IntegrationBase.onRelease 必须 final（模板入口）",
                Modifier.isFinal(entry.getModifiers()));
        Method deviceHook = IntegrationDeviceBase.class
                .getDeclaredMethod("onReleaseImpl");
        assertTrue("IntegrationDeviceBase.onReleaseImpl 必须 final（防设备收尾被子类钩子遮蔽）",
                Modifier.isFinal(deviceHook.getModifiers()));
    }

    private static ConfigEntry newEntry() {
        ConfigEntry e = new ConfigEntry();
        e.setEntryId("entry-tpl");
        e.setUniqueId("u-tpl");
        e.setCoordinate("com.ecat:test-tpl");
        Map<String, Object> data = new HashMap<>();
        data.put("name", "n-tpl");
        e.setData(data);
        return e;
    }
}
