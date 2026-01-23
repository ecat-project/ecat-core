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
 * 文本长度验证器
 * @author coffee
 */
public class TextLengthValidator implements ConstraintValidator<String> {

    private final int minLength;
    private final int maxLength;

    public TextLengthValidator(int minLength, int maxLength) {
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    @Override
    public boolean validate(String value) {
        if (value == null) {
            return minLength == 0; // 如果允许空字符串
        }
        int length = value.length();
        return length >= minLength && length <= maxLength;
    }

    @Override
    public String getErrorMessage() {
        return "文本长度必须在 " + minLength + " 和 " + maxLength + " 个字符之间";
    }

    public int getMinLength() {
        return minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }
}
