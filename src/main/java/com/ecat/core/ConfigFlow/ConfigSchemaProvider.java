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

/**
 * Schema 提供者接口
 * <p>
 * 类名作为 Schema 标识，通过 {@link #createSchema()} 方法创建 Schema 实例。
 * 用于实现 Schema 的复用和引用机制。
 *
 * @author coffee
 */
public interface ConfigSchemaProvider {

    /**
     * 创建 Schema
     *
     * @return Schema 实例
     */
    ConfigSchema createSchema();
}
