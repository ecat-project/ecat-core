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

package com.ecat.core.ConfigEntry;

/**
 * 流程 / 条目来源类型枚举（合一；对齐 HA config_entries 的 source 概念，ECAT 自有命名）。
 * <p>
 * 代码落点 = 就地扩展现有 {@code SourceType} 至 7 值（不新建并行 enum，见需求 D-15）。
 * 按角色分三类：
 * <ul>
 *   <li>携带 discovery payload 的发现源（仅这三个可注册 {@code DiscoveryHandler&lt;P&gt;}）：
 *       {@link #IMPORT_FLOW} / {@link #MQTT} / {@link #ZEROCONF}</li>
 *   <li>既有入口（不携带 discovery payload，走各自现有 step）：{@link #USER} / {@link #RECONFIGURE}</li>
 *   <li>控制源（忽略/召回，复用 ConfigEntry）：{@link #IGNORE} / {@link #UNIGNORE}</li>
 * </ul>
 *
 * @author coffee
 */
public enum SourceType {

    /**
     * 用户主动创建（人工完整向导 registerStepUser）。
     */
    USER,

    /**
     * 重新配置已有 entry（registerStepReconfigure）。
     */
    RECONFIGURE,

    // —— 携带 discovery payload 的发现源（仅这三个可注册 DiscoveryHandler<P>) ——

    /**
     * import-flow：外部（同进程 SDK）提供设备的部分识别信息 → 集成 handler 自解析 →
     * 跳过"设备型号选择"等前置 step、直达设备连接配置 step。仍是 flow（走 DAG、仍有需人工填写的步）。
     * <p>ECAT 自有语义，<b>非</b>对齐 HA {@code SOURCE_IMPORT}（后者是一次性完整 entry 导入，语义不同，见需求 R8）。
     */
    IMPORT_FLOW,

    /**
     * mqtt 集成订阅事件产出的发现源（HA MQTT discovery 规范）。
     */
    MQTT,

    /**
     * tcp/mdns 集成 mDNS/zeroconf 广播订阅产出的发现源（原 mdns，命名对齐 HA SOURCE_ZEROCONF）。
     */
    ZEROCONF,

    // —— 控制源（忽略/召回，复用 ConfigEntry，见"Discovery Flow 管理机制"）——

    /**
     * 用户忽略某发现（建 source=IGNORE 的 ConfigEntry 抑制同 uniqueId 的后续发现，req2）。
     */
    IGNORE,

    /**
     * 用户召回已忽略（删 IGNORE entry 后重发现，req3）。
     */
    UNIGNORE;

    /**
     * 是否"携带 discovery payload 的发现源"——仅 {@link #IMPORT_FLOW}/{@link #MQTT}/{@link #ZEROCONF}。
     * <p>
     * 注册不变式强校验用：只有这三个 source 可以注册 {@code DiscoveryHandler<P>}，
     * 对其余值注册会抛 IllegalStateException（严格模式）。
     *
     * @return 若本值为携带 payload 的发现源则返回 true
     */
    public boolean isPayloadDiscoverySource() {
        return this == IMPORT_FLOW || this == MQTT || this == ZEROCONF;
    }
}
