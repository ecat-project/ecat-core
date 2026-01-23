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
 * Class for list size validator
 * @author coffee
 */
public class ListSizeValidator<T> implements ConstraintValidator<List<T>> {
    private final int minSize;
    private final int maxSize;

    public ListSizeValidator(int minSize, int maxSize) {
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    @Override
    public boolean validate(List<T> value) {
        if (value == null) {
            return true;
        }
        int size = value.size();
        return size >= minSize && size <= maxSize;
    }

    @Override
    public String getErrorMessage() {
        return "列表大小必须在 " + minSize + " 到 " + maxSize + " 之间";
    }

    public boolean isSingleSelect() {
        return minSize == 1 && maxSize == 1;
    }
}
