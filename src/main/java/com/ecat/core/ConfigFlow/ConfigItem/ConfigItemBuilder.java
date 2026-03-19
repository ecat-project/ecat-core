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

package com.ecat.core.ConfigFlow.ConfigItem;

import java.util.*;

/**
 * 配置项构建器
 * <p>
 * 提供便捷的工厂方法用于创建各种类型的配置项，并支持链式调用。
 * <p>
 * 使用静态导入简化使用：
 * <pre>{@code
 * import static com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder.*;
 *
 * // 文本字段
 * builder.add(text("username", true)
 *     .displayName("用户名")
 *     .length(1, 50));
 *
 * // 数值字段
 * builder.add(numeric("port", true, 502.0)
 *     .displayName("端口号")
 *     .range(1, 65535));
 *
 * // 枚举字段
 * builder.add(enumItem("protocol", true)
 *     .displayName("协议类型")
 *     .addOption("TCP", "TCP 网络协议")
 *     .addOption("UDP", "UDP 网络协议")
 *     .defaultValue("TCP")
 *     .buildValidator());
 * }</pre>
 *
 * @author coffee
 */
public class ConfigItemBuilder {

    private final List<AbstractConfigItem<?>> items = new ArrayList<>();

    /**
     * 添加配置项
     *
     * @param item 配置项
     * @return this
     */
    public ConfigItemBuilder add(AbstractConfigItem<?> item) {
        items.add(item);
        return this;
    }

    /**
     * 批量添加配置项
     *
     * @param items 配置项数组
     * @return this
     */
    public ConfigItemBuilder addAll(AbstractConfigItem<?>... items) {
        Collections.addAll(this.items, items);
        return this;
    }

    /**
     * 构建配置项列表
     *
     * @return 配置项列表
     */
    public List<AbstractConfigItem<?>> build() {
        return new ArrayList<>(items);
    }

    /**
     * 构建配置项数组
     *
     * @return 配置项数组
     */
    public AbstractConfigItem<?>[] buildArray() {
        return items.toArray(new AbstractConfigItem[0]);
    }

    /**
     * 清空构建器
     *
     * @return this
     */
    public ConfigItemBuilder clear() {
        items.clear();
        return this;
    }

    /**
     * 获取配置项数量
     *
     * @return 配置项数量
     */
    public int size() {
        return items.size();
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建文本配置项
     *
     * @param key 配置项键
     * @param required 是否必需
     * @return 文本配置项
     */
    public static TextConfigItem text(String key, boolean required) {
        return new TextConfigItem(key, required);
    }

    /**
     * 创建文本配置项（带默认值）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @return 文本配置项
     */
    public static TextConfigItem text(String key, boolean required, String defaultValue) {
        return new TextConfigItem(key, required, defaultValue);
    }

    /**
     * 创建数值配置项
     *
     * @param key 配置项键
     * @param required 是否必需
     * @return 数值配置项
     */
    public static NumericConfigItem numeric(String key, boolean required) {
        return new NumericConfigItem(key, required);
    }

    /**
     * 创建数值配置项（带默认值）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @return 数值配置项
     */
    public static NumericConfigItem numeric(String key, boolean required, double defaultValue) {
        return new NumericConfigItem(key, required, defaultValue);
    }

    /**
     * 创建浮点数配置项
     *
     * @param key 配置项键
     * @param required 是否必需
     * @return 浮点数配置项
     */
    public static FloatConfigItem floatItem(String key, boolean required) {
        return new FloatConfigItem(key, required);
    }

    /**
     * 创建浮点数配置项（带默认值）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @return 浮点数配置项
     */
    public static FloatConfigItem floatItem(String key, boolean required, float defaultValue) {
        return new FloatConfigItem(key, required, defaultValue);
    }

    /**
     * 创建短整型配置项
     *
     * @param key 配置项键
     * @param required 是否必需
     * @return 短整型配置项
     */
    public static ShortConfigItem shortItem(String key, boolean required) {
        return new ShortConfigItem(key, required);
    }

    /**
     * 创建短整型配置项（带默认值）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @return 短整型配置项
     */
    public static ShortConfigItem shortItem(String key, boolean required, short defaultValue) {
        return new ShortConfigItem(key, required, defaultValue);
    }

    /**
     * 创建短整型配置项（接受 int 默认值）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @return 短整型配置项
     */
    public static ShortConfigItem shortItem(String key, boolean required, int defaultValue) {
        return new ShortConfigItem(key, required, defaultValue);
    }

    /**
     * 创建布尔配置项
     *
     * @param key 配置项键
     * @param required 是否必需
     * @return 布尔配置项
     */
    public static BooleanConfigItem booleanItem(String key, boolean required) {
        return new BooleanConfigItem(key, required);
    }

    /**
     * 创建布尔配置项（带默认值）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @return 布尔配置项
     */
    public static BooleanConfigItem booleanItem(String key, boolean required, boolean defaultValue) {
        return new BooleanConfigItem(key, required, defaultValue);
    }

    /**
     * 创建枚举配置项
     *
     * @param key 配置项键
     * @param required 是否必需
     * @return 枚举配置项
     */
    public static EnumConfigItem enumItem(String key, boolean required) {
        return new EnumConfigItem(key, required);
    }

    /**
     * 创建枚举配置项（带默认值）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @return 枚举配置项
     */
    public static EnumConfigItem enumItem(String key, boolean required, String defaultValue) {
        return new EnumConfigItem(key, required, defaultValue);
    }

    /**
     * 创建数组配置项
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param <T> 元素类型
     * @return 数组配置项
     */
    public static <T> ArrayConfigItem<T> array(String key, boolean required) {
        return new ArrayConfigItem<>(key, required);
    }

    /**
     * 创建数组配置项（指定元素类型）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param elementType 元素类型
     * @param <T> 元素类型
     * @return 数组配置项
     */
    public static <T> ArrayConfigItem<T> array(String key, boolean required, String elementType) {
        return new ArrayConfigItem<>(key, required, elementType);
    }

    /**
     * 创建数组配置项（带默认值）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @param <T> 元素类型
     * @return 数组配置项
     */
    public static <T> ArrayConfigItem<T> array(String key, boolean required, List<T> defaultValue) {
        return new ArrayConfigItem<>(key, required, defaultValue);
    }

    /**
     * 创建数组配置项（带默认值和元素类型）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @param elementType 元素类型
     * @param <T> 元素类型
     * @return 数组配置项
     */
    public static <T> ArrayConfigItem<T> array(String key, boolean required, List<T> defaultValue, String elementType) {
        return new ArrayConfigItem<>(key, required, defaultValue, elementType);
    }

    /**
     * 创建字符串数组配置项（用于多选）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @return 字符串数组配置项
     */
    public static ArrayConfigItem<String> stringArray(String key, boolean required) {
        return new ArrayConfigItem<>(key, required, "string");
    }

    /**
     * 创建字符串数组配置项（带默认值）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @return 字符串数组配置项
     */
    public static ArrayConfigItem<String> stringArray(String key, boolean required, List<String> defaultValue) {
        return new ArrayConfigItem<>(key, required, defaultValue, "string");
    }
}
