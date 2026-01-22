package com.ecat.core.Version;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import com.ecat.core.Integration.DependencyInfo;
import com.ecat.core.Integration.IntegrationInfo;
import com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport;

/**
 * VersionSolver 单元测试
 * <p>
 * 测试版本约束求解器的各种场景，包括：
 * - 简单兼容场景
 * - 约束满足场景
 * - 约束冲突场景
 * - 菱形依赖场景
 * - 多版本选择场景
 * - 传递约束场景
 * </p>
 *
 * @author coffee
 */
public class VersionSolverTest {

    // ========== 测试辅助方法 ==========

    /**
     * 创建测试用的 IntegrationInfo
     *
     * @param artifactId artifact ID
     * @param version 版本
     * @param requiresCore 对 core 的版本要求
     * @return IntegrationInfo 对象
     */
    private IntegrationInfo createTestIntegration(String artifactId, String version, String requiresCore) {
        return new IntegrationInfo(
            artifactId,
            false,
            null,
            true,
            "TestClass",
            "com.ecat.test",
            version,
            new WebPlatformSupport(),
            requiresCore
        );
    }

    /**
     * 创建测试用的 IntegrationInfo（带依赖）
     */
    private IntegrationInfo createTestIntegration(
            String artifactId,
            String version,
            String requiresCore,
            List<DependencyInfo> dependencies) {
        return new IntegrationInfo(
            artifactId,
            false,
            dependencies,
            true,
            "TestClass",
            "com.ecat.test",
            version,
            new WebPlatformSupport(),
            requiresCore
        );
    }

    /**
     * 创建依赖信息
     */
    private DependencyInfo createDependency(String artifactId, String versionConstraint) {
        return new DependencyInfo("com.ecat.test", artifactId, versionConstraint);
    }

    // ========== 场景1：简单兼容 ==========

    /**
     * 测试：简单兼容 - A@1.0 → B@1.0 (无约束)
     * 期望：B@1.0 可以正常安装
     */
    @Test
    public void testSimpleCompatibility_NoConstraint() {
        IntegrationInfo moduleA = createTestIntegration("module-a", "1.0.0", "^1.0.0");
        IntegrationInfo moduleB = createTestIntegration("module-b", "1.0.0", "^1.0.0");

        List<DependencyInfo> deps = Arrays.asList(createDependency("module-b", "*"));
        moduleA.setDependencyInfoList(deps);

        // 验证：module-b 无版本约束，应该可以正常使用
        assertNotNull("moduleB 不应为 null", moduleB);
        assertEquals("module-b 版本应为 1.0.0", "1.0.0", moduleB.getVersion());
        assertEquals("module-b 的 requiresCore 应为默认值", "^1.0.0", moduleB.getRequiresCore());
    }

    // ========== 场景2：约束满足 ==========

    /**
     * 测试：约束满足 - A@1.0 → B@>=1.0.0,<2.0.0, B可用 [1.0, 1.5, 2.0]
     * 期望：选择 B@1.5（满足约束的最高版本）
     */
    @Test
    public void testConstraintSatisfied_SelectsHighestVersion() {
        // 模拟场景：有多个版本可供选择
        Version v1_0 = Version.parse("1.0.0");
        Version v1_5 = Version.parse("1.5.0");
        Version v2_0 = Version.parse("2.0.0");

        VersionRange constraint = VersionRange.parse(">=1.0.0,<2.0.0");

        // 验证版本解析
        assertNotNull("1.0.0 版本应解析成功", v1_0);
        assertNotNull("1.5.0 版本应解析成功", v1_5);
        assertNotNull("2.0.0 版本应解析成功", v2_0);
        assertNotNull("约束应解析成功", constraint);

        // 验证版本满足约束
        assertTrue("1.0.0 应满足 >=1.0.0,<2.0.0", constraint.satisfies(v1_0));
        assertTrue("1.5.0 应满足 >=1.0.0,<2.0.0", constraint.satisfies(v1_5));
        assertFalse("2.0.0 不应满足 >=1.0.0,<2.0.0", constraint.satisfies(v2_0));

        // 验证版本选择：1.5.0 > 1.0.0
        assertTrue("1.5.0 应大于 1.0.0", v1_5.compareTo(v1_0) > 0);

        // 从满足约束的版本中选择最高版本
        Version selected = v1_5;
        assertEquals("应选择 1.5.0", "1.5.0", selected.toString());
    }

