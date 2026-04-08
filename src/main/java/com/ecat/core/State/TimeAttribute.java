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

package com.ecat.core.State;

import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * 时间属性 - 表示一个时间戳（Instant）类型的属性。
 *
 * <p>TimeAttribute 继承 {@link AttributeBase}，泛型为 {@link Instant}，
 * 用于存储和显示时间戳数据（如滤膜更换时间、切割器更换时间等）。
 *
 * <p>显示格式为 "yyyy-MM-dd HH:mm:ss"，使用系统默认时区。
 * 解析时支持 "yyyy-MM-dd HH:mm:ss" 和 ISO-8601 两种格式。
 *
 * @see AttributeBase
 * @see AttrValueType#INSTANT
 * @author coffee
 */
public class TimeAttribute extends AttributeBase<Instant> {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 支持I18n的构造函数
     *
     * @param attributeID 属性ID
     * @param attrClass 属性类型
     * @param nativeUnit 原始信号单位（时间属性通常为null）
     * @param displayUnit 显示单位（时间属性通常为null）
     * @param displayPrecision 显示精度（时间属性通常为0）
     * @param unitChangeable 是否允许修改单位
     * @param valueChangeable 是否允许外部修改值
     */
    public TimeAttribute(String attributeID, AttributeClass attrClass,
                         UnitInfo nativeUnit, UnitInfo displayUnit,
                         int displayPrecision, boolean unitChangeable, boolean valueChangeable) {
        super(attributeID, attrClass, nativeUnit, displayUnit,
              displayPrecision, unitChangeable, valueChangeable);
    }

    /**
     * 获取显示值，将 Instant 格式化为 "yyyy-MM-dd HH:mm:ss"（使用系统默认时区）。
     *
     * @param toUnit 目标显示单位（时间属性忽略单位）
     * @return 格式化后的时间字符串，如果值为null则返回null
     */
    @Override
    public String getDisplayValue(UnitInfo toUnit) {
        if (getValue() == null) return null;
        ZonedDateTime localDateTime = getValue().atZone(ZoneId.systemDefault());
        return DISPLAY_FORMATTER.format(localDateTime);
    }

    /**
     * 使用字符串设置显示值。
     * 重写父类方法以支持 "yyyy-MM-dd HH:mm:ss" 和 ISO-8601 格式的字符串解析。
     *
     * @param newDisplayValue 时间字符串
     * @param fromUnit 来源单位（时间属性忽略）
     * @return CompletableFuture，true表示设置成功，false表示解析失败
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        if (!valueChangeable) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            Instant instant = parseTimeString(newDisplayValue);
            return setDisplayValueImp(instant, fromUnit);
        } catch (Exception e) {
            CompletableFuture<Boolean> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(
                new IllegalArgumentException("setDisplayValue类型转换失败: " + e.getMessage(), e)
            );
            return failedFuture;
        }
    }

    /**
     * 将时间字符串解析为 Instant。
     * 依次尝试 "yyyy-MM-dd HH:mm:ss" 格式和 ISO-8601 格式。
     *
     * @param source 输入时间字符串
     * @return 解析后的 Instant
     * @throws Exception 如果两种格式都解析失败
     */
    protected Instant parseTimeString(String source) throws Exception {
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(source,
                DISPLAY_FORMATTER.withZone(ZoneId.systemDefault()));
            return zdt.toInstant();
        } catch (Exception e) {
            // 尝试 ISO-8601 格式
            return Instant.parse(source);
        }
    }

    /**
     * 使用已转换的 Instant 值设置属性值。
     *
     * @param value 已解析的 Instant 值
     * @param fromUnit 来源单位（时间属性忽略）
     * @return CompletableFuture，true表示设置成功
     */
    @Override
    protected CompletableFuture<Boolean> setDisplayValueImp(Instant value, UnitInfo fromUnit) {
        updateValue(value);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    protected Instant convertFromUnitImp(Instant value, UnitInfo fromUnit) {
        return value; // 时间属性无单位转换
    }

    @Override
    public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) {
        throw new UnsupportedOperationException("TimeAttribute does not support unit conversion");
    }

    @Override
    public ConfigDefinition getValueDefinition() {
        return null;
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.time_attr.", "");
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.TEXT;
    }
}
