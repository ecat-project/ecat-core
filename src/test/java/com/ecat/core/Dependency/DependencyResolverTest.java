package com.ecat.core.Dependency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import com.ecat.core.Integration.IntegrationInfo;
import com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport;
import com.ecat.core.Version.Version;

/**
 * DependencyResolver单元测试
 * 注意：现在使用 coordinate (groupId:artifactId) 作为唯一键
 */
public class DependencyResolverTest {

    /**
     * 创建测试用的IntegrationInfo
     */
    private IntegrationInfo createTestIntegration(String artifactId, String version,
            List<String> dependencies, Map<String, String> versionRanges) {
        // 从旧格式创建 DependencyInfo 列表
        List<com.ecat.core.Integration.DependencyInfo> dependencyInfoList = null;
        if (dependencies != null && !dependencies.isEmpty()) {
            dependencyInfoList = new ArrayList<>();
            for (String depArtifactId : dependencies) {
                String range = null;
                if (versionRanges != null && versionRanges.containsKey(depArtifactId)) {
                    range = versionRanges.get(depArtifactId);
                }
                // 使用相同的 groupId (com.ecat.test) 来确保坐标匹配
                dependencyInfoList.add(new com.ecat.core.Integration.DependencyInfo("com.ecat.test", depArtifactId, range));
            }
        }

        IntegrationInfo info = new IntegrationInfo(
            artifactId,
            false,
            dependencyInfoList,
            true,
            "TestClass",
            "com.ecat.test",
            version,
            new WebPlatformSupport(),
            "^1.0.0"  // requiresCore 默认值
        );
        return info;
    }

    // ========== 基本解析测试 ==========

    @Test
    public void testResolve_SingleIntegrationNoDeps() {
        DependencyResolver resolver = new DependencyResolver();

        List<IntegrationInfo> available = new ArrayList<>();
        available.add(createTestIntegration("test-module", "1.0.0",
            Collections.emptyList(), null));

        List<IntegrationInfo> roots = new ArrayList<>();
        roots.add(available.get(0));

        DependencyResolver.ResolutionResult result = resolver.resolve(available, roots);

        assertTrue("解析应该成功", result.isSuccess());
        assertEquals("应选择1个版本", 1, result.getSelectedVersions().size());
        // 现在使用 coordinate (groupId:artifactId) 作为 key
        assertEquals("应选择1.0.0版本", "1.0.0",
            result.getSelectedVersions().get("com.ecat.test:test-module").getVersion().toString());
    }

