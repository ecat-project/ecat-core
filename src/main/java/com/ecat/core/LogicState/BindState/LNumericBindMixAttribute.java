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

package com.ecat.core.LogicState.BindState;

import com.ecat.core.LogicState.LNumericAttribute;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.UnitInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 多源数值聚合绑定（N个逻辑属性到1个逻辑属性）。
 *
 * <p>LNumericBindMixAttribute 继承 {@link LNumericAttribute} 并实现 {@link ILogicBindAttribute}，
 * 支持将多个逻辑设备的数值属性聚合为一个逻辑值。
 *
 * <p><b>注册源：</b> 通过 {@link #registerLogicSource(String, String)} 注册逻辑源，
 * 每个源用 (sourceDeviceId, sourceAttrId) 定位。
 *
 * <p><b>值更新：</b> 当源属性值更新时，调用 {@link #onSourceUpdated(String, String, double)}，
 * 系统会标记对应源已更新，并在所有源都更新后调用 {@link #calcRawValue(List)} 进行聚合计算。
 *
 * <p><b>聚合逻辑：</b> 子类必须实现 {@link #calcRawValue(List)} 方法提供具体的聚合公式
 * （如求和、求平均、最大值等）。
 *
 * <p>使用示例：
 * <pre>
 *   class SumPowerAttr extends LNumericBindMixAttribute {
 *       SumPowerAttr() {
 *           super("total_power", AttributeClass.POWER, null, null, 2);
 *       }
 *       &#64;Override
 *       protected double calcRawValue(List&lt;BindedLogicAttrData&gt; sources) {
 *           double sum = 0;
 *           for (BindedLogicAttrData s : sources) sum += s.getValue();
 *           return sum;
 *       }
 *   }
 * </pre>
 *
 * @see ILogicBindAttribute
 * @see BindedLogicAttrData
 * @author coffee
 */
public abstract class LNumericBindMixAttribute extends LNumericAttribute
        implements ILogicBindAttribute<Double> {

    /** 已注册的逻辑源映射，key = "sourceDeviceId:sourceAttrId"，保持插入顺序 */
    protected final Map<String, BindedLogicAttrData> bindSources = new LinkedHashMap<>();

    /**
     * 构造函数 - 创建一个多源数值聚合属性。
     *
     * @param attrId 本逻辑属性ID
     * @param attrClass 属性类型
     * @param nativeUnit 原始信号单位
     * @param displayUnit 显示单位
     * @param precision 显示精度
     */
    public LNumericBindMixAttribute(String attrId, AttributeClass attrClass,
                                    UnitInfo nativeUnit, UnitInfo displayUnit,
                                    int precision) {
        super(attrId, attrClass, nativeUnit, displayUnit, precision);
    }

    /**
     * 注册一个逻辑源。
     * 必须在值更新之前调用。
     *
     * @param sourceDeviceId 源逻辑设备ID
     * @param sourceAttrId 源逻辑属性ID
     */
    public void registerLogicSource(String sourceDeviceId, String sourceAttrId) {
        String key = sourceDeviceId + ":" + sourceAttrId;
        bindSources.put(key, new BindedLogicAttrData(sourceDeviceId, sourceAttrId));
    }

    /**
     * 获取所有已注册的逻辑源列表。
     *
     * @return 已注册的逻辑源列表（不可修改）
     */
    public List<BindedLogicAttrData> getSources() {
        return Collections.unmodifiableList(new ArrayList<>(bindSources.values()));
    }

    /**
     * 当某个源逻辑属性值更新时调用。
     *
     * <p>更新对应源的值和时间戳，标记为已更新。
     * 当所有源都已更新时，调用 {@link #calcRawValue(List)} 进行聚合计算，
     * 并将结果更新到本属性的值。
     *
     * @param sourceDeviceId 源逻辑设备ID
     * @param sourceAttrId 源逻辑属性ID
     * @param value 更新的数值
     */
    public void onSourceUpdated(String sourceDeviceId, String sourceAttrId, double value) {
        String key = sourceDeviceId + ":" + sourceAttrId;
        BindedLogicAttrData data = bindSources.get(key);
        if (data == null) return;

        data.setUpdated(true);
        data.setUpdateTime(System.currentTimeMillis());

        // 如果所有源都已更新，执行聚合计算
        if (allSourcesUpdated()) {
            double result = calcRawValue(new ArrayList<>(bindSources.values()));
            updateValue(result);
            // 重置所有更新标记
            resetUpdateFlags();
        }
    }

    /**
     * 检查所有源是否都已更新。
     *
     * @return true表示所有源都已更新
     */
    protected boolean allSourcesUpdated() {
        for (BindedLogicAttrData data : bindSources.values()) {
            if (!data.isUpdated()) return false;
        }
        return true;
    }

    /**
     * 重置所有源的更新标记。
     */
    protected void resetUpdateFlags() {
        for (BindedLogicAttrData data : bindSources.values()) {
            data.setUpdated(false);
        }
    }

    /**
     * 返回已解析的源逻辑属性列表。
     *
     * @return 包含已解析源属性引用的列表
     */
    @Override
    public List<AttributeBase<?>> getBindedAttrs() {
        List<AttributeBase<?>> result = new ArrayList<>();
        for (BindedLogicAttrData data : bindSources.values()) {
            if (data.getResolvedSource() instanceof AttributeBase) {
                result.add((AttributeBase<?>) data.getResolvedSource());
            }
        }
        return result;
    }

    /**
     * 子类实现聚合计算公式。
     *
     * <p>在所有源都已更新时调用。子类应从 sources 列表中读取每个源的值
     * 并计算聚合结果。
     *
     * @param sources 所有已注册的源数据列表
     * @return 聚合计算结果
     */
    protected abstract double calcRawValue(List<BindedLogicAttrData> sources);

    /**
     * 设置显示值 - 聚合属性默认不支持外部设置，返回 false。
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * 绑定模式始终返回 false。
     *
     * @return false
     */
    public boolean isStandalone() {
        return false;
    }
}
