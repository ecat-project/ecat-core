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

package com.ecat.core.LogicState;

import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.UnitInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 多源报警属性基类（多对一）。
 * 绑定同一设备内多个逻辑属性。
 *
 * <p>默认行为：时间窗口对齐 — 等待所有注册源都更新后才触发 evaluate()。
 * 子类如需即时响应（每次更新都评估），可覆写 {@link #updateBindAttrValue(AttributeBase)}
 * 跳过窗口检查。
 *
 * @see LStringSelectAttribute
 * 
 * @author coffee
 */
public abstract class LStringSelectMixAttribute extends LStringSelectAttribute {

    protected final List<AttributeBase<?>> bindSources = new ArrayList<>();
    private final Set<String> updatedSources = new HashSet<>();

    /**
     * @param attributeID 报警属性 ID
     * @param attrClass 属性类（如 ALARM_STATUS）
     * @param options 可选项列表（如 ["正常", "报警"]）
     */
    public LStringSelectMixAttribute(String attributeID, AttributeClass attrClass,
                                      List<String> options) {
        super(attributeID, attrClass, options);
    }

    /**
     * 注册一个源属性。
     *
     * @param source 源属性（调用时源必须已存在于 attrMap）
     */
    public void registerSource(AttributeBase<?> source) {
        bindSources.add(source);
    }

    @Override
    public List<AttributeBase<?>> getBindedAttrs() {
        return Collections.unmodifiableList(bindSources);
    }

    /**
     * 默认：时间窗口对齐 — 标记源已更新，所有源更新后触发 evaluate。
     * 子类可 override 实现即时响应模式。
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        String attrId = updatedAttr.getAttributeID();
        updatedSources.add(attrId);
        if (allSourcesUpdated()) {
            evaluate();
            resetUpdated();
        }
    }

    /**
     * 所有源是否都已更新。
     */
    protected boolean allSourcesUpdated() {
        for (AttributeBase<?> src : bindSources) {
            if (!updatedSources.contains(src.getAttributeID())) return false;
        }
        return true;
    }

    /**
     * 重置更新标记。
     */
    protected void resetUpdated() {
        updatedSources.clear();
    }

    /**
     * 子类实现评估逻辑（设置 alarm 值并 publicState）。
     */
    protected abstract void evaluate();

    @Override
    public boolean isStandalone() { return false; }

    @Override
    public CompletableFuture<Boolean> setDisplayValue(String val, UnitInfo fromUnit) {
        return CompletableFuture.completedFuture(false);
    }
}
