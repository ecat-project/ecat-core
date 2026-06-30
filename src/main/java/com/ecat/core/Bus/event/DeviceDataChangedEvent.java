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
package com.ecat.core.Bus.event;

import com.ecat.core.State.AttrState;

/**
 * 设备数据"变化"事件——device.data.update topic 的载荷（≡ Home Assistant 的 state_changed 事件）。
 *
 * <p>携带 {@link #oldState}（变更前）与 {@link #newState}（变更后）两个不可变 {@link AttrState}。
 * 消费方据此做告警/变化检测/录制——没有 old/new 的设备数据事件对下游无意义。
 *
 * <p>{@code oldState} 可为 null（属性首次出现 / 设备新增时无旧值）；{@code newState} 永远非空。
 * 两个 AttrState 都是发布时刻在 synchronized 块内原子捕获的不可变状态，订阅者拿到的事件
 * 绝对自洽、绝不撕裂（修原先发布可变 AttributeBase 导致的竞争脏数据）。
 *
 * @author coffee
 * @see AttrState
 */
public final class DeviceDataChangedEvent implements BusPayload {

    private final String deviceId;
    private final String attrId;
    private final AttrState<?> oldState;
    private final AttrState<?> newState;

    /**
     * @param deviceId  设备 ID，非空
     * @param attrId    属性 ID，非空
     * @param oldState  变更前状态，可为 null（首次出现/设备新增）
     * @param newState  变更后状态，非空
     */
    public DeviceDataChangedEvent(String deviceId, String attrId, AttrState<?> oldState, AttrState<?> newState) {
        if (deviceId == null || attrId == null || newState == null) {
            throw new IllegalArgumentException("deviceId/attrId/newState must not be null");
        }
        this.deviceId = deviceId;
        this.attrId = attrId;
        this.oldState = oldState;
        this.newState = newState;
    }

    public String getDeviceId() { return deviceId; }
    public String getAttrId() { return attrId; }

    /** 变更前状态；属性首次出现 / 设备新增时为 null。 */
    public AttrState<?> getOldState() { return oldState; }

    /** 变更后状态；永远非空。 */
    public AttrState<?> getNewState() { return newState; }
}
