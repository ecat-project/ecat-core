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
