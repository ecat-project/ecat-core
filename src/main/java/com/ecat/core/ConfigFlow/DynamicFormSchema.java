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

import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

/**
 * 动态表单 Schema 函数式接口
 *
 * <p>用于在 {@link com.ecat.core.ConfigFlow.AbstractConfigFlow#show_form} 方法中
 * 提供动态生成表单 Schema 的逻辑。
 *
 * <p>实现此接口可以根据上下文信息（如已填写的表单数据、错误信息等）
 * 动态生成配置定义。
 *
 * @author ECAT Core
 */
@FunctionalInterface
public interface DynamicFormSchema {

    /**
     * 根据上下文生成表单 Schema
     *
     * @param context 表单上下文，包含步骤 ID、表单数据、错误信息等
     * @return 配置定义
     */
    ConfigDefinition generateSchema(FormContext context);
}
