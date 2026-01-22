package com.ecat.core.Dependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.ecat.core.Integration.IntegrationInfo;
import com.ecat.core.Version.Version;
import com.ecat.core.Version.VersionRange;

/**
 * 依赖解析器，用于解析集成模块之间的依赖关系
 *
 * 功能：
 * - 解析依赖关系并确定加载顺序
 * - 检测版本冲突
 * - 检测循环依赖
 * - 选择满足所有约束的版本
 *
 * @author coffee
 */
public class DependencyResolver {

    /**
     * 选中的集成信息（包含IntegrationInfo和选择原因）
     */
    private static class SelectedIntegration {
        final IntegrationInfo integrationInfo;
        final String reason;

        SelectedIntegration(IntegrationInfo integrationInfo, String reason) {
            this.integrationInfo = integrationInfo;
            this.reason = reason;
        }

        String getCoordinate() {
            return integrationInfo.getCoordinate();
        }

        Version getVersion() {
            return integrationInfo.getVersionObject();
        }
    }

    /**
     * 依赖解析结果
     */
    public static class ResolutionResult {
        private final Map<String, SelectedVersion> selectedVersions;
        private final List<ConflictException> conflicts;
        private final List<IntegrationInfo> loadOrder;
        private final List<String> unresolvedDependencies;

        public ResolutionResult(
                Map<String, SelectedVersion> selectedVersions,
                List<ConflictException> conflicts,
                List<IntegrationInfo> loadOrder,
                List<String> unresolvedDependencies) {
            this.selectedVersions = selectedVersions != null
                ? selectedVersions
                : new HashMap<>();
            this.conflicts = conflicts != null ? conflicts : new ArrayList<>();
            this.loadOrder = loadOrder != null ? loadOrder : new ArrayList<>();
            this.unresolvedDependencies = unresolvedDependencies != null
                ? unresolvedDependencies
                : new ArrayList<>();
        }

        public Map<String, SelectedVersion> getSelectedVersions() {
            return selectedVersions;
        }

        public List<ConflictException> getConflicts() {
            return conflicts;
        }

        public List<IntegrationInfo> getLoadOrder() {
            return loadOrder;
        }

        public List<String> getUnresolvedDependencies() {
            return unresolvedDependencies;
        }

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }

        public boolean hasUnresolvedDependencies() {
            return !unresolvedDependencies.isEmpty();
        }

