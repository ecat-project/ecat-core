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

import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 表单上下文
 *
 * <p>用于在生成动态表单 Schema 时提供上下文信息。
 *
 * <p>包含当前步骤 ID、表单数据和错误信息。
 *
 * @author coffee
 */
@Getter
public class FormContext {

    /**
     * 当前步骤 ID
     */
    private final String stepId;

    /**
     * 表单数据映射，key 为字段名，value 为字段值
     */
    private final Map<String, Object> data;

    /**
     * 错误信息映射，key 为字段名，value 为错误描述
     */
    private final Map<String, Object> errors;

    /**
     * 构造函数
     *
     * @param stepId 当前步骤 ID
     * @param data 表单数据
     * @param errors 错误信息
     */
    public FormContext(String stepId, Map<String, Object> data, Map<String, Object> errors) {
        this.stepId = stepId;
        this.data = data != null ? new HashMap<>(data) : Collections.emptyMap();
        this.errors = errors != null ? new HashMap<>(errors) : Collections.emptyMap();
    }

    /**
     * 获取字段值
     *
     * @param fieldName 字段名
     * @return 字段值，不存在时返回 null
     */
    public Object getValue(String fieldName) {
        return data.get(fieldName);
    }

    /**
     * 获取字段错误
     *
     * @param fieldName 字段名
     * @return 错误描述，不存在时返回 null
     */
    public String getError(String fieldName) {
        Object error = errors.get(fieldName);
        return error != null ? error.toString() : null;
    }

    /**
     * 检查是否有错误
     *
     * @return 如果有错误返回 true，否则返回 false
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * 检查指定字段是否有错误
     *
     * @param fieldName 字段名
     * @return 如果该字段有错误返回 true，否则返回 false
     */
    public boolean hasError(String fieldName) {
        return errors.containsKey(fieldName);
    }
}