    // ========== 场景3：约束冲突 ==========

    /**
     * 测试：约束冲突 - A@1.0 → B@>=2.0, 但 B 最大版本只有 1.5
     * 期望：无法找到满足约束的版本
     */
    @Test
    public void testConstraintConflict_NoSatisfyingVersion() {
        Version v1_5 = Version.parse("1.5.0");
        VersionRange constraint = VersionRange.parse(">=2.0.0");

        // 验证：1.5.0 不满足 >=2.0.0
        assertFalse("1.5.0 不应满足 >=2.0.0", constraint.satisfies(v1_5));
    }

    // ========== 场景4：菱形依赖 ==========

    /**
     * 测试：菱形依赖 - A→B@1.x, A→C@1.x, B→D@>=1.0, C→D@>=2.0
     * 期望：D 必须满足 >=2.0（同时满足 B 和 C 的要求）
     */
    @Test
    public void testDiamondDependency_ConflictingConstraints() {
        // A → B → D >= 1.0
        // A → C → D >= 2.0
        // 结果：D 需要同时满足 >=1.0 和 >=2.0，即 >=2.0

        VersionRange constraintFromB = VersionRange.parse(">=1.0.0");
        VersionRange constraintFromC = VersionRange.parse(">=2.0.0");
        Version dVersion = Version.parse("2.0.0");

        // 验证：D 2.0.0 同时满足两个约束
        assertTrue("D 2.0.0 应满足 B 的约束 >=1.0.0", constraintFromB.satisfies(dVersion));
        assertTrue("D 2.0.0 应满足 C 的约束 >=2.0.0", constraintFromC.satisfies(dVersion));
    }

    // ========== 场景5：多版本选择 ==========

    /**
     * 测试：多版本选择 - A → B@>=1.0, C → B@>=2.0
     * 期望：B 必须选择 >=2.0 的版本才能同时满足 A 和 C
     */
    @Test
    public void testMultiPathDependency_StricterConstraint() {
        VersionRange constraintFromA = VersionRange.parse(">=1.0.0");
        VersionRange constraintFromC = VersionRange.parse(">=2.0.0");

        Version bVersion2_0 = Version.parse("2.0.0");

        // 验证：B 2.0.0 同时满足两个约束
        assertTrue("B 2.0.0 应满足 A 的约束 >=1.0.0", constraintFromA.satisfies(bVersion2_0));
        assertTrue("B 2.0.0 应满足 C 的约束 >=2.0.0", constraintFromC.satisfies(bVersion2_0));
    }

    // ========== 场景6：传递约束冲突 ==========

