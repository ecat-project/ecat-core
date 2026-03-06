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

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 步骤信息
 * <p>
 * 封装步骤的显示信息，支持未来扩展（如描述、图标等）。
 *
 * @author ECAT Core
 */
@Data
@NoArgsConstructor
public class StepInfo {
    /**
     * 步骤显示名称
     */
    private String displayName;

    /**
     * 步骤描述 (可选)
     */
    private String description;

    /**
     * 步骤图标 (可选)
     */
    private String icon;

    public StepInfo(String displayName) {
        this.displayName = displayName;
    }

    public StepInfo(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 快速创建只包含显示名称的 StepInfo
     *
     * @param displayName 显示名称
     * @return StepInfo 实例
     */
    public static StepInfo of(String displayName) {
        return new StepInfo(displayName);
    }

    /**
     * 快速创建包含显示名称和描述的 StepInfo
     *
     * @param displayName 显示名称
     * @param description 描述
     * @return StepInfo 实例
     */
    public static StepInfo of(String displayName, String description) {
        return new StepInfo(displayName, description);
    }
}
