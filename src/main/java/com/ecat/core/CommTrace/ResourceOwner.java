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

package com.ecat.core.CommTrace;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Device.RemovalHost;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Utils.Mdc.MdcContext;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * IO 资源使用者的权威身份记录（不可变）。IO 库（serial/modbus/tcp/httpserver/mqtt）的
 * 资源账本以此为值——"这个口/连接/句柄上注册了哪些使用方"与通讯追踪归因共用同一真相源。
 *
 * <p><b>身份模型</b>：层级（{@link OwnerLevel}）+ 结构身份字段（coordinate/entryId/deviceId）
 * + 使用方式（{@link Usage}）+ 兼容占位（rawIdentity，仅 LEGACY）。构造期按层级 fail-fast
 * 校验（严格模式，不猜）：INTEGRATION→entryId/deviceId 必 null；ENTRY→entryId 必非空、
 * deviceId 必 null；DEVICE→entryId/deviceId 必非空；LEGACY→结构字段全 null、rawIdentity 必非空；
 * 其余层 rawIdentity 必 null。四层 coordinate 规则：LEGACY 必 null，其余三层必非空
 * （查询契约按 coordinate 精准命中，无 coordinate 的类型化 owner 无法被查到）。
 *
 * <p><b>host 活引用</b>（仅 {@code of(host)} 派生路径留存）：只服务展示元数据现取——
 * DEVICE 层设备名从宿主现取（改名实时反映，非注册期快照）。不入构造校验、不入
 * equals/hashCode/ownerKey；账本条目随宿主 onRemove 摘除，引用寿命天然有界。字段构造路径
 * （带主转发/消费方显式身份）host 为 null，相应展示元数据如实缺省。
 *
 * <p><b>DEVICE 层派生</b>（四层身份模型，2026-09-12 裁定）：entryId = 宿主
 * {@code getEntry().getEntryId()}（真实主键，网关子设备 N 台共网关 entry 折叠正确）、
 * deviceId = {@code getId()}（core 铸造稳定 UUID）——两值不同。
 *
 * @author coffee
 */
@Getter
public final class ResourceOwner {

    private final OwnerLevel level;
    private final String coordinate;
    private final String entryId;
    private final String deviceId;
    private final Usage usage;
    private final String rawIdentity;
    /** 展示元数据宿主活引用（DEVICE 层取设备名现值）；不参与身份等值，见类注释。 */
    @Getter(lombok.AccessLevel.NONE)
    private final RemovalHost host;

    /** 包级构造：校验收口唯一通道（同包测试直接构造非法形态锁校验矩阵）。 */
    ResourceOwner(OwnerLevel level, String coordinate, String entryId, String deviceId,
            Usage usage, String rawIdentity, RemovalHost host) {
        this.level = Objects.requireNonNull(level, "level 不能为 null");
        this.usage = Objects.requireNonNull(usage, "usage 不能为 null");
        validateIdentity(level, coordinate, entryId, deviceId, rawIdentity);
        if (level == OwnerLevel.DEVICE && host != null && !(host instanceof DeviceBase)) {
            // mdcEntries 在 DEVICE 层把 host 当 DeviceBase 现取设备名——错型宿主在此 fail-fast，
            // 不留到展示时静默缺名
            throw new IllegalArgumentException(
                    "DEVICE 层 host 须为 DeviceBase（设备名展示来源），实为 " + host.getClass().getName());
        }
        this.coordinate = coordinate;
        this.entryId = entryId;
        this.deviceId = deviceId;
        this.rawIdentity = rawIdentity;
        this.host = host;
    }

    // ========== 静态工厂 ==========