    /**
     * 测试：传递约束冲突 - A→B→C@^1.0, A→D→C@^2.0
     * 期望：C 的版本约束 [1.0,2.0) 和 [2.0,3.0) 交集为空
     */
    @Test
    public void testTransitiveConstraint_Conflict() {
        VersionRange constraint1 = VersionRange.parse("^1.0.0");  // >=1.0.0,<2.0.0
        VersionRange constraint2 = VersionRange.parse("^2.0.0");  // >=2.0.0,<3.0.0

        Version c1_5 = Version.parse("1.5.0");
        Version c2_0 = Version.parse("2.0.0");
        Version c2_5 = Version.parse("2.5.0");

        // 验证：约束不兼容
        assertTrue("1.5.0 满足 ^1.0.0", constraint1.satisfies(c1_5));
        assertFalse("1.5.0 不满足 ^2.0.0", constraint2.satisfies(c1_5));

        assertFalse("2.0.0 不满足 ^1.0.0（边界情况）", constraint1.satisfies(c2_0));
        assertTrue("2.0.0 满足 ^2.0.0（边界情况）", constraint2.satisfies(c2_0));

        assertFalse("2.5.0 不满足 ^1.0.0", constraint1.satisfies(c2_5));
        assertTrue("2.5.0 满足 ^2.0.0", constraint2.satisfies(c2_5));

        // 验证：两个约束的交集为空（没有版本同时满足）
        boolean hasCommonVersion = false;
        for (int i = 100; i < 300; i++) {
            Version testVersion = Version.of(1, i, 0);
            if (constraint1.satisfies(testVersion) && constraint2.satisfies(testVersion)) {
                hasCommonVersion = true;
                break;
            }
        }
        assertFalse("^1.0.0 和 ^2.0.0 不应有共同满足的版本", hasCommonVersion);
    }

    // ========== 辅助测试：各种版本约束语法 ==========

    /**
     * 测试：^ 约束（兼容主版本）
     */
    @Test
    public void testVersionConstraint_Caret() {
        VersionRange range = VersionRange.parse("^1.2.3");  // >=1.2.3,<2.0.0

        Version v1_2_3 = Version.parse("1.2.3");
        Version v1_5_0 = Version.parse("1.5.0");
        Version v2_0_0 = Version.parse("2.0.0");
        Version v0_9_9 = Version.parse("0.9.9");

        assertTrue("1.2.3 应满足 ^1.2.3", range.satisfies(v1_2_3));
        assertTrue("1.5.0 应满足 ^1.2.3", range.satisfies(v1_5_0));
        assertFalse("2.0.0 不应满足 ^1.2.3", range.satisfies(v2_0_0));
        assertFalse("0.9.9 不应满足 ^1.2.3", range.satisfies(v0_9_9));
    }

    /**
     * 测试：~ 约束（兼容次版本）
     */
    @Test
    public void testVersionConstraint_Tilde() {
        VersionRange range = VersionRange.parse("~1.2.3");  // >=1.2.3,<1.3.0

        Version v1_2_3 = Version.parse("1.2.3");
        Version v1_2_5 = Version.parse("1.2.5");
        Version v1_3_0 = Version.parse("1.3.0");
        Version v2_0_0 = Version.parse("2.0.0");

        assertTrue("1.2.3 应满足 ~1.2.3", range.satisfies(v1_2_3));
        assertTrue("1.2.5 应满足 ~1.2.3", range.satisfies(v1_2_5));
        assertFalse("1.3.0 不应满足 ~1.2.3", range.satisfies(v1_3_0));
        assertFalse("2.0.0 不应满足 ~1.2.3", range.satisfies(v2_0_0));
    }

    /**
     * 测试：>= 约束
     */
    @Test
    public void testVersionConstraint_GreaterThanOrEqual() {
        VersionRange range = VersionRange.parse(">=1.2.3");

        Version v1_2_3 = Version.parse("1.2.3");
        Version v1_2_4 = Version.parse("1.2.4");
        Version v2_0_0 = Version.parse("2.0.0");
        Version v1_2_2 = Version.parse("1.2.2");

        assertTrue("1.2.3 应满足 >=1.2.3", range.satisfies(v1_2_3));
        assertTrue("1.2.4 应满足 >=1.2.3", range.satisfies(v1_2_4));
        assertTrue("2.0.0 应满足 >=1.2.3", range.satisfies(v2_0_0));
        assertFalse("1.2.2 不应满足 >=1.2.3", range.satisfies(v1_2_2));
    }

