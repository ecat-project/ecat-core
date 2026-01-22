package com.ecat.core.Utils.DynamicConfig;

import java.util.LinkedHashMap;

public class ConfigItemBuilder {
        private final LinkedHashMap<String, ConfigItem<?>> items = new LinkedHashMap<>();

    public ConfigItemBuilder add(ConfigItem<?> item) {
        items.put(item.getKey(), item);
        return this;
    }

    public ConfigItem<?>[] build() {
        return items.values().toArray(new ConfigItem[0]);
    }
}