    /**
     * 从移除宿主派生 owner（IO 库 register(host) 收口用）：DeviceBase→DEVICE 层、
     * IntegrationBase→INTEGRATION 层，两者皆留存 host 活引用（设备名现取）。
     *
     * @throws IllegalArgumentException 设备宿主 entry 缺 entryId（{@code @Deprecated} Map
     *         构造器的空 ConfigEntry 路径——该构造器待删，设备须走 entry-backed/网关构造），
     *         或宿主为其他实现（严格模式不猜，调用方须换显式字段构造工厂）
     */
    public static ResourceOwner of(RemovalHost host) {
        Objects.requireNonNull(host, "host 不能为 null——of() 从宿主派生身份");
        if (host instanceof DeviceBase) {
            DeviceBase device = (DeviceBase) host;
            String entryId = device.getEntry() != null ? device.getEntry().getEntryId() : null;
            if (entryId == null || entryId.isEmpty()) {
                throw new IllegalArgumentException("设备宿主 entry 缺 entryId，无法派生 DEVICE 层身份"
                        + "（@Deprecated Map 构造器的空 ConfigEntry 路径；该构造器待删，"
                        + "设备须走 entry-backed/网关构造）");
            }
            return new ResourceOwner(OwnerLevel.DEVICE, device.getCoordinate(), entryId,
                    device.getId(), Usage.DIRECT, null, host);
        }
        if (host instanceof IntegrationBase) {
            return new ResourceOwner(OwnerLevel.INTEGRATION, ((IntegrationBase) host).getCoordinate(),
                    null, null, Usage.DIRECT, null, host);
        }
        throw new IllegalArgumentException("无法从该宿主派生归属身份：" + host.getClass().getName()
                + "——仅支持 DeviceBase（DEVICE 层）/ IntegrationBase（INTEGRATION 层），"
                + "其余调用方请用显式字段构造工厂");
    }

    /** 旧签名兼容占位：register(info, String identity) 的自由字符串原样保留，不伪造结构身份。 */
    public static ResourceOwner legacy(String rawIdentity) {
        return new ResourceOwner(OwnerLevel.LEGACY, null, null, null, Usage.DIRECT, rawIdentity, null);
    }

    /** 集成本体显式身份（INTEGRATION 层；无宿主——生命周期由调用方自管）。 */
    public static ResourceOwner integration(String coordinate) {
        return new ResourceOwner(OwnerLevel.INTEGRATION, coordinate, null, null, Usage.DIRECT, null, null);
    }

    /** entry 级显式身份（ENTRY 层唯一构造路径——of(host) 不派生本层）。 */
    public static ResourceOwner entry(String coordinate, String entryId) {
        return new ResourceOwner(OwnerLevel.ENTRY, coordinate, entryId, null, Usage.DIRECT, null, null);
    }

    /** 设备级显式身份（无展示宿主——消费方 registerForDevice 的"字段构造 owner"形态）。 */
    public static ResourceOwner device(String coordinate, String entryId, String deviceId) {
        return new ResourceOwner(OwnerLevel.DEVICE, coordinate, entryId, deviceId, Usage.DIRECT, null, null);
    }

    /** 设备级显式身份 + 展示宿主（host 供设备名现取，须为 DeviceBase 或 null）。 */
    public static ResourceOwner device(String coordinate, String entryId, String deviceId,
            RemovalHost host) {
        return new ResourceOwner(OwnerLevel.DEVICE, coordinate, entryId, deviceId, Usage.DIRECT, null,
                host);
    }

    /**
     * 借用带主转发：usage 标 ADAPTER，身份字段与 host 引用原样（登记的仍是最终使用者身份，
     * 只标注路径经他库转手）。供 modbus→serial 的 RTU 子注册转发调用者 owner。
     */
    public ResourceOwner asAdapter() {
        if (usage == Usage.ADAPTER) {
            return this;
        }
        return new ResourceOwner(level, coordinate, entryId, deviceId, Usage.ADAPTER, rawIdentity, host);
    }

    // ========== 派生 ==========