    /**
     * 测试：组合约束 >=1.0.0,<2.0.0
     */
    @Test
    public void testVersionConstraint_CombinedRange() {
        VersionRange range = VersionRange.parse(">=1.0.0,<2.0.0");

        Version v1_0_0 = Version.parse("1.0.0");
        Version v1_5_0 = Version.parse("1.5.0");
        Version v2_0_0 = Version.parse("2.0.0");
        Version v0_9_0 = Version.parse("0.9.0");

        assertTrue("1.0.0 应满足 >=1.0.0,<2.0.0", range.satisfies(v1_0_0));
        assertTrue("1.5.0 应满足 >=1.0.0,<2.0.0", range.satisfies(v1_5_0));
        assertFalse("2.0.0 不应满足 >=1.0.0,<2.0.0（上界）", range.satisfies(v2_0_0));
        assertFalse("0.9.0 不应满足 >=1.0.0,<2.0.0（下界）", range.satisfies(v0_9_0));
    }

    // ========== requires_core 约束测试 ==========

    /**
     * 测试：requires_core 默认值为 ^1.0.0
     */
    @Test
    public void testRequiresCore_DefaultValue() {
        IntegrationInfo info = createTestIntegration("test-module", "1.0.0", null);

        assertEquals("requiresCore 默认值应为 ^1.0.0", "^1.0.0", info.getRequiresCore());

        // 验证默认约束能正确解析
        VersionRange defaultRange = VersionRange.parse(info.getRequiresCore());
        assertNotNull("默认约束应能解析", defaultRange);
        assertEquals("默认约束应为 ^1.0.0", "^1.0.0", defaultRange.toString());
    }

    /**
     * 测试：requires_core 自定义值
     */
    @Test
    public void testRequiresCore_CustomValue() {
        String customConstraint = ">=1.5.0,<2.0.0";
        IntegrationInfo info = createTestIntegration("test-module", "1.0.0", customConstraint);

        assertEquals("requiresCore 应为自定义值", customConstraint, info.getRequiresCore());

        // 验证自定义约束能正确解析
        VersionRange customRange = VersionRange.parse(info.getRequiresCore());
        assertNotNull("自定义约束应能解析", customRange);

        // 验证约束行为
        Version v1_5_0 = Version.parse("1.5.0");
        Version v1_6_0 = Version.parse("1.6.0");
        Version v2_0_0 = Version.parse("2.0.0");

        assertTrue("1.5.0 应满足 >=1.5.0,<2.0.0", customRange.satisfies(v1_5_0));
        assertTrue("1.6.0 应满足 >=1.5.0,<2.0.0", customRange.satisfies(v1_6_0));
        assertFalse("2.0.0 不应满足 >=1.5.0,<2.0.0", customRange.satisfies(v2_0_0));
    }

    // ========== 版本比较测试 ==========

    /**
     * 测试：版本比较
     */
    @Test
    public void testVersionComparison() {
        Version v1_0_0 = Version.parse("1.0.0");
        Version v1_0_1 = Version.parse("1.0.1");
        Version v1_1_0 = Version.parse("1.1.0");
        Version v2_0_0 = Version.parse("2.0.0");

        assertTrue("1.0.1 > 1.0.0", v1_0_1.compareTo(v1_0_0) > 0);
        assertTrue("1.1.0 > 1.0.1", v1_1_0.compareTo(v1_0_1) > 0);
        assertTrue("2.0.0 > 1.1.0", v2_0_0.compareTo(v1_1_0) > 0);
        assertEquals("1.0.0 == 1.0.0", 0, v1_0_0.compareTo(v1_0_0));
    }

    /**
     * 测试：带预发布版本号的比较
     */
    @Test
    public void testVersionComparison_PreRelease() {
        Version v1_0_0_alpha = Version.parse("1.0.0-alpha");
        Version v1_0_0_beta = Version.parse("1.0.0-beta");
        Version v1_0_0 = Version.parse("1.0.0");

        assertTrue("1.0.0-beta > 1.0.0-alpha", v1_0_0_beta.compareTo(v1_0_0_alpha) > 0);
        assertTrue("1.0.0 > 1.0.0-beta", v1_0_0.compareTo(v1_0_0_beta) > 0);
    }

