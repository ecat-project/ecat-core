package com.ecat.core.Dependency;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * DependencyGraph单元测试
 */
public class DependencyGraphTest {

    // ========== 基本图操作测试 ==========

    @Test
    public void testAddNode() {
        DependencyGraph graph = new DependencyGraph();
        graph.addNode("module-a");

        assertEquals("应该有1个节点", 1, graph.size());
        assertTrue("应该包含module-a", graph.getNodes().contains("module-a"));
    }

    @Test
    public void testAddDependency() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency("module-a", "module-b");

        assertEquals("应该有2个节点", 2, graph.size());
        assertEquals("应该有1条边", 1, graph.edgeCount());

        Set<String> depsOfA = graph.getDependencies("module-a");
        assertEquals("module-a应该有1个依赖", 1, depsOfA.size());
        assertTrue("module-a应该依赖module-b", depsOfA.contains("module-b"));

        Set<String> depsOfB = graph.getDependencies("module-b");
        assertEquals("module-b应该没有依赖", 0, depsOfB.size());
    }

    @Test
    public void testAddDependencies_Batch() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependencies("module-a", Arrays.asList("module-b", "module-c", "module-d"));

        assertEquals("应该有4个节点", 4, graph.size());
        assertEquals("应该有3条边", 3, graph.edgeCount());

        Set<String> depsOfA = graph.getDependencies("module-a");
        assertEquals("module-a应该有3个依赖", 3, depsOfA.size());
    }

    @Test
    public void testGetEdges() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency("module-a", "module-b");
        graph.addDependency("module-b", "module-c");

        List<DependencyGraph.Edge> edges = graph.getEdges();
        assertEquals("应该有2条边", 2, edges.size());

        // 验证边的顺序可能不同，检查内容
        boolean hasAB = edges.stream()
            .anyMatch(e -> e.getFrom().equals("module-a") && e.getTo().equals("module-b"));
        boolean hasBC = edges.stream()
            .anyMatch(e -> e.getFrom().equals("module-b") && e.getTo().equals("module-c"));

        assertTrue("应该有A->B边", hasAB);
        assertTrue("应该有B->C边", hasBC);
    }

    // ========== 度数测试 ==========

    @Test
    public void testInDegree() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency("module-a", "module-c");
        graph.addDependency("module-b", "module-c");

        assertEquals("module-c的入度应该是2", 2, graph.getInDegree("module-c"));
        assertEquals("module-a的入度应该是0", 0, graph.getInDegree("module-a"));
    }

    @Test
    public void testOutDegree() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency("module-a", "module-b");
        graph.addDependency("module-a", "module-c");

        assertEquals("module-a的出度应该是2", 2, graph.getOutDegree("module-a"));
        assertEquals("module-b的出度应该是0", 0, graph.getOutDegree("module-b"));
    }

    // ========== 反向依赖测试 ==========

    @Test
    public void testGetDependents() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency("module-a", "module-c");
        graph.addDependency("module-b", "module-c");

        Set<String> dependentsOfC = graph.getDependents("module-c");
        assertEquals("module-c应该有2个反向依赖", 2, dependentsOfC.size());
        assertTrue("module-a应该依赖module-c", dependentsOfC.contains("module-a"));
        assertTrue("module-b应该依赖module-c", dependentsOfC.contains("module-b"));
    }

    // ========== 路径检测测试 ==========

    @Test
    public void testHasPath_Direct() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency("module-a", "module-b");

        assertTrue("应该存在A到B的路径", graph.hasPath("module-a", "module-b"));
        assertFalse("不应该存在B到A的路径", graph.hasPath("module-b", "module-a"));
    }

    @Test
    public void testHasPath_Indirect() {
        DependencyGraph graph = new DependencyGraph();
        // A -> B -> C
        graph.addDependency("module-a", "module-b");
        graph.addDependency("module-b", "module-c");

        assertTrue("应该存在A到C的路径（通过B）", graph.hasPath("module-a", "module-c"));
        assertFalse("不应该存在C到A的路径", graph.hasPath("module-c", "module-a"));
    }

    @Test
    public void testHasPath_SameNode() {
        DependencyGraph graph = new DependencyGraph();
        graph.addNode("module-a");

        assertTrue("节点到自身应该存在路径", graph.hasPath("module-a", "module-a"));
    }

    @Test
    public void testHasPath_NonExistentNode() {
        DependencyGraph graph = new DependencyGraph();
        graph.addNode("module-a");

        assertFalse("不存在的节点应该返回false", graph.hasPath("module-a", "module-x"));
        assertFalse("不存在的节点应该返回false", graph.hasPath("module-x", "module-a"));
    }

    // ========== 循环检测测试 ==========

    @Test
    public void testHasCycle_SimpleCycle() {
        DependencyGraph graph = new DependencyGraph();
        // A -> B -> A
        graph.addDependency("module-a", "module-b");
        graph.addDependency("module-b", "module-a");

        assertTrue("应该检测到循环", graph.hasCycle());
    }

    @Test
    public void testHasCycle_ComplexCycle() {
        DependencyGraph graph = new DependencyGraph();
        // A -> B -> C -> A
        graph.addDependency("module-a", "module-b");
        graph.addDependency("module-b", "module-c");
        graph.addDependency("module-c", "module-a");

        assertTrue("应该检测到循环", graph.hasCycle());
    }

    @Test
    public void testHasCycle_SelfLoop() {
        DependencyGraph graph = new DependencyGraph();
        // A -> A
        graph.addDependency("module-a", "module-a");

        assertTrue("应该检测到自循环", graph.hasCycle());
    }

    @Test
    public void testHasCycle_NoCycle() {
        DependencyGraph graph = new DependencyGraph();
        // A -> B -> C（无循环）
        graph.addDependency("module-a", "module-b");
        graph.addDependency("module-b", "module-c");

        assertFalse("不应该检测到循环", graph.hasCycle());
    }

    @Test
    public void testHasCycle_Diamond() {
        DependencyGraph graph = new DependencyGraph();
        // 菱形依赖
        //     A
        //    / \
        //   B   C
        //    \ /
        //     D
        graph.addDependency("module-a", "module-b");
        graph.addDependency("module-a", "module-c");
        graph.addDependency("module-b", "module-d");
        graph.addDependency("module-c", "module-d");

        assertFalse("菱形依赖不是循环", graph.hasCycle());
    }

    // ========== Edge类测试 ==========

    @Test
    public void testEdgeEquals() {
        DependencyGraph.Edge edge1 = new DependencyGraph.Edge("a", "b");
        DependencyGraph.Edge edge2 = new DependencyGraph.Edge("a", "b");
        DependencyGraph.Edge edge3 = new DependencyGraph.Edge("b", "a");

        assertEquals("相同的边应该相等", edge1, edge2);
        assertNotEquals("相反的边不应该相等", edge1, edge3);
    }

    @Test
    public void testEdgeHashCode() {
        DependencyGraph.Edge edge1 = new DependencyGraph.Edge("a", "b");
        DependencyGraph.Edge edge2 = new DependencyGraph.Edge("a", "b");

        assertEquals("相等的边应该有相同的hashCode", edge1.hashCode(), edge2.hashCode());
    }

    @Test
    public void testEdgeToString() {
        DependencyGraph.Edge edge = new DependencyGraph.Edge("a", "b");
        assertEquals("边的toString格式", "a -> b", edge.toString());
    }

    // ========== 清空和大小测试 ==========

    @Test
    public void testClear() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency("module-a", "module-b");
        graph.addDependency("module-b", "module-c");

        graph.clear();

        assertEquals("清空后应该有0个节点", 0, graph.size());
        assertEquals("清空后应该有0条边", 0, graph.edgeCount());
    }

    @Test
    public void testSize() {
        DependencyGraph graph = new DependencyGraph();
        graph.addNode("a");
        graph.addNode("b");
        graph.addNode("c");

        assertEquals("应该有3个节点", 3, graph.size());
    }

    @Test
    public void testEdgeCount() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency("a", "b");
        graph.addDependency("a", "c");
        graph.addDependency("b", "d");

        assertEquals("应该有3条边", 3, graph.edgeCount());
    }

    // ========== toString测试 ==========

    @Test
    public void testToString() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency("a", "b");

        String str = graph.toString();
        assertTrue("toString应该包含节点数", str.contains("nodes="));
        assertTrue("toString应该包含边数", str.contains("edges="));
        assertTrue("toString应该包含边信息", str.contains("a -> b"));
    }
}
