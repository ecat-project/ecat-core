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

import java.util.Set;

/**
 * Class for string enum validator
 * @author coffee
 */
public class StringEnumValidator implements ConstraintValidator<String> {
    private final Set<String> validValues;

    public StringEnumValidator(Set<String> validValues) {
        this.validValues = validValues;
    }

    @Override
    public boolean validate(String value) {
        return validValues.contains(value);
    }

    @Override
    public String getErrorMessage() {
        return "字符串值必须是 " + validValues + " 中的一个";
    }

    public Set<String> getValidValues() {
        return validValues;
    }
}