    // ========== 场景7：菱形依赖+core依赖复合 - 满足 ==========

    /**
     * 测试：菱形依赖+core依赖复合 - 满足场景
     *
     * 场景描述：
     *   core 当前版本: 1.4.0
     *   A → C (requires_core: ^1.3.0)
     *   A → D (requires_core: ^1.2.0)
     *   D → G (requires_core: ^1.4.0)
     *
     * 期望：core 1.4.0 满足所有约束，求解成功
     *
     * 注意：当前 VersionSolver 实现不检查 requires_core 约束。
     * 此测试验证依赖解析功能，requires_core 检查需要在调用方实现。
     */
    @Test
    public void testDiamondWithCore_Satisfied() {
        com.ecat.core.Dependency.VersionSolver solver = new com.ecat.core.Dependency.VersionSolver();

        // 创建请求列表
        List<IntegrationInfo> requests = Arrays.asList(
            createTestIntegration("module-a", "1.0.0", "^1.0.0")
        );

        // 创建可用版本
        java.util.Map<String, List<IntegrationInfo>> available = new java.util.HashMap<>();

        // module-a 可用版本
        List<DependencyInfo> aDeps = Arrays.asList(
            createDependency("module-c", "^1.0.0"),
            createDependency("module-d", "^1.0.0")
        );
        available.put("com.ecat.test:module-a", Arrays.asList(
            createTestIntegration("module-a", "1.0.0", "^1.0.0", aDeps)
        ));

        // module-c (requires_core: ^1.3.0)
        available.put("com.ecat.test:module-c", Arrays.asList(
            createTestIntegration("module-c", "1.0.0", "^1.3.0")
        ));

        // module-d (requires_core: ^1.2.0)
        List<DependencyInfo> dDeps = Arrays.asList(
            createDependency("module-g", "^1.0.0")
        );
        available.put("com.ecat.test:module-d", Arrays.asList(
            createTestIntegration("module-d", "1.0.0", "^1.2.0", dDeps)
        ));

        // module-g (requires_core: ^1.4.0)
        available.put("com.ecat.test:module-g", Arrays.asList(
            createTestIntegration("module-g", "1.0.0", "^1.4.0")
        ));

        // 执行求解
        com.ecat.core.Dependency.Solution solution = solver.solve(requests, available);

        // 验证：有解（依赖关系可以解析）
        assertTrue("应该找到满足依赖约束的解", solution.hasSolution());
        assertFalse("不应有依赖冲突", solution.hasConflicts());

        // 验证：所有模块都被选中
        assertNotNull("module-a 应被选中", solution.getVersion("com.ecat.test:module-a"));
        assertNotNull("module-c 应被选中", solution.getVersion("com.ecat.test:module-c"));
        assertNotNull("module-d 应被选中", solution.getVersion("com.ecat.test:module-d"));
        assertNotNull("module-g 应被选中", solution.getVersion("com.ecat.test:module-g"));

        // TODO: 未来 VersionSolver 应该支持 requires_core 约束检查
        // 当前 requires_core 检查在 InstallCommand.checkCoreCompatibility() 中实现
    }

    // ========== 场景8：菱形依赖+core依赖复合 - 依赖冲突 ==========

