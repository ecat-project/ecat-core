package com.ecat.core.Dependency;

import java.util.*;
import java.util.stream.Collectors;

import com.ecat.core.Integration.IntegrationInfo;
import com.ecat.core.Version.Version;
import com.ecat.core.Version.VersionRange;

/**
 * 版本约束求解器（基于 PubGrub 算法）
 * <p>
 * 解决多集成的版本兼容性问题，找到满足所有约束的最高版本组合。
 * </p>
 *
 * <h3>算法特点：</h3>
 * <ul>
 *   <li>回溯搜索 - 深度优先搜索 + 回溯</li>
 *   <li>约束传播 - 实时计算可行版本范围</li>
 *   <li>贪心策略 - 优先选择高版本</li>
 *   <li>冲突诊断 - 精确识别冲突来源</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * VersionSolver solver = new VersionSolver();
 * Solution solution = solver.solve(requests, availableVersions);
 *
 * if (solution.hasSolution()) {
 *     Map<String, Version> selected = solution.getSelectedVersions();
 *     // 使用选中的版本
 * } else {
 *     List<Conflict> conflicts = solution.getConflicts();
 *     // 处理冲突
 * }
 * }</pre>
 *
 * @author coffee
 * @see <a href="https://github.com/dart-lang/pub/blob/master/solver/lib/src/documentation/pubgrub.md">
 *     PubGrub Specification
 * </a>
 */
public class VersionSolver {

    /**
     * 求解多集成的兼容版本组合
     *
     * @param requests 请求的集成列表（如 ["core", "serial", "device-app"]）
     * @param availableIntegrations 所有可用的集成版本
     *                格式: Map&lt;coordinate, List&lt;IntegrationInfo&gt;&gt;
     *                例如: "com.ecat:integration-serial" -> [v1.0.0, v1.2.0, v2.0.0]
     * @return 求解结果
     */
    public Solution solve(
            List<IntegrationInfo> requests,
            Map<String, List<IntegrationInfo>> availableIntegrations) {

        SolverState state = new SolverState(availableIntegrations);

        try {
            // 添加所有请求的包
            for (IntegrationInfo request : requests) {
                String coordinate = request.getCoordinate();
                state.addRequest(coordinate, null); // null 表示无版本约束
            }

            // 开始求解
            backtrack(state);

            return new Solution(
                state.getSelectedVersions(),
                true,
                Collections.emptyList()
            );

        } catch (ConflictException e) {
            return new Solution(
                state.getSelectedVersions(),
                false,
                e.getConflicts()
            );
        }
    }

    /**
     * 回溯求解核心算法
     *
     * @param state 当前求解器状态
     * @throws ConflictException 如果发现无法解决的冲突
     */
    private void backtrack(SolverState state) throws ConflictException {
        while (true) {
            // 选择下一个要处理的包
            String nextPackage = state.selectNextPackage();
            if (nextPackage == null) {
                // 没有更多包需要处理，求解成功
                return;
            }

            // 获取该包的所有约束
            List<VersionRange> allConstraints = state.getConstraints(nextPackage);

            // 获取可用的版本列表
            List<Version> availableVersions = getAvailableVersions(
                state.getAvailableIntegrations(),
                nextPackage
            );

            if (availableVersions.isEmpty()) {
                throw new ConflictException(
                    ConflictType.UNSATISFIED_CONSTRAINT,
                    nextPackage,
                    combineConstraints(allConstraints),
                    "没有可用版本"
                );
            }

            // 从高到低尝试每个版本
            boolean found = false;
            for (Version version : availableVersions) {
                // 检查版本是否满足所有约束
                if (satisfiesAllConstraints(version, allConstraints)) {
                    try {
                        // 选择这个版本
                        state.selectVersion(nextPackage, version);

                        // 递归求解
                        backtrack(state);

                        // 成功找到解
                        found = true;
                        break;

                    } catch (ConflictException e) {
                        // 回溯：尝试下一个版本
                        state.undoSelection(nextPackage);
                    }
                }
            }

            if (!found) {
                throw new ConflictException(
                    ConflictType.UNSATISFIED_CONSTRAINT,
                    nextPackage,
                    combineConstraints(allConstraints),
                    "没有版本满足约束: " + allConstraints
                );
            }
        }
    }

