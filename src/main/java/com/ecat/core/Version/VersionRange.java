package com.ecat.core.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 版本范围，用于描述版本约束
 *
 * 支持的语法：
 * - 通配符：* 表示任意版本
 * - 精确版本：1.2.0
 * - 范围约束：>=1.0.0,<2.0.0
 * - 兼容版本（~）：~1.2.0 表示 >=1.2.0,<1.3.0
 * - 主版本兼容（^）：^1.2.0 表示 >=1.2.0,<2.0.0
 * - 或约束：1.2.0 || 2.0.0
 *
 * @author coffee
 */
public class VersionRange {

    private final List<Constraint> constraints;
    private final String originalString;

    /**
     * 单个版本约束
     */
    private static class Constraint {
        final Version version;
        final Operator operator;

        Constraint(Version version, Operator operator) {
            this.version = version;
            this.operator = operator;
        }

        /**
         * 检查版本是否满足约束
         */
        boolean satisfies(Version v) {
            switch (operator) {
                case EXACT:
                    return v.compareTo(version) == 0;
                case GREATER_THAN:
                    return v.compareTo(version) > 0;
                case GREATER_THAN_OR_EQUAL:
                    return v.compareTo(version) >= 0;
                case LESS_THAN:
                    return v.compareTo(version) < 0;
                case LESS_THAN_OR_EQUAL:
                    return v.compareTo(version) <= 0;
                default:
                    return false;
            }
        }

        @Override
        public String toString() {
            return operator.symbol + version;
        }
    }

    /**
     * 操作符枚举
     */
    private enum Operator {
        EXACT("="),
        GREATER_THAN(">"),
        GREATER_THAN_OR_EQUAL(">="),
        LESS_THAN("<"),
        LESS_THAN_OR_EQUAL("<=");

        final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        static Operator fromSymbol(String symbol) {
            for (Operator op : values()) {
                if (op.symbol.equals(symbol)) {
                    return op;
                }
            }
            throw new IllegalArgumentException("未知的操作符: " + symbol);
        }
    }

    /**
     * 私有构造函数
     */
    private VersionRange(List<Constraint> constraints, String originalString) {
        this.constraints = constraints;
        this.originalString = originalString;
    }

    /**
     * 解析版本范围字符串
     *
     * @param rangeString 范围字符串，如 "*"、"^1.2.0"、"~1.2.0"、">=1.0.0,<2.0.0"
     * @return VersionRange实例
     */
    public static VersionRange parse(String rangeString) {
        if (rangeString == null || rangeString.isEmpty()) {
            throw new IllegalArgumentException("版本范围字符串不能为空");
        }

        // 处理通配符 * （任意版本）
        if ("*".equals(rangeString.trim())) {
            // 返回一个接受所有版本的约束范围
            // 使用 0.0.0 作为最小版本，没有上限
            return new VersionRange(new ArrayList<Constraint>(), "*");
        }

        List<Constraint> constraints = new ArrayList<>();

        // 处理或约束 (||)
        String[] orParts = rangeString.split("\\|\\|");
        if (orParts.length > 1) {
            // 或约束：创建多个VersionRange的并集
            // 简化处理：只取第一个约束
            return parse(orParts[0].trim());
        }

        // 处理 ^ 兼容语法（主版本兼容）
        if (rangeString.startsWith("^")) {
            return parseCaretRange(rangeString.substring(1));
        }

        // 处理 ~ 兼容语法（次版本兼容）
        if (rangeString.startsWith("~")) {
            return parseTildeRange(rangeString.substring(1));
        }

        // 处理范围约束 >=1.0.0,<2.0.0
        if (rangeString.contains(",")) {
            return parseRangeConstraint(rangeString);
        }

        // 处理操作符约束 >=1.0.0
        if (rangeString.startsWith(">=") || rangeString.startsWith("<=") ||
            rangeString.startsWith(">") || rangeString.startsWith("<")) {
            return parseOperatorConstraint(rangeString);
        }

        // 精确版本 1.2.0
        Version version = Version.parse(rangeString);
        constraints.add(new Constraint(version, Operator.EXACT));

        return new VersionRange(constraints, rangeString);
    }

    /**
     * 解析 ^ 兼容语法（主版本兼容）
     * ^1.2.0 表示 >=1.2.0,<2.0.0
     * ^1.2 表示 >=1.2.0,<2.0.0
     * ^1 表示 >=1.0.0,<2.0.0
     */
    private static VersionRange parseCaretRange(String versionString) {
        Version version = Version.parse(normalizeVersionString(versionString));
        List<Constraint> constraints = new ArrayList<>();

        // 下界：>= 当前版本
        constraints.add(new Constraint(version, Operator.GREATER_THAN_OR_EQUAL));

        // 上界：< 主版本+1.0.0
        Version maxVersion = Version.of(version.getMajor() + 1, 0, 0);
        constraints.add(new Constraint(maxVersion, Operator.LESS_THAN));

        return new VersionRange(constraints, "^" + versionString);
    }

