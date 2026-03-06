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
 * 配置流程提供者接口
 *
 * <p>其他集成实现此接口来提供配置流程功能。
 *
 * <p>实现此接口的集成会被 ecat-core-api 自动发现并展示在配置向导中。
 *
 * <p>Coordinate 会自动从 integrations.yml 获取，无需手动实现。
 *
 * <p>使用示例：
 * <pre>{@code
 * public class DemoConfigFlowIntegration extends IntegrationBase implements ConfigFlowProvider {
 *
 *     @Override
 *     public String getDisplayName() {
 *         return "Demo 设备配置流程";
 *     }
 *
 *     @Override
 *     public String getFlowType() {
 *         return "demo-device-config";
 *     }
 *
 *     @Override
 *     public AbstractConfigFlow createFlow(String flowId, String targetCoordinate) {
 *         return new DemoConfigFlow(flowId, targetCoordinate);
 *     }
 * }
 * }</pre>
 *
 * @author ECAT Core
 */
public interface ConfigFlowProvider {

    /**
     * 获取显示名称
     *
     * <p>用于在配置向导 UI 中展示给用户。
     *
     * @return 显示名称
     */
    String getDisplayName();

    /**
     * 获取流程类型
     *
     * <p>用于区分不同类型的配置流程。
     *
     * @return 流程类型标识
     */
    String getFlowType();

    /**
     * 创建配置流程实例
     *
     * @param flowId 流程实例 ID (通常是 UUID)
     * @return 配置流程实例
     */
    AbstractConfigFlow createFlow(String flowId);
}
