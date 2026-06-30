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
 * 集成生命周期事件
 *
 * <p>当集成被新增、启用、停用、卸载时由 IntegrationManager 发布。
 * 区分"已立即生效"和"待重启后生效"两种状态。
 *
 * <p>消费者（如 Agent Bridge）根据 Effect.ACTIVE 触发更新操作，
 * 忽略 Effect.PENDING_RESTART（等待下次系统启动时通过 INTEGRATIONS_ALL_LOADED 触发）。
 *
 * @author coffee
 */
public class IntegrationLifecycleEvent implements BusPayload {

    private final String coordinate;
    private final Action action;
    private final Effect effect;

    /**
     * 集成生命周期动作类型
     */
    public enum Action {
        ADDED,
        ENABLED,
        DISABLED,
        REMOVED,
        UPGRADED
    }

    /**
     * 集成生命周期生效方式
     *
     * <p>ACTIVE 表示已立即生效，PENDING_RESTART 表示待重启后生效
     */
    public enum Effect {
        ACTIVE,
        PENDING_RESTART
    }

    /**
     * 构造集成生命周期事件
     *
     * @param coordinate 集成坐标 (groupId:artifactId)
     * @param action 动作类型
     * @param effect 生效方式
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public IntegrationLifecycleEvent(String coordinate, Action action, Effect effect) {
        if (coordinate == null || action == null || effect == null) {
            throw new IllegalArgumentException("coordinate, action, effect must not be null");
        }
        this.coordinate = coordinate;
        this.action = action;
        this.effect = effect;
    }

    /**
     * 获取集成坐标
     *
     * @return 集成坐标 (groupId:artifactId)
     */
    public String getCoordinate() { return coordinate; }

    /**
     * 获取动作类型
     *
     * @return 动作类型
     */
    public Action getAction() { return action; }

    /**
     * 获取生效方式
     *
     * @return 生效方式
     */
    public Effect getEffect() { return effect; }

    @Override
    public String toString() {
        return "IntegrationLifecycleEvent{coordinate=" + coordinate + ", action=" + action + ", effect=" + effect + "}";
    }
}
