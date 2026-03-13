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

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Flow 注册信息
 * <p>
 * 存储类定义和能力信息，用于创建实例和查询。
 * <p>
 * 设计说明：
 * <ul>
 *   <li>注册时从实例提取类定义和能力信息</li>
 *   <li>后续通过类定义创建新实例，保证状态隔离</li>
 *   <li>能力查询基于缓存，无需创建实例</li>
 * </ul>
 *
 * @author coffee
 */
@Getter
@AllArgsConstructor
public class FlowRegistration {
    /**
     * 集成标识 (groupId:artifactId)
     */
    private final String coordinate;

    /**
     * Flow 类定义
     */
    private final Class<? extends AbstractConfigFlow> flowClass;

    /**
     * 是否支持用户入口
     */
    private final boolean userStepSupported;

    /**
     * 是否支持重配置入口
     */
    private final boolean reconfigureStepSupported;

    /**
     * 是否支持发现入口
     */
    private final boolean discoveryStepSupported;

    /**
     * 检查是否支持用户入口 (别名方法，与设计文档一致)
     */
    public boolean hasUserStep() {
        return userStepSupported;
    }

    /**
     * 检查是否支持重配置入口 (别名方法，与设计文档一致)
     */
    public boolean hasReconfigureStep() {
        return reconfigureStepSupported;
    }

    /**
     * 检查是否支持发现入口 (别名方法，与设计文档一致)
     */
    public boolean hasDiscoveryStep() {
        return discoveryStepSupported;
    }
}
