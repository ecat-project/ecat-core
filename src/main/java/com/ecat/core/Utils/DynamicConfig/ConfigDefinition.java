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

import java.util.HashMap;
import java.util.LinkedHashMap;
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

    public ConfigDefinition define(ConfigItemBuilder builder) {
        for (ConfigItem<?> item : builder.build()) {
            configItems.put(item.getKey(), item);
        }
        return this;
    }

    // 验证配置并添加默认值
    public boolean validateConfig(Map<String, Object> config) {
        invalidConfigItems.clear();
        boolean isValid = true;
        for (ConfigItem<?> item : configItems.values()) {
            item.addDefaultValue(config);
            Object value = config.get(item.getKey());
            String errorMessage = item.validate(value);
            if (errorMessage != null) {
                invalidConfigItems.put(item, errorMessage);
                isValid = false;
            }
        }
        return isValid;
    }

    // 填充默认值
    public Map<String, Object> fillDefaults(Map<String, Object> config) {
        Map<String, Object> filledConfig = new HashMap<>(config);
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
        return filledConfig;
    }

    // 对外开放获取未通过验证的配置项及其错误信息的方法
    public Map<ConfigItem<?>, String> getInvalidConfigItems() {
        return invalidConfigItems;
    }

    public LinkedHashMap<String, ConfigItem<?>> getConfigItems() {
        return configItems;
    }
}
