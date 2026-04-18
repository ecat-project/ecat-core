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

package com.ecat.core.Bus;

/**
 * ConfigEntry生命周期事件的数据载体，由ConfigEntryRegistry发布，
 * 供逻辑设备集成(airdevice/airstation)等模块消费。
 * <p>
 * 事件质量等级：注意：此事件在目标集成通知完成之后发布。
 * <ul>
 *   <li>create/reconfigure：异常会向上抛出，不会执行到此事件发布</li>
 *   <li>remove/enable/disable：异常仅被记录，事件仍然会发布</li>
 * </ul>
 * 监听方需要理解：收到事件不保证目标集成的操作一定成功完成。
 *
 * @author coffee
 */
public class ConfigEntryEvent {

    /**
     * ConfigEntry生命周期操作类型
     */
    public enum Action {
        CREATE,
        RECONFIGURE,
        REMOVE,
        ENABLE,
        DISABLE
    }

    private final String entryId;
    private final String coordinate;
    private final Action action;

    /**
     * 构造ConfigEntry事件
     *
     * @param entryId    配置条目ID
     * @param coordinate 集成坐标
     * @param action     操作类型
     */
    public ConfigEntryEvent(String entryId, String coordinate, Action action) {
        this.entryId = entryId;
        this.coordinate = coordinate;
        this.action = action;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getCoordinate() {
        return coordinate;
    }

    public Action getAction() {
        return action;
    }
}
