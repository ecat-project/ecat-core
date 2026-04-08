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

package com.ecat.core.LogicDevice;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * LogicDeviceRegistry DAG 校验单元测试
 *
 * <p>测试逻辑设备注册表的 DAG（有向无环图）校验功能：
 * <ul>
 *   <li>无环依赖链：A -> B -> C 不会触发异常</li>
 *   <li>自环检测：A -> A 应被检测为环路</li>
 *   <li>三角环检测：A -> B -> C -> A 应被检测为环路</li>
 *   <li>validateDAG() 在有环路时抛出 IllegalStateException</li>
 *   <li>validateDAG() 在无环路时正常返回</li>
 * </ul>
 *
 * @author coffee
 */
public class LogicDeviceRegistryDAGTest {

    private LogicDeviceRegistry registry;

    @Before
    public void setUp() {
        registry = new LogicDeviceRegistry();
    }

    // ========== 测试用例 ==========

    /**
     * 测试：无环依赖链 A -> B -> C，hasCycle() 应返回 false
     */
    @Test
    public void testNoCycle() {
        // 构建依赖链：A -> B -> C
        registry.addDependencyEdge("A:attr1", "B:attr1");
        registry.addDependencyEdge("B:attr1", "C:attr1");

        assertFalse("A->B->C 链不应有环路", registry.hasCycle());
    }

    /**
     * 测试：自环 A -> A，hasCycle() 应返回 true
     */
    @Test
    public void testDirectCycle() {
        // 自环：A 依赖自身
        registry.addDependencyEdge("A:attr1", "A:attr1");

        assertTrue("自环 A->A 应检测到环路", registry.hasCycle());
    }

    /**
     * 测试：三角环 A -> B -> C -> A，hasCycle() 应返回 true
     */
    @Test
    public void testTriangleCycle() {
        // 构建三角环：A -> B -> C -> A
        registry.addDependencyEdge("A:attr1", "B:attr1");
        registry.addDependencyEdge("B:attr1", "C:attr1");
        registry.addDependencyEdge("C:attr1", "A:attr1");

        assertTrue("三角环 A->B->C->A 应检测到环路", registry.hasCycle());
    }

    /**
     * 测试：validateDAG() 在有环路时抛出 IllegalStateException
     */
    @Test(expected = IllegalStateException.class)
    public void testValidateDAGThrowsOnCycle() {
        // 构建环路：X -> Y -> X
        registry.addDependencyEdge("X:attr1", "Y:attr1");
        registry.addDependencyEdge("Y:attr1", "X:attr1");

        // validateDAG() 应抛出 IllegalStateException
        registry.validateDAG();
    }

    /**
     * 测试：validateDAG() 在无环路时正常返回（不抛异常）
     */
    @Test
    public void testValidateDAGNoThrowWhenNoCycle() {
        // 构建无环依赖图：D -> E -> F, D -> G
        registry.addDependencyEdge("D:attr1", "E:attr1");
        registry.addDependencyEdge("E:attr1", "F:attr1");
        registry.addDependencyEdge("D:attr1", "G:attr1");

        // 不应抛出异常
        registry.validateDAG();
    }

    /**
     * 测试：空的依赖图不应触发环路检测
     */
    @Test
    public void testEmptyGraphNoCycle() {
        assertFalse("空图不应有环路", registry.hasCycle());
    }

    /**
     * 测试：单节点无边不应触发环路检测
     */
    @Test
    public void testSingleNodeNoCycle() {
        registry.addDependencyEdge("A:attr1", "B:attr1");

        assertFalse("单边 A->B 不应有环路", registry.hasCycle());
    }

    /**
     * 测试：复杂无环图（菱形依赖）
     *   A -> B
     *   A -> C
     *   B -> D
     *   C -> D
     */
    @Test
    public void testDiamondNoCycle() {
        registry.addDependencyEdge("A:attr1", "B:attr1");
        registry.addDependencyEdge("A:attr1", "C:attr1");
        registry.addDependencyEdge("B:attr1", "D:attr1");
        registry.addDependencyEdge("C:attr1", "D:attr1");

        assertFalse("菱形依赖不应有环路", registry.hasCycle());
    }
}