        public boolean isSuccess() {
            return !hasConflicts() && !hasUnresolvedDependencies();
        }
    }

    /**
     * 选中的版本信息
     */
    public static class SelectedVersion {
        private final String artifactId;
        private final Version version;
        private final String reason;

        public SelectedVersion(String artifactId, Version version, String reason) {
            this.artifactId = artifactId;
            this.version = version;
            this.reason = reason;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public Version getVersion() {
            return version;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return artifactId + "@" + version + " (" + reason + ")";
        }
    }

    /**
     * 冲突异常信息
     */
    public static class ConflictException {
        public enum ConflictType {
            VERSION_CONFLICT,          // 版本冲突
            CIRCULAR_DEPENDENCY,       // 循环依赖
            MISSING_DEPENDENCY,        // 缺失依赖
            UNSATISFIED_CONSTRAINT     // 无法满足的版本约束
        }

        private final ConflictType type;
        private final String artifactId;
        private final String message;
        private final String suggestion;

        public ConflictException(ConflictType type, String artifactId,
                String message, String suggestion) {
            this.type = type;
            this.artifactId = artifactId;
            this.message = message;
            this.suggestion = suggestion;
        }

        public ConflictType getType() {
            return type;
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

        @Override
        public String toString() {
            return "[" + type + "] " + artifactId + ": " + message
                + (suggestion != null ? " -> " + suggestion : "");
        }
    }

    /**
     * 解析依赖关系，返回加载顺序
     *
     * @param availableIntegrations 所有可用的集成列表
     * @param rootIntegrations 根集成（用户启用的集成）
     * @return 解析结果
     */
    public ResolutionResult resolve(
            List<IntegrationInfo> availableIntegrations,
            List<IntegrationInfo> rootIntegrations) {

        List<ConflictException> conflicts = new ArrayList<>();
        List<String> unresolvedDependencies = new ArrayList<>();

        // 构建 coordinate 到 IntegrationInfo 的映射
        Map<String, List<IntegrationInfo>> coordinateToVersions = new HashMap<>();
        for (IntegrationInfo info : availableIntegrations) {
            coordinateToVersions
                .computeIfAbsent(info.getCoordinate(), k -> new ArrayList<>())
                .add(info);
        }

        // 1. 选择版本并处理依赖
        Map<String, SelectedIntegration> selectedIntegrations = new HashMap<>();
        Set<String> processing = new HashSet<>();
        Set<String> processed = new HashSet<>();

        for (IntegrationInfo root : rootIntegrations) {
            selectIntegration(root.getCoordinate(), null, root,
                coordinateToVersions, selectedIntegrations,
                processing, processed, conflicts);
        }

        // 如果有冲突，直接返回
        if (!conflicts.isEmpty()) {
            return new ResolutionResult(buildSelectedVersions(selectedIntegrations),
                conflicts, Collections.emptyList(), unresolvedDependencies);
        }

        // 2. 构建依赖图并检测循环依赖
        DependencyGraph graph = buildDependencyGraph(selectedIntegrations);

        // 3. 检测循环依赖
        if (graph.hasCycle()) {
            for (String node : graph.getNodes()) {
                if (graph.getInDegree(node) > 0 && graph.getOutDegree(node) > 0) {
                    // 可能参与循环的节点
                    conflicts.add(new ConflictException(
                        ConflictException.ConflictType.CIRCULAR_DEPENDENCY,
                        node,
                        "检测到循环依赖",
                        "请检查依赖关系"
                    ));
                }
            }
            if (!conflicts.isEmpty()) {
                return new ResolutionResult(buildSelectedVersions(selectedIntegrations),
                    conflicts, Collections.emptyList(), unresolvedDependencies);
            }
        }

        // 4. 拓扑排序确定加载顺序
        List<IntegrationInfo> loadOrder = topologicalSort(graph, selectedIntegrations, conflicts);

        return new ResolutionResult(buildSelectedVersions(selectedIntegrations),
            conflicts, loadOrder, unresolvedDependencies);
    }

    /**
     * 递归选择满足约束的集成
     */
    private void selectIntegration(
            String coordinate,
            VersionRange constraint,
            IntegrationInfo requestedBy,
            Map<String, List<IntegrationInfo>> coordinateToVersions,
            Map<String, SelectedIntegration> selectedIntegrations,
            Set<String> processing,
            Set<String> processed,
            List<ConflictException> conflicts) {

        // 检测循环依赖
        if (processing.contains(coordinate)) {
            conflicts.add(new ConflictException(
                ConflictException.ConflictType.CIRCULAR_DEPENDENCY,
                coordinate,
                "检测到循环依赖: " + coordinate,
                "请检查依赖关系，移除循环引用"
            ));
            return;
        }

        // 已经处理过
        if (processed.contains(coordinate)) {
            // 检查版本约束是否满足
            SelectedIntegration selected = selectedIntegrations.get(coordinate);
            if (constraint != null && selected != null) {
                Version selectedVersion = selected.getVersion();
                if (selectedVersion != null && !constraint.satisfies(selectedVersion)) {
                    conflicts.add(new ConflictException(
                        ConflictException.ConflictType.VERSION_CONFLICT,
                        coordinate,
                        "版本冲突: " + coordinate + " 约束 " + constraint
                            + " 但已选版本 " + selectedVersion,
                        "调整版本约束或更新集成版本"
                    ));
                }
            }
            return;
        }

        processing.add(coordinate);

        // 获取所有可用版本
        List<IntegrationInfo> availableVersions =
            coordinateToVersions.get(coordinate);

        if (availableVersions == null || availableVersions.isEmpty()) {
            // 缺失依赖
            conflicts.add(new ConflictException(
                ConflictException.ConflictType.MISSING_DEPENDENCY,
                coordinate,
                "找不到集成: " + coordinate,
                "请安装 " + coordinate + " 集成"
            ));
            processing.remove(coordinate);
            return;
        }

        // 筛选满足约束的版本
        List<IntegrationInfo> validVersions = new ArrayList<>();
        for (IntegrationInfo info : availableVersions) {
            Version v = info.getVersionObject();
            if (v != null && (constraint == null || constraint.satisfies(v))) {
                validVersions.add(info);
            }
        }

        if (validVersions.isEmpty()) {
            conflicts.add(new ConflictException(
                ConflictException.ConflictType.UNSATISFIED_CONSTRAINT,
                coordinate,
                "没有满足约束 " + constraint + " 的版本",
                "放宽版本约束或安装新版本"
            ));
            processing.remove(coordinate);
            return;
        }

        // 选择最大版本
        validVersions.sort((i1, i2) -> {
            Version v1 = i1.getVersionObject();
            Version v2 = i2.getVersionObject();
            if (v1 == null) return 1;
            if (v2 == null) return -1;
            return v2.compareTo(v1); // 降序
        });
        IntegrationInfo selectedInfo = validVersions.get(0);

        selectedIntegrations.put(coordinate, new SelectedIntegration(
            selectedInfo,
            constraint != null ? "满足约束 " + constraint : "直接指定"
        ));

        // 递归处理依赖（从 dependencyInfoList 获取 coordinate 列表）
        List<com.ecat.core.Integration.DependencyInfo> dependencyInfoList = selectedInfo.getDependencyInfoList();
        if (dependencyInfoList != null && !dependencyInfoList.isEmpty()) {
            for (com.ecat.core.Integration.DependencyInfo dep : dependencyInfoList) {
                String depCoordinate = dep.getCoordinate();
                // 从 DependencyInfo 获取版本约束
                VersionRange range = null;
                if (dep.hasVersionConstraint()) {
                    try {
                        range = VersionRange.parse(dep.getVersion());
                    } catch (Exception e) {
                        // 忽略无效的版本范围
                    }
                }
                selectIntegration(depCoordinate, range, selectedInfo,
                    coordinateToVersions, selectedIntegrations,
                    processing, processed, conflicts);
            }
        }

        processing.remove(coordinate);
        processed.add(coordinate);
    }

    /**
     * 构建依赖图
     */
    private DependencyGraph buildDependencyGraph(
            Map<String, SelectedIntegration> selectedIntegrations) {

        DependencyGraph graph = new DependencyGraph();

        // 添加节点
        for (String coordinate : selectedIntegrations.keySet()) {
            graph.addNode(coordinate);
        }

        // 添加边（依赖关系）
        for (SelectedIntegration selected : selectedIntegrations.values()) {
            String from = selected.getCoordinate();
            List<com.ecat.core.Integration.DependencyInfo> dependencyInfoList = selected.integrationInfo.getDependencyInfoList();

            if (dependencyInfoList != null) {
                for (com.ecat.core.Integration.DependencyInfo depInfo : dependencyInfoList) {
                    String depCoordinate = depInfo.getCoordinate();
                    if (selectedIntegrations.containsKey(depCoordinate)) {
                        graph.addDependency(from, depCoordinate);
                    }
                }
            }
        }

        return graph;
    }

    /**
     * 拓扑排序
     */
    private List<IntegrationInfo> topologicalSort(
            DependencyGraph graph,
            Map<String, SelectedIntegration> selectedIntegrations,
            List<ConflictException> conflicts) {

        // Kahn算法
        Map<String, Integer> inDegree = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        // 初始化入度
        for (String node : graph.getNodes()) {
            inDegree.put(node, 0);
        }
        for (String node : graph.getNodes()) {
            for (String neighbor : graph.getDependencies(node)) {
                inDegree.put(neighbor, inDegree.getOrDefault(neighbor, 0) + 1);
            }
        }

        // 入度为0的节点入队
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        // 拓扑排序
        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(current);

            for (String neighbor : graph.getDependencies(current)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        // 检测循环
        if (sorted.size() != graph.getNodes().size()) {
            for (String node : graph.getNodes()) {
                if (!sorted.contains(node)) {
                    conflicts.add(new ConflictException(
                        ConflictException.ConflictType.CIRCULAR_DEPENDENCY,
                        node,
                        "检测到循环依赖，无法确定加载顺序",
                        "请检查依赖关系"
                    ));
                }
            }
            return Collections.emptyList();
        }

        // 反转顺序（依赖在前）
        Collections.reverse(sorted);

        // 构建加载顺序
        List<IntegrationInfo> loadOrder = new ArrayList<>();
        for (String artifactId : sorted) {
            SelectedIntegration selected = selectedIntegrations.get(artifactId);
            if (selected != null) {
                loadOrder.add(selected.integrationInfo);
            }
        }

        return loadOrder;
    }

    /**
     * 构建SelectedVersion映射
     */
    private Map<String, SelectedVersion> buildSelectedVersions(
            Map<String, SelectedIntegration> selectedIntegrations) {

        Map<String, SelectedVersion> result = new HashMap<>();
        for (SelectedIntegration selected : selectedIntegrations.values()) {
            Version version = selected.getVersion();
            if (version != null) {
                result.put(selected.getCoordinate(),
                    new SelectedVersion(selected.getCoordinate(), version, selected.reason));
            }
        }
        return result;
    }
}
