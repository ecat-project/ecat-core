package com.ecat.core.Utils.DynamicConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置项类，用于定义和验证配置项的结构和约束。
 * 
 * @author coffee
 */
public class ConfigItem<T> {
    private final String key;
    private final Class<T> type;
    private final boolean required;
    private final T defaultValue;
    private final List<ConstraintValidator<T>> validators;
    private final Map<String, ConfigItem<?>> nestedConfigItems;
    private ConfigItemBuilder nestedListConfig; // 列表中每个元素的配置项构建器

    public ConfigItem(String key, Class<T> type, boolean required, T defaultValue, List<ConstraintValidator<T>> validators) {
        this.key = key;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
        this.validators = validators != null ? validators : new ArrayList<>();
        this.nestedConfigItems = new HashMap<>();
    }

    public ConfigItem(String key, Class<T> type, boolean required, T defaultValue, ConstraintValidator<T> validators) {
        this(key, type, required, defaultValue, validators != null ? Collections.singletonList(validators) : null);
    }

    public ConfigItem(String key, Class<T> type, boolean required, T defaultValue) {
        this(key, type, required, defaultValue, (List<ConstraintValidator<T>>) null);
    }

    public String getKey() {
        return key;
    }

    public Class<T> getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public ConfigItem<T> addNestedConfigItems(ConfigItemBuilder builder) {
        for (ConfigItem<?> item : builder.build()) {
            nestedConfigItems.put(item.getKey(), item);
        }
        return this;
    }

    // 检查是否有嵌套配置项
    public boolean hasNestedConfigItems() {
        return !nestedConfigItems.isEmpty();
    }

    // 获取嵌套配置项集合
    public Collection<ConfigItem<?>> getNestedConfigItems() {
        return nestedConfigItems.values();
    }

    // 验证配置项的值
    public String validate(Object value) {
        if (value == null) {
            if (required) {
                return "配置项 " + key + " 是必需的，但值为空";
            }
            return null;
        }
        if (!type.isInstance(value)) {
            return "配置项 " + key + " 的类型不正确，期望类型为 " + type.getSimpleName();
        }
        T typedValue = type.cast(value);
        for (ConstraintValidator<T> validator : validators) {
            if (!validator.validate(typedValue)) {
                return "配置项 " + key + " 不满足验证条件: " + validator.getErrorMessage();
            }
        }
        if (hasNestedConfigItems() && value instanceof Map) {
            Map<String, Object> nestedConfig = (Map<String, Object>) value;
            for (ConfigItem<?> nestedItem : getNestedConfigItems()) {
                Object nestedValue = nestedConfig.get(nestedItem.getKey());
                String nestedError = nestedItem.validate(nestedValue);
                if (nestedError != null) {
                    return "配置项 " + key + " 的嵌套配置项 " + nestedItem.getKey() + " 验证失败: " + nestedError;
                }
            }
        }

        // 新增：验证嵌套列表中的每个元素
        if (type == List.class && isNestedList()) {
            List<?> list = (List<?>) value;
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    Object element = list.get(i);
                    if (!(element instanceof Map)) {
                        return "列表元素 " + i + " 类型错误，期望为 Map";
                    }
                    // 使用 nestedListConfig 验证元素的每个字段
                    for (ConfigItem<?> field : nestedListConfig.build()) {
                        Object fieldValue = ((Map<?, ?>) element).get(field.getKey());
                        String error = field.validate(fieldValue);
                        if (error != null) {
                            return "列表元素 " + i + " 的字段 " + field.getKey() + " 验证失败: " + error;
                        }
                    }
                }
            }
        }
        return null;
    }

    // 为配置添加默认值
    public void addDefaultValue(Map<String, Object> config) {
        if (!config.containsKey(key) && defaultValue != null) {
            config.put(key, defaultValue);
        }
        if (hasNestedConfigItems()) {
            Object nestedValue = config.get(key);
            if (nestedValue instanceof Map) {
                Map<String, Object> nestedConfig = (Map<String, Object>) nestedValue;
                for (ConfigItem<?> nestedItem : getNestedConfigItems()) {
                    nestedItem.addDefaultValue(nestedConfig);
                }
            }
        }
    }

    public List<ConstraintValidator<T>> getValidators() {
        return validators;
    }

    // 新增：标记当前列表是否为嵌套对象列表（每个元素是结构化对象）
    public boolean isNestedList() {
        return type == List.class && nestedListConfig != null;
    }

    // 新增：设置列表中每个元素的配置结构
    public ConfigItem<T> addNestedListItems(ConfigItemBuilder builder) {
        this.nestedListConfig = builder;
        return this;
    }

    // 新增：获取列表元素的配置结构
    public ConfigItemBuilder getNestedListConfig() {
        return nestedListConfig;
    }
}