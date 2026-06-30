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
 * 总线事件载荷的统一类型边界接口。
 *
 * <p>所有总线事件载荷（{@link DeviceDataChangedEvent}、{@link ConfigEntryEvent}、
 * {@link IntegrationLifecycleEvent}、{@link NotificationEvent}、{@link AsyncExecutionEvent}）实现此接口，
 * 配合 {@link BusEvent}&lt;T extends BusPayload&gt; 的泛型约束。注：{@link com.ecat.core.State.AttrState}
 * 是 DeviceDataChangedEvent 的内嵌 State（载荷内容），不直接 implements BusPayload——State 与 bus 解耦：
 * <ul>
 *   <li>给 BusEvent 一个统一的载荷类型上界——订阅方拿到 {@code BusEvent<DeviceDataChangedEvent>} 时
 *       {@code getPayload()} 直接是强类型载荷，无需 instanceof/强转、无需 Map 解析；</li>
 *   <li>序列化/SSE 层有统一类型边界；</li>
 *   <li>编译期保证 producer 与 consumer 共享同一载荷类定义——杜绝用 {@code Map<String,Object>} 弱类型
 *       承载已知事件字段导致的数据结构漂移（G-TYPE-1）。</li>
 * </ul>
 *
 * <p><b>设计哲学</b>（参照 Home Assistant 的 Event/State 分离，但用 Java 强类型取代 HA 的 dict 载荷）：
 * 各载荷的业务字段各异，由实现类强类型表达；信封级公共元数据（type/firedAt/context/uuid）在 {@link BusEvent}，
 * 不强行下沉到载荷接口。本接口仅作类型边界，不强制共同方法——避免给异构载荷塞进名不副实的公共字段。
 *
 * @author coffee
 * @see BusEvent
 */
public interface BusPayload {
    // 标记 + 类型边界接口。各事件载荷的差异化字段由实现类强类型定义（非 Map）。
}
