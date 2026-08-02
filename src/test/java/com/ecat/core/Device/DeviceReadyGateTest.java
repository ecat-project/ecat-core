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
import com.ecat.core.Bus.event.DeviceDataChangedEvent;
import com.ecat.core.EcatCore;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.StateManager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/**
 * 设备就绪门禁（READY gate）硬门禁回归：device 未 READY（phase &lt; READY）时，
 * {@link AttributeBase#publicState()} 必须 <b>抛 {@link IllegalStateException}</b>（而非静默挂起），
 * 且保留 midState（isValueUpdated 不清）待 {@link DeviceBase#markReady()} flush。
 *
 * <p>硬门禁（替代原软门禁的 return-true 挂起）：清理三设备预 ready 期 publish 后，全工作区已无合法
 * 预 ready publisher（逻辑设备 init 期 updateValue 刻意早于 setAttribute，device=null 不建 midState），
 * 故预 ready publish 即垃圾代码——当场报错。
 *
 * <p>四层断言：
 * <ol>
 *   <li>预 ready publish 抛 + 保留 midState + 不发 bus；</li>
 *   <li>READY 后 publish 正常单次；</li>
 *   <li>markReady 推进 CONSTRUCTED→READY 并 flush 挂起态（稳定 id 首发）；</li>
 *   <li>重复 markReady 抛非法迁移（READY 不可二次写入）。</li>
 * </ol>
 */
public class DeviceReadyGateTest {

    /** 预 ready：publicState 抛 IllegalStateException，midState 保留（isValueUpdated 不清），bus 0 次。 */
    @Test
    public void publicState_preReady_throwsAndRetainsMidState() {
        DeviceBase device = mock(DeviceBase.class, RETURNS_DEEP_STUBS);
        when(device.isReady()).thenReturn(false);
        AttributeBase<Double> attr = new TestPhyDeviceHelper.TestPhyAttr("attr1");
        attr.setDevice(device);
        BusRegistry bus = mock(BusRegistry.class);
        when(device.getCore().getBusRegistry()).thenReturn(bus);

        assertTrue(attr.updateValue(111.0, AttributeStatus.NORMAL));   // 建 midState + isValueUpdated=true（updateValue 不自动发布）

        IllegalStateException ex = assertThrows(IllegalStateException.class, attr::publicState);
        assertTrue("异常信息须点明未就绪，msg=" + ex.getMessage(),
            ex.getMessage().contains("未就绪"));
        assertTrue("硬门禁抛出后 isValueUpdated 必须保留（midState 未 flush）", attr.isValueUpdated());
        verify(bus, never()).publish(any(BusEvent.class));
    }

    /** READY 后 publicState 正常发布单次、清 isValueUpdated。 */
    @Test
    public void publicState_whenReady_publishesOnce() {
        DeviceBase device = mock(DeviceBase.class, RETURNS_DEEP_STUBS);
        when(device.getId()).thenReturn("stable-id");
        when(device.isReady()).thenReturn(true);
        AttributeBase<Double> attr = new TestPhyDeviceHelper.TestPhyAttr("attr1");
        attr.setDevice(device);
        BusRegistry bus = mock(BusRegistry.class);
        when(device.getCore().getBusRegistry()).thenReturn(bus);

        assertTrue(attr.updateValue(222.0, AttributeStatus.NORMAL));
        assertTrue(attr.publicState());

        assertFalse(attr.isValueUpdated());
        verify(bus, times(1)).publish(any(BusEvent.class));
    }

    /**
     * markReady 推进 CONSTRUCTED→READY 并 flush：真设备 + 真属性。
     * 未 READY 期 updateValue+publicState 抛（bus 0 次、isValueUpdated 保留）；
     * markReady 后单次 flush 发布，deviceId=稳定 id、isValueUpdated 清零。
     */
    @Test
    public void markReady_advancesToReady_andFlushesPending() {
        // 真设备（构造铸造临时 id；此处不进 registry/不 setId，仅验 flush 行为，id 即构造默认值）
        DeviceBase device = TestPhyDeviceHelper.createDevice("entry1", "uid1", "com.ecat:integration-test");
        String stableId = device.getId();
        TestPhyDeviceHelper.TestPhyAttr attr = new TestPhyDeviceHelper.TestPhyAttr("attr1");
        device.setAttribute(attr);

        // 注入 mock core + bus + stateManager（restore/flush 走 device.getCore()）
        EcatCore core = mock(EcatCore.class);
        BusRegistry bus = mock(BusRegistry.class);
        StateManager sm = mock(StateManager.class);
        when(core.getBusRegistry()).thenReturn(bus);
        when(core.getStateManager()).thenReturn(sm);
        device.load(core);

        // CONSTRUCTED（未 markReady）→ 硬门禁：publicState 抛、bus 0 次、midState 保留
        assertFalse("构造后未 markReady，phase 须 < READY", device.isReady());
        assertTrue(attr.updateValue(333.0, AttributeStatus.NORMAL));
        assertThrows(IllegalStateException.class, attr::publicState);
        assertTrue(attr.isValueUpdated());
        verify(bus, never()).publish(any(BusEvent.class));

        // markReady → CONSTRUCTED→READY + 内部 flushPendingPublishes → 单次稳定 id 发布
        device.markReady();
        assertTrue("markReady 后 phase 须 >= READY", device.isReady());
        assertFalse("flush 后 isValueUpdated 必须清零", attr.isValueUpdated());
        verify(bus, times(1)).publish(any(BusEvent.class));

        // 捕获发布事件，断言 deviceId=稳定 id（非临时/非 null）
        org.mockito.ArgumentCaptor<BusEvent> captor = org.mockito.ArgumentCaptor.forClass(BusEvent.class);
        verify(bus, times(1)).publish(captor.capture());
        DeviceDataChangedEvent payload = (DeviceDataChangedEvent) captor.getValue().getPayload();
        assertEquals("flush 发布的事件必须用稳定 deviceId", stableId, payload.getDeviceId());
    }

