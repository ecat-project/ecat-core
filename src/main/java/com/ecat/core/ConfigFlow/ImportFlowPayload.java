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

package com.ecat.core.ConfigFlow;

import java.util.Objects;

/**
 * IMPORT_FLOW 发现源的 payload——强类型<b>信封</b>（envelope 归 ecat-core，{@code data} 格式归各设备集成）。
 * <p>
 * 固定三字段：
 * <ul>
 *   <li>{@code coordinate} —— 派发键。core 据其按 {@code (coordinate, IMPORT_FLOW)} 取目标集成的 handler。
 *       <b>这是 core 唯一读取的字段</b>。</li>
 *   <li>{@code version} —— {@code data} 串的格式版本号。被触发集成据其选择 {@code data} 的解析方式。
 *       core 不解析。</li>
 *   <li>{@code data} —— 被触发设备集成<b>自定义格式</b>的识别串（opaque）。core 不解析、原样转交。
 *       外部生产方要导入某集成设备时，须按该集成的 {@code data} 格式 + {@code version} 组串。</li>
 * </ul>
 *
 * <p>import-flow 没有协议主人（不像 MQTT/ZEROCONF 有协议集成），故 {@code data} 内容契约由<b>消费它的设备集成</b>定义。
 * 这与 MQTT payload 归协议集成拥有是对称的所有权划分（需求 D-10）。
 *
 * <p>{@link #equals(Object)} / {@link #hashCode()} 基于三字段——供 Layer2 matching（R12）的 flow 级去重使用。
 *
 * @author coffee
 */
public final class ImportFlowPayload {

    /** 派发键：core 据其按 (coordinate, IMPORT_FLOW) 取 handler（core 唯一读取的字段）。 */
    private final String coordinate;

    /** data 串格式版本号；被触发集成据其选解析方式。core 不解析。 */
    private final int version;

    /** 被触发设备集成自定义格式的识别串（opaque）。core 不解析。 */
    private final String data;

    /**
     * 构造 import-flow 信封。
     *
     * @param coordinate 派发键（目标集成的 coordinate，groupId:artifactId）
     * @param version    data 格式版本号
     * @param data       被触发集成自定义格式的识别串
     */
    public ImportFlowPayload(String coordinate, int version, String data) {
        this.coordinate = coordinate;
        this.version = version;
        this.data = data;
    }

    public String getCoordinate() {
        return coordinate;
    }

    public int getVersion() {
        return version;
    }

    public String getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImportFlowPayload)) {
            return false;
        }
        ImportFlowPayload that = (ImportFlowPayload) o;
        return version == that.version
                && Objects.equals(coordinate, that.coordinate)
                && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coordinate, version, data);
    }

    /**
     * 调试用 toString（core 不解析 data，仅日志可读）。
     */
    @Override
    public String toString() {
        return "ImportFlowPayload{coordinate='" + coordinate + "', version=" + version
                + ", data='" + data + "'}";
    }
}