    @Test
    public void testResolve_SimpleDependencyChain() {
        DependencyResolver resolver = new DependencyResolver();

        // A -> B -> C
        List<IntegrationInfo> available = new ArrayList<>();
        IntegrationInfo c = createTestIntegration("module-c", "1.0.0",
            Collections.emptyList(), null);
        IntegrationInfo b = createTestIntegration("module-b", "1.0.0",
            Arrays.asList("module-c"), null);
        IntegrationInfo a = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), null);

        available.add(c);
        available.add(b);
        available.add(a);

        List<IntegrationInfo> roots = Arrays.asList(a);

        DependencyResolver.ResolutionResult result = resolver.resolve(available, roots);

        assertTrue("解析应该成功", result.isSuccess());
        assertEquals("应选择3个版本", 3, result.getSelectedVersions().size());

        // 验证加载顺序：C, B, A（依赖在前）
        List<IntegrationInfo> loadOrder = result.getLoadOrder();
        assertEquals("应有3个模块", 3, loadOrder.size());
        assertEquals("第一个应该是C", "module-c", loadOrder.get(0).getArtifactId());
        assertEquals("第二个应该是B", "module-b", loadOrder.get(1).getArtifactId());
        assertEquals("第三个应该是A", "module-a", loadOrder.get(2).getArtifactId());
    }

    // ========== 版本约束测试 ==========

    @Test
    public void testResolve_VersionConstraintSelectsHighest() {
        DependencyResolver resolver = new DependencyResolver();

        List<IntegrationInfo> available = new ArrayList<>();

        // 多个版本的module-b
        available.add(createTestIntegration("module-b", "1.0.0",
            Collections.emptyList(), null));
        available.add(createTestIntegration("module-b", "1.2.0",
            Collections.emptyList(), null));
        available.add(createTestIntegration("module-b", "1.1.0",
            Collections.emptyList(), null));

        // module-a依赖 module-b >= 1.1.0
        Map<String, String> ranges = new HashMap<>();
        ranges.put("module-b", ">=1.1.0");
        available.add(createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), ranges));

        List<IntegrationInfo> roots = Arrays.asList(available.get(3));

        DependencyResolver.ResolutionResult result = resolver.resolve(available, roots);

        assertTrue("解析应该成功", result.isSuccess());
        // 现在使用 coordinate (groupId:artifactId) 作为 key
        assertEquals("应选择module-b 1.2.0（最高版本）", "1.2.0",
            result.getSelectedVersions().get("com.ecat.test:module-b").getVersion().toString());
    }

    @Test
    public void testResolve_CaretRange() {
        DependencyResolver resolver = new DependencyResolver();

        List<IntegrationInfo> available = new ArrayList<>();
        available.add(createTestIntegration("module-b", "1.2.0",
            Collections.emptyList(), null));
        available.add(createTestIntegration("module-b", "2.0.0",
            Collections.emptyList(), null));
        available.add(createTestIntegration("module-b", "1.5.0",
            Collections.emptyList(), null));

        // ^1.2.0 表示 >=1.2.0,<2.0.0
        Map<String, String> ranges = new HashMap<>();
        ranges.put("module-b", "^1.2.0");
        available.add(createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), ranges));

        List<IntegrationInfo> roots = Arrays.asList(available.get(3));

        DependencyResolver.ResolutionResult result = resolver.resolve(available, roots);

        assertTrue("解析应该成功", result.isSuccess());
        // 现在使用 coordinate (groupId:artifactId) 作为 key
        assertEquals("应选择1.5.0（^1.2.0范围内的最高版本）", "1.5.0",
            result.getSelectedVersions().get("com.ecat.test:module-b").getVersion().toString());
    }

    @Test
    public void testResolve_TildeRange() {
        DependencyResolver resolver = new DependencyResolver();

        List<IntegrationInfo> available = new ArrayList<>();
        available.add(createTestIntegration("module-b", "1.2.0",
            Collections.emptyList(), null));
        available.add(createTestIntegration("module-b", "1.2.5",
            Collections.emptyList(), null));
        available.add(createTestIntegration("module-b", "1.3.0",
            Collections.emptyList(), null));

        // ~1.2.0 表示 >=1.2.0,<1.3.0
        Map<String, String> ranges = new HashMap<>();
        ranges.put("module-b", "~1.2.0");
        available.add(createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), ranges));

        List<IntegrationInfo> roots = Arrays.asList(available.get(3));

        DependencyResolver.ResolutionResult result = resolver.resolve(available, roots);

        assertTrue("解析应该成功", result.isSuccess());
        // 现在使用 coordinate (groupId:artifactId) 作为 key
        assertEquals("应选择1.2.5（~1.2.0范围内的最高版本）", "1.2.5",
            result.getSelectedVersions().get("com.ecat.test:module-b").getVersion().toString());
    }

    // ========== 冲突检测测试 ==========

    @Test
    public void testResolve_VersionConflict() {
        DependencyResolver resolver = new DependencyResolver();

        List<IntegrationInfo> available = new ArrayList<>();
        available.add(createTestIntegration("module-b", "1.0.0",
            Collections.emptyList(), null));

        // module-a需要 module-b >= 2.0.0，但只有1.0.0
        Map<String, String> ranges = new HashMap<>();
        ranges.put("module-b", ">=2.0.0");
        available.add(createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), ranges));

        List<IntegrationInfo> roots = Arrays.asList(available.get(1));

        DependencyResolver.ResolutionResult result = resolver.resolve(available, roots);

        assertFalse("应该检测到冲突", result.isSuccess());
        assertTrue("应该有冲突", result.hasConflicts());
        assertEquals("应该有1个冲突", 1, result.getConflicts().size());
        assertEquals("冲突类型应该是UNSATISFIED_CONSTRAINT",
            DependencyResolver.ConflictException.ConflictType.UNSATISFIED_CONSTRAINT,
            result.getConflicts().get(0).getType());
    }

    @Test
    public void testResolve_MissingDependency() {
        DependencyResolver resolver = new DependencyResolver();

        List<IntegrationInfo> available = new ArrayList<>();
        // module-a依赖 module-b，但module-b不存在
        available.add(createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), null));

        List<IntegrationInfo> roots = Arrays.asList(available.get(0));

        DependencyResolver.ResolutionResult result = resolver.resolve(available, roots);

        assertFalse("应该检测到缺失依赖", result.isSuccess());
        assertTrue("应该有冲突", result.hasConflicts());
        assertEquals("冲突类型应该是MISSING_DEPENDENCY",
            DependencyResolver.ConflictException.ConflictType.MISSING_DEPENDENCY,
            result.getConflicts().get(0).getType());
    }

    @Test
    public void testResolve_ConflictingConstraints() {
        DependencyResolver resolver = new DependencyResolver();

        List<IntegrationInfo> available = new ArrayList<>();
        available.add(createTestIntegration("module-c", "1.5.0",
            Collections.emptyList(), null));

        // module-a需要 module-c >= 1.0.0,<1.5.0
        Map<String, String> rangesA = new HashMap<>();
        rangesA.put("module-c", ">=1.0.0,<1.5.0");
        IntegrationInfo a = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-c"), rangesA);

        // module-b需要 module-c >= 1.5.0
        Map<String, String> rangesB = new HashMap<>();
        rangesB.put("module-c", ">=1.5.0");
        IntegrationInfo b = createTestIntegration("module-b", "1.0.0",
            Arrays.asList("module-c"), rangesB);

        available.add(a);
        available.add(b);

        List<IntegrationInfo> roots = Arrays.asList(a, b);

        DependencyResolver.ResolutionResult result = resolver.resolve(available, roots);

        // 这两个约束是冲突的：
        // - module-a 需要 >=1.0.0,<1.5.0（即 < 1.5.0）
        // - module-b 需要 >=1.5.0
        // 版本1.5.0满足>=1.5.0但不满足<1.5.0
        assertFalse("应该检测到版本冲突", result.isSuccess());
        assertTrue("应该有冲突", result.hasConflicts());
    }

    // ========== 菱形依赖测试 ==========

    @Test
    public void testResolve_DiamondDependency() {
        DependencyResolver resolver = new DependencyResolver();

        //     A
        //    / \
        //   B   C
        //    \ /
        //     D
        List<IntegrationInfo> available = new ArrayList<>();
        IntegrationInfo d = createTestIntegration("module-d", "1.0.0",
            Collections.emptyList(), null);
        IntegrationInfo b = createTestIntegration("module-b", "1.0.0",
            Arrays.asList("module-d"), null);
        IntegrationInfo c = createTestIntegration("module-c", "1.0.0",
            Arrays.asList("module-d"), null);
        IntegrationInfo a = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b", "module-c"), null);

        available.add(d);
        available.add(b);
        available.add(c);
        available.add(a);

        List<IntegrationInfo> roots = Arrays.asList(a);

        DependencyResolver.ResolutionResult result = resolver.resolve(available, roots);

        assertTrue("解析应该成功", result.isSuccess());
        assertEquals("应选择4个版本", 4, result.getSelectedVersions().size());

        // 验证加载顺序：D, B, C, A
        List<IntegrationInfo> loadOrder = result.getLoadOrder();
        assertEquals("module-d", loadOrder.get(0).getArtifactId());
        // B和C的顺序可能变化，只要它们都在D之后A之前即可
        assertTrue("B和C应该在A之前",
            loadOrder.indexOf(b) < loadOrder.indexOf(a) &&
            loadOrder.indexOf(c) < loadOrder.indexOf(a));
    }
}
