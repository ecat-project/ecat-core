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

import com.ecat.core.ConfigFlow.ConfigItem.AbstractConfigItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置 Schema - 字段定义集合
 * <p>
 * 所有字段（包括嵌套和引用）统一通过 {@link #addField(AbstractConfigItem)} 添加。
 * 嵌套和引用 Schema 通过 {@link com.ecat.core.ConfigFlow.ConfigItem.SchemaConfigItem} 实现（也是一种字段类型）。
 *
 * @author coffee
 */
public class ConfigSchema {

    /** 字段列表（唯一数据结构） */
    private final List<AbstractConfigItem<?>> fields = new ArrayList<>();

    // ========== 添加字段 ==========

    /**
     * 添加字段
     *
     * @param field 字段定义
     * @return this，支持链式调用
     */
    public ConfigSchema addField(AbstractConfigItem<?> field) {
        this.fields.add(field);
        return this;
    }

    // ========== 获取字段 ==========

    /**
     * 获取所有字段（不可修改）
     *
     * @return 字段列表
     */
    public List<AbstractConfigItem<?>> getFields() {
        return Collections.unmodifiableList(fields);
    }

    // ========== 验证 ==========

    /**
     * 验证输入数据
     *
     * @param input 输入数据
     * @return 错误映射，key 为字段名，value 为错误信息；无错误返回空 Map
     */
    public Map<String, String> validate(Map<String, Object> input) {
        Map<String, String> errors = new HashMap<>();

        for (AbstractConfigItem<?> field : fields) {
            String error = field.validate(input.get(field.getKey()));
            if (error != null) {
                errors.put(field.getKey(), error);
            }
        }

        return errors;
    }

    /**
     * 添加默认值
     * <p>
     * 如果配置中不存在该键且有默认值，则添加默认值。
     *
     * @param config 配置映射
     */
    public void addDefaults(Map<String, Object> config) {
        for (AbstractConfigItem<?> field : fields) {
            field.addDefaultValue(config);
        }
    }
}
