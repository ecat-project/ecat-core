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

package com.ecat.core.Utils.Mdc;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;

import com.ecat.core.CommTrace.ResourceOwner;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Device.RemovalHost;
import com.ecat.core.Integration.IntegrationBase;

/**
 * 设备维度 MDC 上下文 scope（工单 G：通讯追踪设备归属注入；资源归属登记工单升级注入源）。
 *
 * <p><b>应用场景</b>：传输 SDK（serial/modbus/tcp/httpserver）的轮询周期链在
 * {@code chain.start()} 处对提交线程 MDC 做全量快照、逐轮恢复——本 scope 在起链处把
 * 设备标识写入当前线程 MDC，使后续每轮轮询（及其经 MdcExecutorService 派生的 IO 线程）
 * 的日志与通讯帧捕获（CommTraceBuffer）自动携带设备归属，无需任何逐轮开销。
 *
 * <p><b>注入源两级</b>（D1'：owner 单一真相源，升级在注入端不在消费端）：
 * <ul>
 *   <li>{@link #scopeOf(ResourceOwner)}——权威路径：键集由 owner.mdcEntries() 按 level
 *       派发（DEVICE→设备三键 / ENTRY→entry+coordinate / INTEGRATION→coordinate /
 *       LEGACY→rawIdentity）。owner 已过账本级构造校验，键集恒一致；日志 enrichment
 *       与帧归因同一真相源，id/name 同刻同源。</li>
 *   <li>{@link #scopeOf(RemovalHost)}——宿主路径（轮询 SDK 既有调用面）：按宿主类型
 *       派发——DeviceBase→设备三键、IntegrationBase→coordinate（推送/上报链路的升级
 *       收益）、其余宿主（测试假宿主）no-op 容忍。MDC 路径不适用账本级严格失败：
 *       设备缺 name/coordinate 时省键注入（id 恒有），不抛。</li>
 * </ul>
 *
 * <p><b>为什么是 scope 而非裸 put</b>：起链线程（ConfigFlow/看门狗 worker）是复用线程，
 * 设备键泄漏会污染同线程后续无关注入——try-with-resources 保证异常路径也恢复先前置。
 *
 * <p><b>注入键</b>：{@code device.id}（core 构造期铸造，恒非 null）、{@code device.name}
 * 与 {@code integration.coordinate}（缺失不 put——MDC 值不允许 null）。coordinate 随设备注入
 * 顺带修复 boot 期 entry 恢复线程无 coordinate 的历史缺口（该路径不经集成构造器线程）。
 *
 * @author coffee
 */
public final class DeviceMdcContext {

    private DeviceMdcContext() {
    }

    /**
     * 按宿主取 scope（轮询 SDK 的 {@code RemovalHost host} 形参调用面）：
     * DeviceBase→设备三键；IntegrationBase→coordinate；非设备/集成宿主（测试假宿主等）
     * 返回 no-op scope——调用方无需 instanceof 分支。null 宿主同 no-op（防御显式命名）。
     */
    public static Scope scopeOf(RemovalHost host) {
        if (host instanceof DeviceBase) {
            return scope((DeviceBase) host);
        }
        if (host instanceof IntegrationBase) {
            String coordinate = ((IntegrationBase) host).getCoordinate();
            if (coordinate == null) {
                // onLoad 前无 coordinate：容忍不注入（MDC 路径不适用账本级严格失败）
                return NO_OP;
            }
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put(MdcContext.INTEGRATION_COORDINATE_KEY, coordinate);
            return new Scope(entries);
        }
        return NO_OP;
    }

    /**
     * 按 owner 取 scope（权威路径）：键集整体取 {@link ResourceOwner#mdcEntries()} 派发，
     * 不与宿主路径混搭。null owner 或空键集 no-op（防御显式命名，不猜）。
     */
    public static Scope scopeOf(ResourceOwner owner) {
        if (owner == null) {
            return NO_OP;
        }
        Map<String, String> entries = owner.mdcEntries();
        if (entries.isEmpty()) {
            return NO_OP;
        }
        return new Scope(entries);
    }

    /**
     * 设备 scope：put 设备三键，close 恢复先前置（全量快照恢复，与 TraceContext.restore 同语义）。
     *
     * @param device 待注入标识的设备（null 直接 no-op——防御显式命名，不猜）
     */
    public static Scope scope(DeviceBase device) {
        if (device == null) {
            return NO_OP;
        }
        return new Scope(device);
    }

    /** 设备键 scope 句柄（AutoCloseable 且 close 不抛受检异常，try-with-resources 友好）。 */
    public static class Scope implements AutoCloseable {

        private final Map<String, String> previous;
        /** no-op scope（非设备宿主）：put 未发生，close 不动线程 MDC。 */
        private final boolean noOp;

        private Scope(DeviceBase device) {
            this(buildDeviceEntries(device));
        }

        /** 通用键集 scope：entries 值恒非 null（构建方保证，MDC 不接受 null 值）。 */
        private Scope(Map<String, String> entries) {
            this.previous = MDC.getCopyOfContextMap();
            this.noOp = false;
            entries.forEach(MDC::put);
        }

        /** 设备三键构建（容忍路径：name/coordinate 缺失省键，id 恒有）。 */
        private static Map<String, String> buildDeviceEntries(DeviceBase device) {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put(MdcContext.DEVICE_ID_KEY, device.getId());
            if (device.getName() != null) {
                entries.put(MdcContext.DEVICE_NAME_KEY, device.getName());
            }
            if (device.getCoordinate() != null) {
                entries.put(MdcContext.INTEGRATION_COORDINATE_KEY, device.getCoordinate());
            }
            return entries;
        }

        private Scope() {
            this.previous = null;
            this.noOp = true;
        }

        @Override
        public void close() {
            if (noOp) {
                return;
            }
            MDC.clear();
            if (previous != null) {
                previous.forEach(MDC::put);
            }
        }
    }

    /** 非设备宿主/null 的 no-op scope 共享单例。 */
    private static final Scope NO_OP = new Scope();
}
