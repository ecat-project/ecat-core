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

package com.ecat.core.Utils.DynamicConfig;

import com.ecat.core.ConfigFlow.ConfigItem.AbstractConfigItem;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ConfigDefinition 类用于定义和验证配置项的结构和约束。
 * 它允许动态添加配置项，并提供验证和填充默认值的功能。
 *
 * @author coffee
 */
public class ConfigDefinition {
    private final LinkedHashMap<String, ConfigItem<?>> configItems = new LinkedHashMap<>();
    private final Map<ConfigItem<?>, String> invalidConfigItems = new HashMap<>();

    // 存储 ConfigFlow 的新版 AbstractConfigItem
    private final LinkedHashMap<String, AbstractConfigItem<?>> flowConfigItems = new LinkedHashMap<>();
    private final Map<AbstractConfigItem<?>, String> invalidFlowConfigItems = new HashMap<>();

    public ConfigDefinition define(ConfigItemBuilder builder) {
        for (ConfigItem<?> item : builder.build()) {
            configItems.put(item.getKey(), item);
        }
        return this;
    }

    /**
     * 定义 ConfigFlow 的新版配置项
     *
     * @param builder ConfigFlow ConfigItemBuilder
     * @return this
     */
    public ConfigDefinition defineFlowItems(com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder builder) {
        for (AbstractConfigItem<?> item : builder.build()) {
            flowConfigItems.put(item.getKey(), item);
        }
        return this;
    }

    // 验证配置并添加默认值
    public boolean validateConfig(Map<String, Object> config) {
        invalidConfigItems.clear();
        invalidFlowConfigItems.clear();
        boolean isValid = true;

        // 验证旧版 ConfigItem
        for (ConfigItem<?> item : configItems.values()) {
            item.addDefaultValue(config);
            Object value = config.get(item.getKey());
            String errorMessage = item.validate(value);
            if (errorMessage != null) {
                invalidConfigItems.put(item, errorMessage);
                isValid = false;
            }
        }

        // 验证新版 AbstractConfigItem
        for (AbstractConfigItem<?> item : flowConfigItems.values()) {
            item.addDefaultValue(config);
            Object value = config.get(item.getKey());
            Object validationResult = item.validate(value);
            if (validationResult != null) {
                invalidFlowConfigItems.put(item, String.valueOf(validationResult));
                isValid = false;
            }
        }

        return isValid;
    }

    // 填充默认值
    public Map<String, Object> fillDefaults(Map<String, Object> config) {
        Map<String, Object> filledConfig = new HashMap<>(config);

        // 填充旧版 ConfigItem 默认值
        for (ConfigItem<?> item : configItems.values()) {
            if (!filledConfig.containsKey(item.getKey()) && item.getDefaultValue() != null) {
                filledConfig.put(item.getKey(), item.getDefaultValue());
            }
            if (item.hasNestedConfigItems()) {
                Object nestedValue = filledConfig.get(item.getKey());
                if (nestedValue instanceof Map) {
                    Map<String, Object> nestedConfig = (Map<String, Object>) nestedValue;
                    for (ConfigItem<?> nestedItem : item.getNestedConfigItems()) {
                        if (!nestedConfig.containsKey(nestedItem.getKey()) && nestedItem.getDefaultValue() != null) {
                            nestedConfig.put(nestedItem.getKey(), nestedItem.getDefaultValue());
                        }
                    }
                }
            }
        }

        // 填充新版 AbstractConfigItem 默认值
        for (AbstractConfigItem<?> item : flowConfigItems.values()) {
            if (!filledConfig.containsKey(item.getKey()) && item.getDefaultValue() != null) {
                filledConfig.put(item.getKey(), item.getDefaultValue());
            }
        }

        return filledConfig;
    }

    // 对外开放获取未通过验证的配置项及其错误信息的方法
    public Map<ConfigItem<?>, String> getInvalidConfigItems() {
        return invalidConfigItems;
    }

    /**
     * 获取未通过验证的 ConfigFlow 配置项及其错误信息
     *
     * @return 未通过验证的配置项映射
     */
    public Map<AbstractConfigItem<?>, String> getInvalidFlowConfigItems() {
        return invalidFlowConfigItems;
    }

    public LinkedHashMap<String, ConfigItem<?>> getConfigItems() {
        return configItems;
    }

    /**
     * 获取 ConfigFlow 配置项
     *
     * @return ConfigFlow 配置项映射
     */
    public LinkedHashMap<String, AbstractConfigItem<?>> getFlowConfigItems() {
        return flowConfigItems;
    }

    /**
     * 检查是否有任何配置项（旧版或新版）
     *
     * @return 是否有配置项
     */
    public boolean hasConfigItems() {
        return !configItems.isEmpty() || !flowConfigItems.isEmpty();
    }

    /**
     * 获取所有配置项数量（旧版 + 新版）
     *
     * @return 配置项数量
     */
    public int getConfigItemCount() {
        return configItems.size() + flowConfigItems.size();
    }
}