    /**
     * 解析 ~ 兼容语法（次版本兼容）
     * ~1.2.0 表示 >=1.2.0,<1.3.0
     * ~1.2 表示 >=1.2.0,<1.3.0
     * ~1 表示 >=1.0.0,<2.0.0
     */
    private static VersionRange parseTildeRange(String versionString) {
        Version version = Version.parse(normalizeVersionString(versionString));
        List<Constraint> constraints = new ArrayList<>();

        // 下界：>= 当前版本
        constraints.add(new Constraint(version, Operator.GREATER_THAN_OR_EQUAL));

        // 上界：如果是 ~1.2.x，则为 <1.3.0；如果是 ~1.x，则为 <2.0.0
        Version maxVersion;
        if (version.getMinor() > 0 || version.getPatch() > 0) {
            // ~1.2.0 -> <1.3.0
            maxVersion = Version.of(version.getMajor(), version.getMinor() + 1, 0);
        } else {
            // ~1 -> <2.0.0
            maxVersion = Version.of(version.getMajor() + 1, 0, 0);
        }
        constraints.add(new Constraint(maxVersion, Operator.LESS_THAN));

        return new VersionRange(constraints, "~" + versionString);
    }

    /**
     * 规范化版本字符串，补全缺失的部分
     * "1" -> "1.0.0"
     * "1.2" -> "1.2.0"
     * "1.2.0" -> "1.2.0"
     */
    private static String normalizeVersionString(String versionString) {
        String[] parts = versionString.split("\\.");
        switch (parts.length) {
            case 1:
                return versionString + ".0.0";
            case 2:
                return versionString + ".0";
            default:
                return versionString;
        }
    }

    /**
     * 解析范围约束 >=1.0.0,<2.0.0
     */
    private static VersionRange parseRangeConstraint(String rangeString) {
        List<Constraint> constraints = new ArrayList<>();
        String[] parts = rangeString.split(",");

        for (String part : parts) {
            part = part.trim();
            if (part.startsWith(">=")) {
                Version version = Version.parse(part.substring(2));
                constraints.add(new Constraint(version, Operator.GREATER_THAN_OR_EQUAL));
            } else if (part.startsWith("<=")) {
                Version version = Version.parse(part.substring(2));
                constraints.add(new Constraint(version, Operator.LESS_THAN_OR_EQUAL));
            } else if (part.startsWith(">")) {
                Version version = Version.parse(part.substring(1));
                constraints.add(new Constraint(version, Operator.GREATER_THAN));
            } else if (part.startsWith("<")) {
                Version version = Version.parse(part.substring(1));
                constraints.add(new Constraint(version, Operator.LESS_THAN));
            } else {
                throw new IllegalArgumentException("未知的约束格式: " + part);
            }
        }

        return new VersionRange(constraints, rangeString);
    }

    /**
     * 解析操作符约束 >=1.0.0
     */
    private static VersionRange parseOperatorConstraint(String rangeString) {
        List<Constraint> constraints = new ArrayList<>();

        if (rangeString.startsWith(">=")) {
            Version version = Version.parse(rangeString.substring(2));
            constraints.add(new Constraint(version, Operator.GREATER_THAN_OR_EQUAL));
        } else if (rangeString.startsWith("<=")) {
            Version version = Version.parse(rangeString.substring(2));
            constraints.add(new Constraint(version, Operator.LESS_THAN_OR_EQUAL));
        } else if (rangeString.startsWith(">")) {
            Version version = Version.parse(rangeString.substring(1));
            constraints.add(new Constraint(version, Operator.GREATER_THAN));
        } else if (rangeString.startsWith("<")) {
            Version version = Version.parse(rangeString.substring(1));
            constraints.add(new Constraint(version, Operator.LESS_THAN));
        }

        return new VersionRange(constraints, rangeString);
    }

    /**
     * 检查版本是否在范围内
     *
     * @param version 要检查的版本
     * @return 如果在范围内返回true
     */
    public boolean satisfies(Version version) {
        // 如果没有约束（通配符 * 的情况），接受所有版本
        if (constraints.isEmpty()) {
            return true;
        }
        // 所有约束都必须满足（AND关系）
        for (Constraint constraint : constraints) {
            if (!constraint.satisfies(version)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取范围内可用的最大版本
     *
     * @param availableVersions 可用版本列表
     * @return 最大可用版本，如果没有则返回null
     */
    public Version getMaxSatisfiedVersion(Iterable<Version> availableVersions) {
        Version maxVersion = null;
        for (Version version : availableVersions) {
            if (satisfies(version)) {
                if (maxVersion == null || version.compareTo(maxVersion) > 0) {
                    maxVersion = version;
                }
            }
        }
        return maxVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionRange that = (VersionRange) o;
        return Objects.equals(originalString, that.originalString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalString);
    }

    @Override
    public String toString() {
        return originalString;
    }
}
