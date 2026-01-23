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

package com.ecat.core.Dependency;

import com.ecat.core.Version.VersionRange;

/**
 * 版本约束冲突
 * <p>
 * 描述版本求解过程中发现的冲突。
 * </p>
 *
 * @author coffee
 */
public class Conflict {

    private final VersionSolver.ConflictType type;
    private final String packageCoordinate;
    private final VersionRange constraint;
    private final String reason;

    /**
     * 创建冲突描述
     *
     * @param type 冲突类型
     * @param packageCoordinate 包坐标
     * @param constraint 约束条件
     * @param reason 冲突原因
     */
    public Conflict(
            VersionSolver.ConflictType type,
            String packageCoordinate,
            VersionRange constraint,
            String reason) {
        this.type = type;
        this.packageCoordinate = packageCoordinate;
        this.constraint = constraint;
        this.reason = reason;
    }

    /**
     * 获取冲突类型
     *
     * @return 冲突类型
     */
    public VersionSolver.ConflictType getType() {
        return type;
    }

    /**
     * 获取包坐标
     *
     * @return 包坐标（groupId:artifactId）
     */
    public String getPackageCoordinate() {
        return packageCoordinate;
    }

    /**
     * 获取约束条件
     *
     * @return 版本约束
     */
    public VersionRange getConstraint() {
        return constraint;
    }

    /**
     * 获取冲突原因
     *
     * @return 原因描述
     */
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return String.format("Conflict{%s: %s requires %s: %s}",
            type, packageCoordinate, constraint, reason);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Conflict conflict = (Conflict) o;

        return type == conflict.type &&
            packageCoordinate.equals(conflict.packageCoordinate) &&
            constraint.equals(conflict.constraint);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + packageCoordinate.hashCode();
        result = 31 * result + constraint.hashCode();
        return result;
    }
}
