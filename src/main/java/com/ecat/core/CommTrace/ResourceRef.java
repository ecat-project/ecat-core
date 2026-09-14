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

package com.ecat.core.CommTrace;

import lombok.Value;

/**
 * 定位一笔 IO 资源的自描述标识：反向折叠查询（资源→使用者）的参数，也是各库账本条目的
 * 自描述。key 取各库账本键原样（portName / ip:port / clientId …，见 {@link ResourceKind}）。
 *
 * @author coffee
 */
@Value
public class ResourceRef {

    ResourceKind kind;

    String key;
}
