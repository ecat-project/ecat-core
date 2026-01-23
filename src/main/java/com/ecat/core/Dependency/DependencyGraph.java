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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 依赖图，用于表示集成模块之间的依赖关系
 *
 * 使用邻接表表示有向图：
 * - 节点：集成模块的artifactId
 * - 边：依赖关系（A -> B 表示 A 依赖于 B）
 *
 * @author coffee
 */
public class DependencyGraph {

    /**
     * 邻接表：key -> 依赖列表
     * 例如：{"integration-tcp": ["integration-ecat-common"]}
     * 表示 integration-tcp 依赖于 integration-ecat-common
     */
    private final Map<String, Set<String>> adjacencyList;

    /**
     * 所有节点集合
     */
    private final Set<String> nodes;

    public DependencyGraph() {
        this.adjacencyList = new HashMap<>();
        this.nodes = new HashSet<>();
    }

    /**
     * 添加节点
     *
     * @param node 节点标识（artifactId）
     */
    public void addNode(String node) {
        nodes.add(node);
        adjacencyList.putIfAbsent(node, new HashSet<>());
    }

    /**
     * 添加依赖关系（边）
     *
     * @param from 依赖方
     * @param to 被依赖方
     */
    public void addDependency(String from, String to) {
        nodes.add(from);
        nodes.add(to);
        adjacencyList.computeIfAbsent(from, k -> new HashSet<>()).add(to);
    }

    /**
     * 批量添加依赖关系
     *
     * @param from 依赖方
     * @param dependencies 被依赖方列表
     */
    public void addDependencies(String from, List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }
        for (String dep : dependencies) {
            addDependency(from, dep);
        }
    }

    /**
     * 获取节点的所有依赖
     *
     * @param node 节点标识
     * @return 依赖列表（不可修改）
     */
    public Set<String> getDependencies(String node) {
        Set<String> deps = adjacencyList.get(node);
        return deps != null ? Collections.unmodifiableSet(deps) : Collections.emptySet();
    }

    /**
     * 获取依赖某个节点的所有节点（反向依赖）
     *
     * @param node 节点标识
     * @return 反向依赖列表
     */
    public Set<String> getDependents(String node) {
        Set<String> dependents = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : adjacencyList.entrySet()) {
            if (entry.getValue().contains(node)) {
                dependents.add(entry.getKey());
            }
        }
        return dependents;
    }

    /**
     * 获取所有节点
     *
     * @return 节点集合（不可修改）
     */
    public Set<String> getNodes() {
        return Collections.unmodifiableSet(nodes);
    }

    /**
     * 获取所有边
     *
     * @return 边列表（from, to）对
     */
    public List<Edge> getEdges() {
        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : adjacencyList.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                edges.add(new Edge(from, to));
            }
        }
        return edges;
    }

    /**
     * 检测是否存在循环依赖
     *
     * @return 如果存在循环返回true
     */
    public boolean hasCycle() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : nodes) {
            if (hasCycleDFS(node, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 深度优先搜索检测循环
     */
    private boolean hasCycleDFS(String node, Set<String> visited,
            Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true; // 找到环
        }
        if (visited.contains(node)) {
            return false; // 已处理过
        }

        visited.add(node);
        recursionStack.add(node);

        Set<String> dependencies = adjacencyList.get(node);
        if (dependencies != null) {
            for (String dep : dependencies) {
                if (hasCycleDFS(dep, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(node);
        return false;
    }

    /**
     * 获取节点的入度（被多少个节点依赖）
     *
     * @param node 节点标识
     * @return 入度值
     */
    public int getInDegree(String node) {
        int degree = 0;
        for (Set<String> deps : adjacencyList.values()) {
            if (deps.contains(node)) {
                degree++;
            }
        }
        return degree;
    }

    /**
     * 获取节点的出度（依赖了多少个节点）
     *
     * @param node 节点标识
     * @return 出度值
     */
    public int getOutDegree(String node) {
        Set<String> deps = adjacencyList.get(node);
        return deps != null ? deps.size() : 0;
    }

    /**
     * 检查两个节点之间是否存在路径
     *
     * @param from 起点
     * @param to 终点
     * @return 如果存在路径返回true
     */
    public boolean hasPath(String from, String to) {
        if (!nodes.contains(from) || !nodes.contains(to)) {
            return false;
        }
        if (from.equals(to)) {
            return true;
        }

        Set<String> visited = new HashSet<>();
        return hasPathDFS(from, to, visited);
    }

    private boolean hasPathDFS(String current, String target, Set<String> visited) {
        if (current.equals(target)) {
            return true;
        }

        visited.add(current);
        Set<String> deps = adjacencyList.get(current);
        if (deps != null) {
            for (String dep : deps) {
                if (!visited.contains(dep) && hasPathDFS(dep, target, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 清空图
     */
    public void clear() {
        adjacencyList.clear();
        nodes.clear();
    }

    /**
     * 获取节点数量
     */
    public int size() {
        return nodes.size();
    }

    /**
     * 获取边数量
     */
    public int edgeCount() {
        int count = 0;
        for (Set<String> deps : adjacencyList.values()) {
            count += deps.size();
        }
        return count;
    }

    /**
     * 边表示类
     */
    public static class Edge {
        private final String from;
        private final String to;

        public Edge(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        @Override
        public String toString() {
            return from + " -> " + to;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Edge edge = (Edge) obj;
            return from.equals(edge.from) && to.equals(edge.to);
        }

        @Override
        public int hashCode() {
            return from.hashCode() * 31 + to.hashCode();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DependencyGraph{nodes=").append(nodes.size())
          .append(", edges=").append(edgeCount()).append("}\n");

        List<Edge> edges = getEdges();
        for (Edge edge : edges) {
            sb.append("  ").append(edge).append("\n");
        }

        return sb.toString();
    }
}
