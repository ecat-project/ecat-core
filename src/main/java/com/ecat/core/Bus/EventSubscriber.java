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

import com.ecat.core.Bus.event.BusEvent;

/**
 * 事件订阅者接口——订阅者接收强类型 {@link BusEvent} 信封：
 * 从 {@code event.getPayload()} 取领域载荷、{@code event.getType()} 取 topic、
 * {@code event.getContext()} 取溯源。总线是同步扇出的哑管道（见 {@link BusRegistry}）。
 *
 * @author coffee
 */
public interface EventSubscriber {

    void handleEvent(BusEvent<?> event);
}