    /**
     * 展示上下文（MDC 注入与通讯追踪归因同一真相源）：按 level 派发——DEVICE→设备三键
     * （name 从 host 现取，缺失如实省键）/ ENTRY→entry 键+coordinate / INTEGRATION→coordinate
     * / LEGACY→rawIdentity 键。键名沿用 MdcContext 键族。返回不可变快照。
     */
    public Map<String, String> mdcEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        switch (level) {
            case DEVICE:
                entries.put(MdcContext.DEVICE_ID_KEY, deviceId);
                String name = host instanceof DeviceBase ? ((DeviceBase) host).getName() : null;
                if (name != null) {
                    entries.put(MdcContext.DEVICE_NAME_KEY, name);
                }
                entries.put(MdcContext.INTEGRATION_COORDINATE_KEY, coordinate);
                break;
            case ENTRY:
                entries.put(MdcContext.ENTRY_ID_KEY, entryId);
                entries.put(MdcContext.INTEGRATION_COORDINATE_KEY, coordinate);
                break;
            case INTEGRATION:
                entries.put(MdcContext.INTEGRATION_COORDINATE_KEY, coordinate);
                break;
            case LEGACY:
                entries.put(MdcContext.OWNER_RAW_IDENTITY_KEY, rawIdentity);
                break;
            default:
                throw new IllegalStateException("未覆盖的层级：" + level);
        }
        return Collections.unmodifiableMap(entries);
    }

    /**
     * 账本 Map 键的稳定拼接。rawIdentity 参与：LEGACY 层结构字段全 null，区分度唯一来自
     * rawIdentity（多集成共线同口是常态，缺它则同口多条 LEGACY 条目撞键互踩）；非 LEGACY
     * 层 rawIdentity 恒 null，只贡献固定段，无影响。
     */
    public String ownerKey() {
        return level.name() + '|' + coordinate + '|' + entryId + '|' + deviceId + '|'
                + usage.name() + '|' + rawIdentity;
    }

    // ========== 等值（仅身份字段；host 不参与——见类注释） ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourceOwner)) {
            return false;
        }
        ResourceOwner that = (ResourceOwner) o;
        return level == that.level && usage == that.usage
                && Objects.equals(coordinate, that.coordinate)
                && Objects.equals(entryId, that.entryId)
                && Objects.equals(deviceId, that.deviceId)
                && Objects.equals(rawIdentity, that.rawIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, coordinate, entryId, deviceId, usage, rawIdentity);
    }

    @Override
    public String toString() {
        return "ResourceOwner{" + ownerKey() + "}@" + Integer.toHexString(System.identityHashCode(host));
    }

    /** 层级-字段一致性校验（严格模式 fail-fast；"必填"= 非 null 且非空串）。 */
    private static void validateIdentity(OwnerLevel level, String coordinate, String entryId,
            String deviceId, String rawIdentity) {
        switch (level) {
            case INTEGRATION:
                requireText(coordinate, "INTEGRATION 层 coordinate 必填");
                requireNull(entryId, "INTEGRATION 层 entryId 必为 null");
                requireNull(deviceId, "INTEGRATION 层 deviceId 必为 null");
                requireNull(rawIdentity, "非 LEGACY 层 rawIdentity 必为 null");
                break;
            case ENTRY:
                requireText(coordinate, "ENTRY 层 coordinate 必填");
                requireText(entryId, "ENTRY 层 entryId 必填");
                requireNull(deviceId, "ENTRY 层 deviceId 必为 null");
                requireNull(rawIdentity, "非 LEGACY 层 rawIdentity 必为 null");
                break;
            case DEVICE:
                requireText(coordinate, "DEVICE 层 coordinate 必填");
                requireText(entryId, "DEVICE 层 entryId 必填");
                requireText(deviceId, "DEVICE 层 deviceId 必填");
                requireNull(rawIdentity, "非 LEGACY 层 rawIdentity 必为 null");
                break;
            case LEGACY:
                requireNull(coordinate, "LEGACY 层 coordinate 必为 null");
                requireNull(entryId, "LEGACY 层 entryId 必为 null");
                requireNull(deviceId, "LEGACY 层 deviceId 必为 null");
                requireText(rawIdentity, "LEGACY 层 rawIdentity 必填（旧签名自由串）");
                break;
            default:
                throw new IllegalStateException("未覆盖的层级：" + level);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNull(String value, String message) {
        if (value != null) {
            throw new IllegalArgumentException(message);
        }
    }
}
