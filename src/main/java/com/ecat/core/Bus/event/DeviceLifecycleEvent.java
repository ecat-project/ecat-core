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

/**
 * 设备生命周期事件载体，由 DeviceRegistry 在 register/unregister 完成时发布。
 * <p>对标 {@link ConfigEntryEvent}（entry 级），供需要按"设备"粒度感知上下线的模块消费
 * （如 logicdevice-airstation 占位替换、media 流启停）——这些模块此前被迫借
 * CONFIG_ENTRY_LIFECYCLE 表达 device 关注。
 *
 * <p>载荷字段：
 * <ul>
 *   <li>{@code deviceId}：设备主键（{@code DeviceBase.getId()}），全局稳定</li>
 *   <li>{@code coordinate}：设备所属集成坐标</li>
 *   <li>{@code entryId}：设备关联的 ConfigEntry（entry-backed=自身 entry；网关子设备=网关 entry，1:N back-ref）</li>
 *   <li>{@code action}：CREATE / RECONFIGURE / REMOVE</li>
 * </ul>
 *
 * <p>device 无独立 enable/disable 事件：entry enable/disable 经设备 create/remove 映射为 CREATE/REMOVE。
 * LogicDevice 豁免——不发本事件（其注册在 LogicDeviceRegistry，不经 DeviceRegistry）。
 *
 * @author coffee
 */
public class DeviceLifecycleEvent implements BusPayload {

    /** 设备生命周期操作类型。Action 由调用方编排方法决定（create/enable→CREATE，reconfigure→RECONFIGURE，disable/remove→REMOVE）。 */
    public enum Action {
        CREATE,
        RECONFIGURE,
        REMOVE
    }

    private final String deviceId;
    private final String coordinate;
    private final String entryId;
    private final Action action;

    public DeviceLifecycleEvent(String deviceId, String coordinate, String entryId, Action action) {
        if (deviceId == null || action == null) {
            throw new IllegalArgumentException("deviceId/action must not be null");
        }
        this.deviceId = deviceId;
        this.coordinate = coordinate;
        this.entryId = entryId;
        this.action = action;
    }

    public String getDeviceId() { return deviceId; }
    public String getCoordinate() { return coordinate; }
    public String getEntryId() { return entryId; }
    public Action getAction() { return action; }
}
