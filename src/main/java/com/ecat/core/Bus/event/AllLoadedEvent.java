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
 * "全部加载完成"信号事件——integration.all_loaded / logic_device.all_loaded topic 的载荷。
 *
 * <p>纯信号事件：载荷本身无业务数据。消费方订阅 topic 即收到"加载完成"通知；
 * 触发时刻从 {@link BusEvent#getFiredAt()} 取（不再用冗余的 Instant 载荷）。
 *
 * <p>两个 topic 共用此载荷类型——topic 名是判别符，载荷只需是非空强类型 BusPayload 标记
 * （满足 {@code BusEvent<T extends BusPayload>} 的类型约束）。
 *
 * @author coffee
 */
public final class AllLoadedEvent implements BusPayload {
    // 纯信号标记；时刻见 BusEvent.getFiredAt()。
}
