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

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 子 flow 基类：供设备集成宿主以 {@code registerFlowStep} 挂载的流程步骤组。
 *
 * <p>与普通 flow 的全部差异都在本类收口（主类 {@link AbstractConfigFlow} 只保留挂载方法，互不污染）：
 * <ul>
 *   <li>入口步显式声明：构造器内调用 {@link #registerStepEntry}（且仅一次）。挂载方拿到的是
 *       声明值——不是「注册顺序的第一步」这种隐式约定（注册顺序≠流转顺序，靠顺序定入口是脆弱契约）。</li>
 *   <li>出口信号：尾步 return {@link #subFlowComplete()}，控制权交回宿主尾步。</li>
 *   <li>结构禁令（构造期即抛，早于挂载）：user/reconfigure 入口角色与 createEntry 属宿主——
 *       子 flow 是步骤组，不是独立流程。</li>
 * </ul>
 *
 * <p>身份纪律：identity 步骤（sn/uniqueId 排重）属宿主——机械守卫只盖 createEntry，
 * {@code context.setEntryUniqueId(...)} 是 public 方法拦不住，靠子 flow 实现方自觉不碰。
 *
 * @author coffee
 */
public abstract class AbstractSubConfigFlow extends AbstractConfigFlow {

    /** 入口步 stepId（registerStepEntry 声明；包私有供同包的 registerFlowStep 读取）。 */
    String entryStepId;

    /** 防重复挂载（一个实例只允许挂进一个宿主）。 */
    boolean mounted;

    /**
     * 注册并声明入口步：挂载后宿主从这一步进入子 flow。
     * 与 registerStepUser/registerStepReconfigure 同族——角色在注册时标记。
     *
     * @param stepId 入口步 ID
     * @param handler 步骤处理函数
     * @param displayName 显示名称
     */
    protected void registerStepEntry(String stepId, Function<Map<String, Object>, ConfigFlowResult> handler,
                                     String displayName) {
        registerStep(stepId, handler, displayName);
        if (entryStepId != null) {
            throw new IllegalStateException("入口步只能声明一次: " + stepId);
        }
        entryStepId = stepId;
    }

    /** 子 flow 出口：本子 flow 步骤已全部完成，控制权交回宿主尾步（挂载时由宿主指定）。 */
    protected final ConfigFlowResult subFlowComplete() {
        return ConfigFlowResult.subFlowComplete();
    }

    // ========== 结构禁令：以下能力属宿主，子 flow 构造期即拦 ==========

    @Override
    protected void registerStepUser(String stepId, String displayName,
                                    BiFunction<Map<String, Object>, FlowContext, ConfigFlowResult> handler) {
        throw new IllegalStateException("子 flow 不允许声明 user 入口步（入口角色属宿主）");
    }

    @Override
    protected void registerStepReconfigure(String stepId, String displayName,
                                           BiFunction<Map<String, Object>, FlowContext, ConfigFlowResult> handler) {
        throw new IllegalStateException("子 flow 不允许声明 reconfigure 入口步（入口角色属宿主）");
    }

    @Override
    protected ConfigFlowResult createEntry() {
        throw new IllegalStateException("子 flow 不允许创建 entry——出口请 return subFlowComplete()");
    }
}
