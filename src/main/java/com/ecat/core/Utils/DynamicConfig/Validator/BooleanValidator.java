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

package com.ecat.core.Utils.DynamicConfig.Validator;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

/**
 * 布尔值验证器
 * 
 * @author coffee
 */
public class BooleanValidator implements ConstraintValidator<Boolean> {

    @Override
    public boolean validate(Boolean value) {
        return value != null; // 布尔值只要不是null都有效
    }

    @Override
    public String getErrorMessage() {
        return "值必须为布尔类型";
    }
}
