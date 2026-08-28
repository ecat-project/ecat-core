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

/**
 * 移除动作宿主窄接口（18 号设计 §3.3，结构本体取自 HA {@code async_on_remove}）：
 * 资源创建方把销毁动作注册到宿主生命周期，宿主停止时 LIFO 统一执行——
 * 作者不接触「生命周期」概念，忘了绑定的错误在签名层面不可能。
 *
 * <p>单方法 = 测试可 lambda 假宿主（{@code action -> {}} 或收集断言型）；
 * SDK 只依赖这个面，不依赖 {@link DeviceBase} 全量。</p>
 *
 * <p><b>非阻塞契约</b>（动作纪律）：移除动作必须提交即返——
 * {@code handle.cancel()} 纯标记（不打断在飞轮次）天然合规；耗时释放（关 socket/flush）
 * 须动作内部经引擎提交后即返（fire-and-forget，与 device.stop() 不等在飞轮次的既有语义
 * 一致）。若需「拆卸等清理完成」的有界保证，日后在 sweep 层加 join，本接口永不变。</p>
 *
 * @author coffee
 */
public interface RemovalHost {

    /**
     * 注册移除动作：宿主停止（{@link DeviceBase#cancelManagedTasks()}）时 LIFO 执行。
     *
     * @param action 提交即返的销毁动作（见接口级非阻塞契约）
     * @throws java.util.concurrent.RejectedExecutionException 宿主已停止后注册属病态调用，
     *         严格模式 reject（防「stop 后才注册」的静默泄漏）
     * @throws IllegalArgumentException action 为 null
     */
    void onRemove(Runnable action);
}