    /**
     * markReady 触发 onReady 钩子，且顺序为 transition(READY) → flush(源属性) → onReady(派生算+发)。
     *
     * <p>锁住 B2 框架契约（onReady 是新公开扩展点）：
     * <ol>
     *   <li>onReady 内 isReady()==true —— transition 已先于 onReady，故 onReady 里的 publicState
     *       过硬门禁不抛（aggregate「就绪期才算+发」正是据此挪出 init）；</li>
     *   <li>onReady 触发时源属性 isValueUpdated==false —— flush 已先于 onReady 提交源 midState，
     *       因先于果：onReady 里读源属性拿到的是已提交因果态；</li>
     *   <li>onReady 内对派生属性 updateValue+publicState 成功发布 —— 证明 onReady 是合法的就绪期发布窗口。</li>
     * </ol>
     */
    @Test
    public void markReady_runsOnReady_afterTransitionAndFlush_gateOpen() {
        // 用数组在 onReady 回调内「就地观测」设备当时态，供事后断言顺序
        final boolean[] gateOpenAtOnReady = {false};
        final boolean[] sourceFlushedAtOnReady = {false};
        final boolean[] onReadyPublishSucceeded = {false};

        // 属性须先于匿名设备类声明（onReady 内引用），effectively final
        final TestPhyDeviceHelper.TestPhyAttr sourceAttr = new TestPhyDeviceHelper.TestPhyAttr("source");
        final TestPhyDeviceHelper.TestPhyAttr aggAttr = new TestPhyDeviceHelper.TestPhyAttr("aggregate");

        // 真设备 + 匿名子类覆写 onReady（模拟 logicdevice aggregate 就绪期算+发）
        DeviceBase device = new DeviceBase(TestPhyDeviceHelper.createDevice("e3", "uid3", null).getEntry()) {
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
            @Override protected void onReady() {
                gateOpenAtOnReady[0] = isReady();                        // ① transition 须已先于 onReady
                sourceFlushedAtOnReady[0] = !sourceAttr.isValueUpdated(); // ② flush 须已先于 onReady
                // ③ onReady 内 publish 须过硬门禁（gate 已开）：模拟 aggregate 就绪期算+发
                aggAttr.updateValue(9.0, AttributeStatus.NORMAL);
                aggAttr.publicState();
                onReadyPublishSucceeded[0] = true;
            }
        };
        device.setAttribute(sourceAttr);
        device.setAttribute(aggAttr);

        EcatCore core = mock(EcatCore.class);
        BusRegistry bus = mock(BusRegistry.class);
        when(core.getBusRegistry()).thenReturn(bus);
        when(core.getStateManager()).thenReturn(mock(StateManager.class));
        device.load(core);

        // 预 ready：源属性建 midState（因），派生属性尚未算
        assertTrue(sourceAttr.updateValue(1.0, AttributeStatus.NORMAL));
        assertTrue(sourceAttr.isValueUpdated());

        device.markReady();

        assertTrue("onReady 须被 markReady 触发", onReadyPublishSucceeded[0]);
        assertTrue("transition→READY 须先于 onReady（门禁已开）", gateOpenAtOnReady[0]);
        assertTrue("flush 须先于 onReady（源属性因果已提交）", sourceFlushedAtOnReady[0]);
        // flush 发源属性 1 次 + onReady 发派生属性 1 次 = 2 次稳定 id 发布
        verify(bus, times(2)).publish(any(BusEvent.class));
    }

    /** 重复 markReady 抛非法迁移：READY 不可二次写入（防重复就绪/终态后误就绪）。 */
    @Test
    public void markReady_twice_throwsIllegalTransition() {
        DeviceBase device = TestPhyDeviceHelper.createDevice("entry2", "uid2", "com.ecat:integration-test");
        EcatCore core = mock(EcatCore.class);
        when(core.getStateManager()).thenReturn(mock(StateManager.class));
        device.load(core);

        device.markReady();   // CONSTRUCTED→READY
        assertTrue(device.isReady());
        assertThrows("重复 markReady 必须抛非法迁移", IllegalStateException.class, device::markReady);
    }
}
