package com.ecat.core.Compatibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ecat.core.Integration.IntegrationInfo;
import com.ecat.core.Version.Version;
import com.ecat.core.Version.VersionRange;

/**
 * 兼容性检查器，检查集成模块之间的版本兼容性
 *
 * 检查项目：
 * - 版本约束是否满足
 * - 依赖版本是否过时
 * - 是否存在潜在的兼容性问题
 *
 * @author coffee
 */
public class CompatibilityChecker {

    /**
     * 兼容性问题
     */
    public static class CompatibilityIssue {
        public enum Severity {
            ERROR,    // 错误：必须解决
            WARNING,  // 警告：建议解决
            INFO      // 信息：仅供参考
        }

        private final Severity severity;
        private final String artifactId;
        private final String message;
        private final String suggestion;
        private final String issueType;

        public CompatibilityIssue(Severity severity, String artifactId,
                String issueType, String message, String suggestion) {
            this.severity = severity;
            this.artifactId = artifactId;
            this.issueType = issueType;
            this.message = message;
            this.suggestion = suggestion;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getMessage() {
            return message;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public String getIssueType() {
            return issueType;
        }

        @Override
        public String toString() {
            return "[" + severity + "] " + artifactId + " (" + issueType + "): "
                + message + (suggestion != null ? " -> " + suggestion : "");
        }
    }

    /**
     * 兼容性检查结果
     */
    public static class CheckResult {
        private final List<CompatibilityIssue> issues;
        private final boolean hasErrors;
        private final boolean hasWarnings;

        public CheckResult(List<CompatibilityIssue> issues) {
            this.issues = issues != null ? issues : new ArrayList<>();
            boolean hasErr = false;
            boolean hasWarn = false;
            for (CompatibilityIssue issue : this.issues) {
                if (issue.getSeverity() == CompatibilityIssue.Severity.ERROR) {
                    hasErr = true;
                } else if (issue.getSeverity() == CompatibilityIssue.Severity.WARNING) {
                    hasWarn = true;
                }
            }
            this.hasErrors = hasErr;
            this.hasWarnings = hasWarn;
        }

        public List<CompatibilityIssue> getIssues() {
            return issues;
        }

        public List<CompatibilityIssue> getErrors() {
            List<CompatibilityIssue> errors = new ArrayList<>();
            for (CompatibilityIssue issue : issues) {
                if (issue.getSeverity() == CompatibilityIssue.Severity.ERROR) {
                    errors.add(issue);
                }
            }
            return errors;
        }

        public List<CompatibilityIssue> getWarnings() {
            List<CompatibilityIssue> warnings = new ArrayList<>();
            for (CompatibilityIssue issue : issues) {
                if (issue.getSeverity() == CompatibilityIssue.Severity.WARNING) {
                    warnings.add(issue);
                }
            }
            return warnings;
        }

        public boolean hasErrors() {
            return hasErrors;
        }

        public boolean hasWarnings() {
            return hasWarnings;
        }

        public boolean isOK() {
            return !hasErrors;
        }
    }

    /**
     * 检查集成列表的兼容性
     *
     * @param integrations 要检查的集成列表
     * @param allAvailable 所有可用的集成（用于版本比较）
     * @return 检查结果
     */
    public CheckResult checkCompatibility(
            List<IntegrationInfo> integrations,
            Map<String, List<IntegrationInfo>> allAvailable) {

        List<CompatibilityIssue> issues = new ArrayList<>();

        // 构建 coordinate 到 IntegrationInfo 的映射
        Map<String, IntegrationInfo> coordinateMap = new HashMap<>();
        for (IntegrationInfo info : integrations) {
            coordinateMap.put(info.getCoordinate(), info);
        }

        // 检查每个集成的依赖
        for (IntegrationInfo info : integrations) {
            checkDependencies(info, coordinateMap, allAvailable, issues);
        }

        // 检查版本是否过时
        for (IntegrationInfo info : integrations) {
            checkVersionOutdated(info, allAvailable, issues);
        }

        return new CheckResult(issues);
    }

    /**
     * 检查依赖兼容性
     */
    private void checkDependencies(
            IntegrationInfo info,
            Map<String, IntegrationInfo> coordinateMap,
            Map<String, List<IntegrationInfo>> allAvailable,
            List<CompatibilityIssue> issues) {

        List<com.ecat.core.Integration.DependencyInfo> dependencyInfoList = info.getDependencyInfoList();
        if (dependencyInfoList == null || dependencyInfoList.isEmpty()) {
            return;
        }

        // 检查每个依赖（包括缺失检查和版本约束检查）
        for (com.ecat.core.Integration.DependencyInfo dep : dependencyInfoList) {
            String depCoordinate = dep.getCoordinate();

            // 检查是否有版本约束
            if (!dep.hasVersionConstraint()) {
                issues.add(new CompatibilityIssue(
                    CompatibilityIssue.Severity.WARNING,
                    info.getArtifactId(),
                    "NO_VERSION_CONSTRAINT",
                    "对依赖 " + depCoordinate + " 没有版本约束",
                    "建议添加版本约束以确保兼容性"
                ));
            }

            IntegrationInfo depInfo = coordinateMap.get(depCoordinate);

            if (depInfo == null) {
                // 依赖缺失
                issues.add(new CompatibilityIssue(
                    CompatibilityIssue.Severity.ERROR,
                    info.getArtifactId(),
                    "MISSING_DEPENDENCY",
                    "缺少依赖 " + depCoordinate,
                    "请安装 " + depCoordinate
                ));
                continue;
            }

            // 检查版本约束
            if (dep.hasVersionConstraint()) {
                String rangeStr = dep.getVersion();
                VersionRange range;
                try {
                    range = VersionRange.parse(rangeStr);
                } catch (Exception e) {
                    issues.add(new CompatibilityIssue(
                        CompatibilityIssue.Severity.ERROR,
                        info.getArtifactId(),
                        "INVALID_VERSION_RANGE",
                        "无效的版本约束: " + rangeStr,
                        "请检查版本约束格式"
                    ));
                    continue;
                }

                Version depVersion = depInfo.getVersionObject();
                if (depVersion == null) {
                    issues.add(new CompatibilityIssue(
                        CompatibilityIssue.Severity.WARNING,
                        info.getArtifactId(),
                        "UNKNOWN_DEPENDENCY_VERSION",
                        "依赖 " + depCoordinate + " 的版本未知",
                        "请确认依赖的版本"
                    ));
                    continue;
                }

                if (!range.satisfies(depVersion)) {
                    issues.add(new CompatibilityIssue(
                        CompatibilityIssue.Severity.ERROR,
                        info.getArtifactId(),
                        "VERSION_CONSTRAINT_VIOLATION",
                        "依赖 " + depCoordinate + " 版本 " + depVersion
                            + " 不满足约束 " + rangeStr,
                        "请升级/降级 " + depCoordinate + " 到满足约束的版本"
                    ));
                }
            }
        }
    }

    /**
     * 检查版本是否过时
     */
    private void checkVersionOutdated(
            IntegrationInfo info,
            Map<String, List<IntegrationInfo>> allAvailable,
            List<CompatibilityIssue> issues) {

        if (allAvailable == null) {
            return;
        }

        List<IntegrationInfo> availableVersions =
            allAvailable.get(info.getCoordinate());

        if (availableVersions == null || availableVersions.isEmpty()) {
            return;
        }

        Version currentVersion = info.getVersionObject();
        if (currentVersion == null) {
            return;
        }

        // 找到最新版本
        Version latestVersion = null;
        for (IntegrationInfo available : availableVersions) {
            Version v = available.getVersionObject();
            if (v != null) {
                if (latestVersion == null || v.compareTo(latestVersion) > 0) {
                    latestVersion = v;
                }
            }
        }

        if (latestVersion != null && latestVersion.compareTo(currentVersion) > 0) {
            // 计算版本差距
            int majorDiff = latestVersion.getMajor() - currentVersion.getMajor();
            int minorDiff = latestVersion.getMinor() - currentVersion.getMinor();
            int patchDiff = latestVersion.getPatch() - currentVersion.getPatch();

            if (majorDiff > 0) {
                // 主版本落后
                issues.add(new CompatibilityIssue(
                    CompatibilityIssue.Severity.WARNING,
                    info.getArtifactId(),
                    "OUTDATED_MAJOR_VERSION",
                    "当前版本 " + currentVersion + "，最新版本 " + latestVersion
                        + "（落后 " + majorDiff + " 个主版本）",
                    "建议评估是否升级到 " + latestVersion
                ));
            } else if (minorDiff > 0) {
                // 次版本落后
                issues.add(new CompatibilityIssue(
                    CompatibilityIssue.Severity.INFO,
                    info.getArtifactId(),
                    "OUTDATED_MINOR_VERSION",
                    "当前版本 " + currentVersion + "，有新版本 " + latestVersion
                        + "（落后 " + minorDiff + " 个次版本）",
                    "可以考虑升级到 " + latestVersion
                ));
            } else if (patchDiff > 0) {
                // 补丁版本落后
                issues.add(new CompatibilityIssue(
                    CompatibilityIssue.Severity.INFO,
                    info.getArtifactId(),
                    "OUTDATED_PATCH_VERSION",
                    "当前版本 " + currentVersion + "，有补丁更新 " + latestVersion
                        + "（落后 " + patchDiff + " 个补丁）",
                    "建议升级到 " + latestVersion + " 以获取bug修复"
                ));
            }
        }
    }

    /**
     * 检查是否存在循环依赖
     */
    public List<CompatibilityIssue> checkCircularDependencies(
            List<IntegrationInfo> integrations) {

        List<CompatibilityIssue> issues = new ArrayList<>();

        // 构建依赖图
        Map<String, Set<String>> graph = new HashMap<>();
        Set<String> allNodes = new HashSet<>();

        for (IntegrationInfo info : integrations) {
            String coordinate = info.getCoordinate();
            allNodes.add(coordinate);
            graph.putIfAbsent(coordinate, new HashSet<>());

            List<com.ecat.core.Integration.DependencyInfo> dependencyInfoList = info.getDependencyInfoList();
            if (dependencyInfoList != null) {
                for (com.ecat.core.Integration.DependencyInfo dep : dependencyInfoList) {
                    String depCoordinate = dep.getCoordinate();
                    graph.get(coordinate).add(depCoordinate);
                    allNodes.add(depCoordinate);
                }
            }
        }

        // 检测循环
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : allNodes) {
            if (detectCycle(node, graph, visited, recursionStack)) {
                // 找到循环，找出参与循环的节点
                Set<String> cycleNodes = new HashSet<>(recursionStack);
                issues.add(new CompatibilityIssue(
                    CompatibilityIssue.Severity.ERROR,
                    cycleNodes.iterator().next(),
                    "CIRCULAR_DEPENDENCY",
                    "检测到循环依赖: " + cycleNodes,
                    "请重构依赖关系以消除循环"
                ));
                break;
            }
        }

        return issues;
    }

    private boolean detectCycle(String node, Map<String, Set<String>> graph,
            Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }

        visited.add(node);
        recursionStack.add(node);

        Set<String> deps = graph.get(node);
        if (deps != null) {
            for (String dep : deps) {
                if (detectCycle(dep, graph, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(node);
        return false;
    }
}
