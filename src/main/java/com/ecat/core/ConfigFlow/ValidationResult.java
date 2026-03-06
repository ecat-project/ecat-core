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
 * 验证结果容器
 *
 * <p>用于封装表单验证结果，包含验证状态和错误信息。
 *
 * @author ECAT Core
 */
@Getter
public class ValidationResult {

    /**
     * 验证是否通过
     */
    private final boolean valid;

    /**
     * 错误信息映射，key 为字段名，value 为错误描述
     */
    private final Map<String, String> errors;

    /**
     * 私有构造函数
     *
     * @param valid 验证是否通过
     * @param errors 错误信息
     */
    private ValidationResult(boolean valid, Map<String, String> errors) {
        this.valid = valid;
        this.errors = errors != null ? new HashMap<>(errors) : new HashMap<>();
    }

    /**
     * 创建验证通过的结果
     *
     * @return 验证通过的结果
     */
    public static ValidationResult valid() {
        return new ValidationResult(true, Collections.emptyMap());
    }

    /**
     * 创建验证失败的结果
     *
     * @param errors 错误信息映射
     * @return 验证失败的结果
     */
    public static ValidationResult invalid(Map<String, String> errors) {
        return new ValidationResult(false, errors);
    }

    /**
     * 创建单字段验证失败的结果
     *
     * @param fieldName 字段名
     * @param error 错误描述
     * @return 验证失败的结果
     */
    public static ValidationResult invalid(String fieldName, String error) {
        Map<String, String> errors = new HashMap<>();
        errors.put(fieldName, error);
        return new ValidationResult(false, errors);
    }
}
