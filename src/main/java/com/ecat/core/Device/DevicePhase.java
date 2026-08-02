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
 * 设备生命周期阶段（READY 门禁的状态机）。
 *
 * <p>核心不变量：{@link AttributeBase#publicState()} 在 {@code phase < READY} 时抛
 * {@link IllegalStateException}——设备未就绪禁止发布属性事件。{@code READY} 唯一写入点是
 * {@link DeviceBase#markReady()}（final，禁止集成 override 抢先就绪）。
 *
 * <p><b>当前接线范围（实事求是）</b>：仅 {@link #CONSTRUCTED}→{@link #READY} 经 markReady 推进，
 * 其余阶段是 final 模板迁移的目标态（模板在 hook 调用周围推进 phase，届时 LOADED/INITIALIZED/
 * REGISTERED/RESTORED/STARTED 才被实际写入）。把 phase 推进放进可被 override 的 load/init/
 * restore/start（load 今天被 ~48 个集成 override）会随 override 漏推进而失效——故中间态推迟到模板。
 * publish 硬门禁只依赖 READY 边界，与中间态无关，故可独立先行。
 *
 * <p>{@link #DISABLED}/{@link #REMOVED} 为终态保留：当前 disable/remove 路径未推进 phase
 * （保持原行为，不阻塞本档）；后续接 disable/remove 推进后，DISABLED/REMOVED 设备的 publish
 * 也会被门禁（{@code compareTo(READY) < 0}）挡下。
 */
public enum DevicePhase {
    /** 构造完成，未 load。初始态。 */
    CONSTRUCTED,
    /** load(core) 完成（解析配置/属性/稳定 id）。【待模板接线】 */
    LOADED,
    /** init() 完成（通信源 register、客户端建连）。【待模板接线】 */
    INITIALIZED,
    /** 进 DeviceRegistry（addDevice / 逻辑 bindingIndex.build）。【待模板接线】 */
    REGISTERED,
    /** restorePersistedState 完成（持久态回填 lastState，不经 midState/publicState）。【待模板接线】 */
    RESTORED,
    /** markReady 完成——publish 门禁在此之后放行。当前唯一由 markReady 写入的态。 */
    READY,
    /** start() 完成（起轮询/定时器）。【待模板接线】 */
    STARTED,
    /** 软移除（disable，可 enable 恢复）。终态保留，待 disable 路径推进。 */
    DISABLED,
    /** 硬移除（remove，不可逆）。终态保留，待 remove 路径推进。 */
    REMOVED
}