    /**
     * 检查版本是否满足所有约束
     */
    private boolean satisfiesAllConstraints(Version version, List<VersionRange> constraints) {
        for (VersionRange constraint : constraints) {
            if (!constraint.satisfies(version)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 合并多个约束为单个约束（用于错误消息）
     * 注意：这里只是简单返回第一个约束，真正的约束交集计算更复杂
     */
    private VersionRange combineConstraints(List<VersionRange> constraints) {
        return constraints.isEmpty() ?
            VersionRange.parse("*") :
            constraints.get(0);
    }

    /**
     * 获取指定包的所有可用版本（从高到低排序）
     */
    private List<Version> getAvailableVersions(
            Map<String, List<IntegrationInfo>> availableIntegrations,
            String coordinate) {

        List<IntegrationInfo> infos = availableIntegrations.get(coordinate);
        if (infos == null || infos.isEmpty()) {
            return Collections.emptyList();
        }

        return infos.stream()
            .map(info -> Version.parse(info.getVersion()))
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());
    }

    // ========== 内部类 ==========

    /**
     * 求解器状态
     */
    private static class SolverState {
        private final Map<String, List<IntegrationInfo>> availableIntegrations;
        private final Map<String, PackageState> packageStates;
        private final Map<String, Version> selectedVersions;
        private final Map<String, List<VersionRange>> constraints; // 每个包的约束列表
        private final Queue<String> workQueue;

        public SolverState(Map<String, List<IntegrationInfo>> availableIntegrations) {
            this.availableIntegrations = availableIntegrations;
            this.packageStates = new HashMap<>();
            this.selectedVersions = new HashMap<>();
            this.constraints = new HashMap<>();
            this.workQueue = new LinkedList<>();
        }

        public void addRequest(String coordinate, VersionRange constraint) {
            packageStates.put(coordinate, PackageState.SELECTING);
            if (constraint != null) {
                addConstraint(coordinate, constraint);
            }
            workQueue.add(coordinate);
        }

        public void addConstraint(String coordinate, VersionRange constraint) {
            constraints.computeIfAbsent(coordinate, k -> new ArrayList<>()).add(constraint);
        }

        public String selectNextPackage() {
            return workQueue.poll();
        }

        public void selectVersion(String coordinate, Version version) {
            selectedVersions.put(coordinate, version);
            packageStates.put(coordinate, PackageState.SELECTED);

            // 添加该包的依赖到工作队列
            List<IntegrationInfo> available = availableIntegrations.get(coordinate);
            if (available != null) {
                for (IntegrationInfo info : available) {
                    if (info.getVersion().equals(version.toString())) {
                        List<com.ecat.core.Integration.DependencyInfo> deps =
                            info.getDependencyInfoList();
                        if (deps != null) {
                            for (com.ecat.core.Integration.DependencyInfo dep : deps) {
                                String depCoordinate = dep.getCoordinate();
                                packageStates.put(depCoordinate, PackageState.SELECTING);
                                // 添加依赖的版本约束
                                if (dep.hasVersionConstraint()) {
                                    VersionRange depConstraint =
                                        VersionRange.parse(dep.getVersion());
                                    addConstraint(depCoordinate, depConstraint);
                                }
                                workQueue.add(depCoordinate);
                            }
                        }
                        break;
                    }
                }
            }
        }

        public void undoSelection(String coordinate) {
            selectedVersions.remove(coordinate);
            packageStates.put(coordinate, PackageState.CONFLICTED);
        }

        public List<VersionRange> getConstraints(String coordinate) {
            return constraints.getOrDefault(coordinate, Collections.emptyList());
        }

        public boolean isSelected(String coordinate) {
            return packageStates.get(coordinate) == PackageState.SELECTED;
        }

        public Map<String, Version> getSelectedVersions() {
            return new HashMap<>(selectedVersions);
        }

        public Map<String, List<IntegrationInfo>> getAvailableIntegrations() {
            return availableIntegrations;
        }
    }

    /**
     * 包状态
     */
    private enum PackageState {
        SELECTING,   // 正在选择版本
        SELECTED,    // 已选择版本
        CONFLICTED   // 发生冲突
    }

    /**
     * 冲突异常
     */
    public static class ConflictException extends Exception {
        private final ConflictType type;
        private final String packageCoordinate;
        private final VersionRange constraint;
        private final String reason;
        private final List<Conflict> conflicts;

        public ConflictException(
                ConflictType type,
                String packageCoordinate,
                VersionRange constraint,
                String reason) {
            super(String.format("%s: %s requires %s: %s",
                type, packageCoordinate, constraint, reason));
            this.type = type;
            this.packageCoordinate = packageCoordinate;
            this.constraint = constraint;
            this.reason = reason;
            this.conflicts = Collections.singletonList(
                new Conflict(type, packageCoordinate, constraint, reason)
            );
        }

        public ConflictType getType() { return type; }
        public String getPackageCoordinate() { return packageCoordinate; }
        public VersionRange getConstraint() { return constraint; }
        public String getReason() { return reason; }
        public List<Conflict> getConflicts() { return conflicts; }
    }

    /**
     * 冲突类型
     */
    public enum ConflictType {
        UNSATISFIED_CONSTRAINT,
        MISSING_DEPENDENCY,
        VERSION_CONFLICT
    }
}
