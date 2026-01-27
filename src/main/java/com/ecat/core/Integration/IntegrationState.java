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

package com.ecat.core.Integration;

/**
 * 集成状态枚举
 *
 * <p>定义 ECAT 系统中集成的生命周期状态，用于统一管理集成的安装、启用、停用、卸载等操作。
 *
 * @author coffee
 * @see <a href="../../../../../../../state.md">状态设计文档</a>
 */
public enum IntegrationState {
    /**
     * 运行中 - 功能正常加载和使用
     */
    RUNNING("运行中", "功能正常运行"),

    /**
     * 已停止 - 暂时停止，可恢复
     */
    STOPPED("已停止", "暂时停止，可恢复"),

    /**
     * 新增待重启 - 新安装，需调整类加载器层级，重启生效
     */
    PENDING_ADDED("新增待重启", "新安装，需重启生效"),

    /**
     * 升级待重启 - 升级中，旧版本继续运行，等待重启
     */
    PENDING_UPGRADE("升级待重启", "升级中，旧版本继续运行，等待重启"),

    /**
     * 卸载待重启 - 已逻辑删除(onRelease已调用)，需重启清理
     */
    PENDING_REMOVED("卸载待重启", "已逻辑删除，需重启清理");

    private final String name;
    private final String description;

    IntegrationState(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 检查是否处于 PENDING_* 状态（锁定状态）
     *
     * @return 如果处于待重启状态返回 true
     */
    public boolean isPending() {
        return this == PENDING_ADDED || this == PENDING_UPGRADE || this == PENDING_REMOVED;
    }

    /**
     * 检查是否处于运行状态
     *
     * @return 如果处于运行状态返回 true
     */
    public boolean isRunning() {
        return this == RUNNING;
    }

    /**
     * 检查是否处于停止状态
     *
     * @return 如果处于停止状态返回 true
     */
    public boolean isStopped() {
        return this == STOPPED;
    }

    @Override
    public String toString() {
        return name + " (" + description + ")";
    }
}
