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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ecat.core.Log.ClassLoaderCoordinateFilter;

/**
 * IntegrationBase 的 RemovalHost 宿主实现自测：onRemove LIFO /
 * 单条动作失败不中断 sweep / sweep 在既有 onRelease 清理之后（尾部）/ 已 sweep 后
 * 注册抛 REE / null 动作拒绝。
 *
 * <p>顺序观测用 ClassLoaderCoordinateFilter 注册计数做既有清理的可见侧效：
 * sweep 动作执行时该计数应已回落（unregisterPackagePrefix 先于移除动作发生）。
 */
public class IntegrationBaseRemovalHostTest {

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

    /** 最小集成替身（IntegrationBase 未实现 onInit/onStart/onPause，测试补空实现）。 */
    static class SweepIntegration extends IntegrationBase {
        @Override public void onInit() { }
        @Override public void onStart() { }
        @Override public void onPause() { }
    }

    /** ① onRemove LIFO：后注册先执行（依赖反向于创建顺序，与 DeviceBase 同构）。 */
    @Test(timeout = 15000)
    public void onRemoveActions_runLifoOnRelease() {
        SweepIntegration integration = new SweepIntegration();
        List<String> order = new CopyOnWriteArrayList<>();
        integration.onRemove(() -> order.add("first"));
        integration.onRemove(() -> order.add("second"));
        integration.onRemove(() -> order.add("third"));

        integration.onRelease();

        assertEquals("移除动作应 LIFO 执行", Arrays.asList("third", "second", "first"), order);
    }

    /** ② 单条动作失败不中断 sweep：异常记 warn 跳过，其余动作照常执行。 */
    @Test(timeout = 15000)
    public void failingAction_skippedSweepContinues() {
        SweepIntegration integration = new SweepIntegration();
        List<String> order = new CopyOnWriteArrayList<>();
        integration.onRemove(() -> order.add("first"));
        integration.onRemove(() -> {
            throw new IllegalStateException("拆卸动作失败（应记 warn 跳过）");
        });
        integration.onRemove(() -> order.add("third"));

        integration.onRelease();   // 不因单条失败上抛

        assertEquals("失败动作跳过，其余照常（LIFO 序）", Arrays.asList("third", "first"), order);

        // 幂等：deque 已排空，二次 onRelease 不再执行任何动作
        integration.onRelease();
        assertEquals("sweep 幂等（重复 release 空转）", Arrays.asList("third", "first"), order);
    }

    /** ③ sweep 在既有 onRelease 清理之后：动作执行时包名前缀注销已发生（计数回落）。 */
    @Test(timeout = 15000)
    public void sweepRuns_afterExistingOnReleaseCleanup() {
        String packagePrefix = SweepIntegration.class.getPackage().getName();
        int before = ClassLoaderCoordinateFilter.getRegisteredPackagePrefixCount();
        ClassLoaderCoordinateFilter.registerPackagePrefix(packagePrefix, "com.ecat:test-sweep-order");
        int afterRegister = ClassLoaderCoordinateFilter.getRegisteredPackagePrefixCount();

        SweepIntegration integration = new SweepIntegration();
        AtomicInteger countAtSweep = new AtomicInteger(-1);
        integration.onRemove(() ->
                countAtSweep.set(ClassLoaderCoordinateFilter.getRegisteredPackagePrefixCount()));

        integration.onRelease();

        assertEquals("既有清理（注销包名前缀）应先于移除动作执行", before, countAtSweep.get());
        assertEquals("onRelease 后注册的包名前缀应被既有清理注销", before,
                ClassLoaderCoordinateFilter.getRegisteredPackagePrefixCount());
        assertTrue(afterRegister == before + 1);
    }

    /** ④ 已 sweep 后注册抛 REE：release 后建池/注册属病态调用，严格模式不静默收下。 */
    @Test(timeout = 15000)
    public void onRemoveAfterRelease_rejectedWithRee() {
        SweepIntegration integration = new SweepIntegration();
        integration.onRelease();
        try {
            integration.onRemove(() -> { });
            fail("已 release 后注册移除动作应抛 RejectedExecutionException");
        } catch (RejectedExecutionException expected) {
            // 防「release 后才注册」的静默泄漏
        }
    }

    /** null 动作拒绝：无动作可注册（调用错误非边界）。 */
    @Test(timeout = 15000)
    public void onRemoveNull_rejected() {
        SweepIntegration integration = new SweepIntegration();
        try {
            integration.onRemove(null);
            fail("onRemove(null) 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 镜像 DeviceBase.onRemove 的 null 拒绝
        }
    }
}
