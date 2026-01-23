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

import java.util.List;

/**
 * Class for list validator
 * @author coffee
 */
public class ListValidator<T> implements ConstraintValidator<List<T>> {
    private final ConstraintValidator<T> elementValidator;

    public ListValidator(ConstraintValidator<T> elementValidator) {
        this.elementValidator = elementValidator;
    }

    @Override
    public boolean validate(List<T> value) {
        if (value == null) {
            return true;
        }
        for (T element : value) {
            if (!elementValidator.validate(element)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getErrorMessage() {
        return "列表中的元素不满足验证条件: " + elementValidator.getErrorMessage();
    }

    public ConstraintValidator<T> getElementValidator() {
        return elementValidator;
    }
}
