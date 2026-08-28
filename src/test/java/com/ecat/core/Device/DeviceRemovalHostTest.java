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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.junit.Before;
import org.junit.Test;

import com.ecat.core.ConfigEntry.ConfigEntry;

/**
 * RemovalHost 结构化生命周期测试（18 号设计 §3.3）：
 * DeviceBase.onRemove 注册移除动作（deque LIFO），cancelManagedTasks LIFO 执行
 * 全部移除动作（逐条隔离）——SDK 轮询句柄等注册资源的框架兜底拆卸面。
 *
 * <p>框架 chokepoint 路径（removeEntry/disableEntry/onPause/onRelease）的
 * IntegrationDeviceBase 侧覆盖见 IntegrationDeviceBaseRemovalActionsTest。</p>
 */
public class DeviceRemovalHostTest {

    private DeviceBase device;

    @Before
    public void setUp() {
        device = newDevice();
    }

    private DeviceBase newDevice() {
        ConfigEntry entry = new ConfigEntry();
        entry.setUniqueId("removal-host-u");
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", "removal-host-test-device");
        entry.setData(data);
        return new DeviceBase(entry) {
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
        };
    }

    /** LIFO：后注册的移除动作先执行（依赖反向于创建顺序，拆卸序=创建序的逆）。 */
    @Test
    public void removalActions_executeInLifoOrder() {
        List<Integer> order = new ArrayList<>();
        device.onRemove(() -> order.add(1));
        device.onRemove(() -> order.add(2));
        device.onRemove(() -> order.add(3));

        device.cancelManagedTasks();

        assertThat("移除动作必须 LIFO 执行（3→2→1）", order, is(java.util.Arrays.asList(3, 2, 1)));
    }

    /** 逐条隔离：某条移除动作抛异常不中断 sweep，其余动作照常执行（清理不容半途而废）。 */
    @Test
    public void failingAction_isolated_remainingActionsStillRun() {
        List<Integer> order = new ArrayList<>();
        device.onRemove(() -> order.add(1));
        device.onRemove(() -> { throw new IllegalStateException("动作自身缺陷"); });
        device.onRemove(() -> order.add(3));

        device.cancelManagedTasks();

        assertThat("抛异常动作之后注册的动作（LIFO 先执行）照常执行，异常被隔离",
            order, is(java.util.Arrays.asList(3, 1)));
    }

    /** stop 后注册 reject：严格模式抛 RejectedExecutionException（防静默泄漏）。 */
    @Test
    public void afterSweep_registrationRejected() {
        device.cancelManagedTasks();
        try {
            device.onRemove(() -> { });
            fail("设备已 stop 后注册移除动作必须抛 RejectedExecutionException，不得静默收下");
        } catch (RejectedExecutionException expected) {
            // 病态调用显式暴露
        }
    }

    /** onRemove(null) 拒绝：无动作可注册，语义不明不允许。 */
    @Test
    public void onRemoveNull_rejected() {
        try {
            device.onRemove(null);
            fail("onRemove(null) 必须抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 与 bind(null) 等同型严格守卫
        }
    }

    /** 二次 sweep 幂等：移除动作不重复执行（deque 已清空）。 */
    @Test
    public void secondSweep_idempotent_actionsNotReExecuted() {
        List<Integer> order = new ArrayList<>();
        device.onRemove(() -> order.add(1));
        device.cancelManagedTasks();
        device.cancelManagedTasks();   // 二次 sweep（IntegrationDeviceBase 幂等补调路径）
        assertThat("移除动作恰执行一次，二次 sweep 空转", order, is(java.util.Arrays.asList(1)));
    }
}
