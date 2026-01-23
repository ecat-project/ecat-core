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

/**
 * IntegerValidator 用于验证整数值是否在指定范围内
 * 
 * <p>
 * 主要功能：
 * <ul>
 * <li>验证整数值是否在指定的最小值和最大值之间</li>
 * <li>提供错误消息以便于调试</li>
 * </ul>
 * 
 * @author coffee
 */
public class IntegerValidator  implements ConstraintValidator<Integer> {
    private final int minValue;
    private final int maxValue;

    public IntegerValidator(int minValue, int maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    @Override
    public boolean validate(Integer value) {
        return value != null && value >= minValue && value <= maxValue;
    }

    @Override
    public String getErrorMessage() {
        return "整数值必须在 " + minValue + " 到 " + maxValue + " 之间";
    }
    
}