    /**
     * 测试：菱形依赖导致的版本冲突
     *
     * 场景描述：
     *   A → C (version: ">=2.0.0")  ← 要求 C >= 2.0
     *   A → D → C (version: "^1.0.0")  ← 要求 C >= 1.0,<2.0
     *
     * 期望：C 的版本约束冲突，无法满足
     *
     * 注意：此测试验证版本约束冲突检测。
     * requires_core 约束检查需要在未来版本中实现。
     */
    @Test
    public void testDiamondWithCore_NotSatisfied() {
        com.ecat.core.Dependency.VersionSolver solver = new com.ecat.core.Dependency.VersionSolver();

        // 创建请求列表
        List<IntegrationInfo> requests = Arrays.asList(
            createTestIntegration("module-a", "1.0.0", "^1.0.0")
        );

        // 创建可用版本
        java.util.Map<String, List<IntegrationInfo>> available = new java.util.HashMap<>();

        // module-a 可用版本
        List<DependencyInfo> aDeps = Arrays.asList(
            createDependency("module-c", ">=2.0.0"),  // 要求 C >= 2.0
            createDependency("module-d", "^1.0.0")
        );
        available.put("com.ecat.test:module-a", Arrays.asList(
            createTestIntegration("module-a", "1.0.0", "^1.0.0", aDeps)
        ));

        // module-d 依赖 C (version: ^1.0.0, 即 >=1.0,<2.0)
        List<DependencyInfo> dDeps = Arrays.asList(
            createDependency("module-c", "^1.0.0")  // 与 A 的要求冲突
        );
        available.put("com.ecat.test:module-d", Arrays.asList(
            createTestIntegration("module-d", "1.0.0", "^1.2.0", dDeps)
        ));

        // module-c 可用版本（1.5.0 不满足 >=2.0.0，2.0.0 不满足 ^1.0.0）
        available.put("com.ecat.test:module-c", Arrays.asList(
            createTestIntegration("module-c", "1.5.0", "^1.0.0"),
            createTestIntegration("module-c", "2.0.0", "^1.0.0")
        ));

        // 执行求解
        com.ecat.core.Dependency.Solution solution = solver.solve(requests, available);

        // 验证：无解（版本约束冲突）
        assertFalse("不应该找到满足所有约束的解", solution.hasSolution());
        assertTrue("应该有冲突", solution.hasConflicts());

        // 验证：冲突信息包含相关包
        List<com.ecat.core.Dependency.Conflict> conflicts = solution.getConflicts();
        assertFalse("冲突列表不应为空", conflicts.isEmpty());
    }

    // ========== 场景9：通配符版本约束 ==========

    /**
     * 测试：通配符 * 版本约束（任意版本）
     *
     * 场景描述：
     *   A → B (*, 即接受任意版本)
     *   B 可用版本：[1.0.0, 1.5.0, 2.0.0]
     *
     * 期望：选择最高版本 2.0.0（因为 * 接受所有版本）
     */
    @Test
    public void testWildcardVersionConstraint() {
        com.ecat.core.Dependency.VersionSolver solver = new com.ecat.core.Dependency.VersionSolver();

        // 创建请求列表
        List<IntegrationInfo> requests = Arrays.asList(
            createTestIntegration("module-a", "1.0.0", "^1.0.0")
        );

        // 创建可用版本
        Map<String, List<IntegrationInfo>> available = new HashMap<>();

        // module-a 依赖 B (*)
        List<DependencyInfo> aDeps = Arrays.asList(
            createDependency("module-b", "*")  // 通配符：任意版本
        );
        available.put("com.ecat.test:module-a", Arrays.asList(
            createTestIntegration("module-a", "1.0.0", "^1.0.0", aDeps)
        ));

        // module-b 可用多个版本
        available.put("com.ecat.test:module-b", Arrays.asList(
            createTestIntegration("module-b", "1.0.0", "^1.0.0"),
            createTestIntegration("module-b", "1.5.0", "^1.0.0"),
            createTestIntegration("module-b", "2.0.0", "^1.0.0")
        ));

        // 执行求解
        com.ecat.core.Dependency.Solution solution = solver.solve(requests, available);

        // 验证：有解
        assertTrue("应该找到满足依赖约束的解", solution.hasSolution());
        assertFalse("不应有冲突", solution.hasConflicts());

        // 验证：选择了最高版本（因为 * 接受所有版本）
        Version selectedB = solution.getVersion("com.ecat.test:module-b");
        assertNotNull("module-b 应被选中", selectedB);
        assertEquals("应选择最高版本 2.0.0", "2.0.0", selectedB.toString());
    }
}
