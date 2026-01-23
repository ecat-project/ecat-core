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

import java.util.LinkedHashMap;

/**
 * A builder class for creating configuration items.
 * This class follows the builder pattern to facilitate the creation of configuration items.
 * @author coffee
 */
public class ConfigItemBuilder {
    // A LinkedHashMap to store configuration items, maintaining insertion order
        private final LinkedHashMap<String, ConfigItem<?>> items = new LinkedHashMap<>();

    public ConfigItemBuilder add(ConfigItem<?> item) {
        items.put(item.getKey(), item);
        return this;
    }

    public ConfigItem<?>[] build() {
        return items.values().toArray(new ConfigItem[0]);
    }
}
