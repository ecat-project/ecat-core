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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 设备基础信息持久化记录。
 * <p>镜像 {@code ConfigEntry} 的持久化模式，存于
 * {@code .ecat-data/core/devices/{groupId}/{artifactId}/{id}.yml}。
 *
 * <p>用途：跨重启稳定 deviceId——{@link DeviceRegistry#load()} 启动时读取全部 DeviceRecord，
 * 在内存建立 {@code (coordinate, uniqueId) → id} 匹配索引，供 getOrCreate 用持久化 id
 * 覆盖构造铸造的默认 UUID，使设备主键跨重启不变。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code id}：设备主键（core 铸造 UUID），文件名同此</li>
 *   <li>{@code coordinate}：groupId:artifactId</li>
 *   <li>{@code uniqueId}：硬件锚点，get-or-create 匹配键（coordinate 内唯一）</li>
 *   <li>{@code entryId}：entry-backed=自身 entryId；网关子设备=创建它的网关 entryId（1:N back-ref）</li>
 *   <li>{@code name/vendor/model}：便利展示字段（可空）</li>
 * </ul>
 *
 * @author coffee
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRecord {
    /** 设备主键（core 铸造 UUID），持久化文件名同此。 */
    private String id;
    /** 集成坐标 groupId:artifactId。 */
    private String coordinate;
    /** 硬件锚点 uniqueId（coordinate 内唯一）。 */
    private String uniqueId;
    /** 关联 ConfigEntry 的 entryId（网关子设备=网关 entryId）。 */
    private String entryId;
    private String name;
    private String vendor;
    private String model;
    private ZonedDateTime createTime;
    private ZonedDateTime updateTime;
}
