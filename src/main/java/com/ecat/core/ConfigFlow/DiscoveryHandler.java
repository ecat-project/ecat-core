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

/**
 * 类型化发现处理器：一个发现源 = 一个类型化 capability（函数）。
 * <p>
 * 集成在 ConfigFlow 构造时按发现源注册（{@code registerStepDiscovery(SourceType, DiscoveryHandler<P>)}），
 * 一个 source 对应一个类型化 handler，payload 作为类型化入参 P 直接传入；集成侧强类型，不从 FlowContext 取 payload。
 *
 * <p><b>core 对 payload 完全 opaque</b>：core 只按 {@code (coordinate, SourceType)} 取出 handler 并把 provider 投递的
 * payload 交给它；不解析、不校验、不仲裁。解析 + 校验 + 判"是不是我的设备"全是设备集成 handler 的职责。
 *
 * <p>handler 返回 {@link ConfigFlowResult}：
 * <ul>
 *   <li>{@code SHOW_FORM} —— 落地到某 step（如 import-flow 跳过型号选择、直达连接配置步）</li>
 *   <li>{@code CREATE_ENTRY} —— 直接完成（payload 已含完整信息）</li>
 *   <li>{@code ABORT} —— 判定非本集成设备 / 校验失败</li>
 * </ul>
 *
 * <p><b>Java 擦除约束</b>：一个类不能同时 {@code implements DiscoveryHandler<A>} 与 {@code DiscoveryHandler<B>}（擦除冲突）。
 * 故集成用<b>多个具名方法 + 方法引用</b>注册（命名规范 {@code this::stepDiscoveryImportFlow} / {@code this::stepDiscoveryMqtt}），
 * 方法名各异避免重载歧义。
 *
 * @param <P> 该发现源的 payload 类型（各 source 完全独立、无统一基类，见需求 R2）
 * @author coffee
 */
public interface DiscoveryHandler<P> {

    /**
     * 处理发现 payload。
     *
     * @param payload  类型化 payload（对 core opaque；对集成是强类型入参）
     * @param context  流程上下文（集成可据此预填 entryData、设置 currentStep 等）
     * @return 流程结果（SHOW_FORM / CREATE_ENTRY / ABORT）
     */
    ConfigFlowResult handle(P payload, FlowContext context);
}
