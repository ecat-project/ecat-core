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

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.EcatCore;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.StateManager;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异常传播策略回归：发布路径上的异常「不吞」——既不把就绪门禁的 IllegalStateException 静默成 return false，
 * 也不把持久化层系统级故障（磁盘/DB/序列化）吞成日志噪声。
 *
 * <p>背景：原 {@link DeviceBase#publicAttrsState()} 的 {@code catch(Exception)} 把门禁
 * {@link IllegalStateException}（设备未 READY 即 publish 的设计缺陷）吞成 {@code log.error + return false}，
 * 掩盖了设计问题；{@link AttributeBase#publicState()} 内的 saveState 落盘失败被内层 catch 静默吞。
 * 本类锁定两条「不吞」契约：
 * <ol>
 *   <li>publicAttrsState 预 ready 期把门禁异常<b>原样冒泡</b>（不再 catch 成 return false）；</li>
 *   <li>publicState 持久化失败<b>抛出带属性/device 上下文</b>的 RuntimeException（不再 log 吞）。</li>
 * </ol>
 *
 * <p>注：发布总线失败（publish）的 catch→return false 是可重试契约，属已知项，本类不覆盖。
 */
public class PublicStateExceptionPropagationTest {

    /**
     * publicAttrsState 预 ready：原 catch 吞成 return false → 改后须把门禁 IllegalStateException 冒泡。
     * 运行时暴露「设备未 markReady 即批量 publish」的设计缺陷，绝不静默。
     */
    @Test
    public void publicAttrsState_preReady_bubblesGateException_notSwallowed() {
        DeviceBase device = TestPhyDeviceHelper.createDevice("entry1", "uid1", "com.ecat:integration-test");
        TestPhyDeviceHelper.TestPhyAttr attr = new TestPhyDeviceHelper.TestPhyAttr("attr1");
        device.setAttribute(attr);

        // mock core：门禁在 publicState 首部即抛，core 仅防 updateValue/publicState 下游 NPE
        EcatCore core = mock(EcatCore.class);
        BusRegistry bus = mock(BusRegistry.class);
        when(core.getBusRegistry()).thenReturn(bus);
        when(core.getStateManager()).thenReturn(mock(StateManager.class));
        device.load(core);

        assertFalse("构造后未 markReady，phase 须 < READY", device.isReady());
        assertTrue(attr.updateValue(1.0, AttributeStatus.NORMAL));   // 建 midState

        IllegalStateException ex = assertThrows(IllegalStateException.class, device::publicAttrsState);
        assertTrue("异常须点明未就绪，msg=" + ex.getMessage(), ex.getMessage().contains("未就绪"));
        // 门禁抛在发布前，bus 不应被触达
        verify(bus, never()).publish(any(BusEvent.class));
    }

    /**
     * publicState 持久化失败：saveState 抛异常（模拟磁盘/DB 故障）须<b>抛出</b>带属性 id 上下文的
     * RuntimeException，不再被内层 catch 静默吞成日志。设备已 READY（绕过门禁）、属性 persistable。
     */
    @Test
    public void publicState_saveStateFails_throwsWithContext_notSwallowed() {
        DeviceBase device = TestPhyDeviceHelper.createDevice("entry2", "uid2", "com.ecat:integration-test");
        // persistable=true：走 saveState 落盘分支
        TestPhyDeviceHelper.TestPhyAttr attr = new TestPhyDeviceHelper.TestPhyAttr("persist-attr", true);
        device.setAttribute(attr);

        EcatCore core = mock(EcatCore.class);
        BusRegistry bus = mock(BusRegistry.class);
        StateManager sm = mock(StateManager.class);
        when(core.getBusRegistry()).thenReturn(bus);
        when(core.getStateManager()).thenReturn(sm);
        device.load(core);
        device.markReady();
        assertTrue("markReady 后须 READY", device.isReady());

        // 模拟持久化层系统级故障
        doThrow(new RuntimeException("disk full")).when(sm).saveState(any(), any());
        assertTrue(attr.updateValue(5.0, AttributeStatus.NORMAL));   // 建 midState

        RuntimeException ex = assertThrows(RuntimeException.class, attr::publicState);
        assertTrue("异常信息须带属性 id 便于定位，msg=" + ex.getMessage(),
            ex.getMessage().contains("persist-attr"));
        // 持久化失败即暴露，不应继续发布总线事件
        verify(bus, never()).publish(any(BusEvent.class));
    }
}
