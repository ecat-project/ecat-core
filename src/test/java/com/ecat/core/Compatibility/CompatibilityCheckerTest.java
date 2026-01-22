package com.ecat.core.Compatibility;

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

/**
 * CompatibilityChecker单元测试
 * 注意：现在使用 coordinate (groupId:artifactId) 作为唯一键
 */
public class CompatibilityCheckerTest {

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

    // ========== 基本兼容性检查测试 ==========

    @Test
    public void testCheckCompatibility_NoIssues() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        integrations.add(createTestIntegration("module-a", "1.0.0",
            Collections.emptyList(), null));

        CompatibilityChecker.CheckResult result =
            checker.checkCompatibility(integrations, null);

        assertTrue("应该没有问题", result.isOK());
        assertFalse("应该没有错误", result.hasErrors());
        assertFalse("应该没有警告", result.hasWarnings());
        assertTrue("问题列表应该为空", result.getIssues().isEmpty());
    }

    @Test
    public void testCheckCompatibility_SatisfiedConstraint() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        IntegrationInfo dep = createTestIntegration("module-b", "1.2.0",
            Collections.emptyList(), null);

        Map<String, String> ranges = new HashMap<>();
        ranges.put("module-b", "^1.0.0"); // >=1.0.0,<2.0.0

        IntegrationInfo root = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), ranges);

        integrations.add(dep);
        integrations.add(root);

        CompatibilityChecker.CheckResult result =
            checker.checkCompatibility(integrations, null);

        assertTrue("版本约束满足，应该没有问题", result.isOK());
    }

    // ========== 版本约束违反测试 ==========

    @Test
    public void testCheckConstraintViolation_LowerThanRequired() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        IntegrationInfo dep = createTestIntegration("module-b", "1.0.0",
            Collections.emptyList(), null);

        Map<String, String> ranges = new HashMap<>();
        ranges.put("module-b", ">=1.5.0"); // 需要 >=1.5.0，但只有1.0.0

        IntegrationInfo root = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), ranges);

        integrations.add(dep);
        integrations.add(root);

        CompatibilityChecker.CheckResult result =
            checker.checkCompatibility(integrations, null);

        assertFalse("应该检测到版本约束违反", result.isOK());
        assertTrue("应该有错误", result.hasErrors());

        List<CompatibilityChecker.CompatibilityIssue> errors = result.getErrors();
        assertEquals("应该有1个错误", 1, errors.size());
        assertEquals("错误类型应该是VERSION_CONSTRAINT_VIOLATION",
            "VERSION_CONSTRAINT_VIOLATION", errors.get(0).getIssueType());
    }

    @Test
    public void testCheckConstraintViolation_HigherThanAllowed() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        IntegrationInfo dep = createTestIntegration("module-b", "2.0.0",
            Collections.emptyList(), null);

        Map<String, String> ranges = new HashMap<>();
        ranges.put("module-b", "<2.0.0"); // 需要 <2.0.0，但是2.0.0

        IntegrationInfo root = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), ranges);

        integrations.add(dep);
        integrations.add(root);

        CompatibilityChecker.CheckResult result =
            checker.checkCompatibility(integrations, null);

        assertFalse("应该检测到版本约束违反", result.isOK());
        assertTrue("应该有错误", result.hasErrors());
    }

    // ========== 缺失依赖测试 ==========

    @Test
    public void testCheckMissingDependency() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        // module-a依赖module-b，但module-b不在列表中
        IntegrationInfo root = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), null);

        integrations.add(root);

        CompatibilityChecker.CheckResult result =
            checker.checkCompatibility(integrations, null);

        assertFalse("应该检测到缺失依赖", result.isOK());
        assertTrue("应该有错误", result.hasErrors());

        List<CompatibilityChecker.CompatibilityIssue> errors = result.getErrors();
        boolean hasMissingDepError = errors.stream()
            .anyMatch(issue -> issue.getIssueType().equals("MISSING_DEPENDENCY"));
        assertTrue("应该有MISSING_DEPENDENCY错误", hasMissingDepError);
    }

    // ========== 无版本约束警告测试 ==========

    @Test
    public void testCheckNoVersionConstraint_Warning() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        IntegrationInfo dep = createTestIntegration("module-b", "1.0.0",
            Collections.emptyList(), null);

        // 有依赖但没有版本约束
        IntegrationInfo root = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), null);

        integrations.add(dep);
        integrations.add(root);

        CompatibilityChecker.CheckResult result =
            checker.checkCompatibility(integrations, null);

        assertTrue("没有版本约束不是错误，只是警告", result.isOK());
        assertTrue("应该有警告", result.hasWarnings());
        assertFalse("不应该有错误", result.hasErrors());

        List<CompatibilityChecker.CompatibilityIssue> warnings = result.getWarnings();
        boolean hasNoConstraintWarning = warnings.stream()
            .anyMatch(issue -> issue.getIssueType().equals("NO_VERSION_CONSTRAINT"));
        assertTrue("应该有NO_VERSION_CONSTRAINT警告", hasNoConstraintWarning);
    }

    // ========== 版本过时测试 ==========

    @Test
    public void testCheckOutdatedVersion_Major() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        integrations.add(createTestIntegration("module-a", "1.0.0",
            Collections.emptyList(), null));

        // 模拟有更高版本可用
        Map<String, List<IntegrationInfo>> allAvailable = new HashMap<>();
        List<IntegrationInfo> availableVersions = new ArrayList<>();
        availableVersions.add(createTestIntegration("module-a", "1.0.0",
            Collections.emptyList(), null));
        availableVersions.add(createTestIntegration("module-a", "3.0.0",
            Collections.emptyList(), null)); // 更新了2个主版本
        allAvailable.put("com.ecat.test:module-a", availableVersions);

        CompatibilityChecker.CheckResult result =
            checker.checkCompatibility(integrations, allAvailable);

        // 主版本落后是WARNING
        List<CompatibilityChecker.CompatibilityIssue> issues = result.getIssues();
        boolean hasOutdatedMajor = issues.stream()
            .anyMatch(issue -> issue.getIssueType().equals("OUTDATED_MAJOR_VERSION"));
        assertTrue("应该检测到主版本过时", hasOutdatedMajor);
    }

    @Test
    public void testCheckOutdatedVersion_Minor() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        integrations.add(createTestIntegration("module-a", "1.0.0",
            Collections.emptyList(), null));

        // 模拟有更高版本可用
        Map<String, List<IntegrationInfo>> allAvailable = new HashMap<>();
        List<IntegrationInfo> availableVersions = new ArrayList<>();
        availableVersions.add(createTestIntegration("module-a", "1.0.0",
            Collections.emptyList(), null));
        availableVersions.add(createTestIntegration("module-a", "1.2.0",
            Collections.emptyList(), null)); // 更新了2个次版本
        allAvailable.put("com.ecat.test:module-a", availableVersions);

        CompatibilityChecker.CheckResult result =
            checker.checkCompatibility(integrations, allAvailable);

        // 次版本落后是INFO级别
        List<CompatibilityChecker.CompatibilityIssue> issues = result.getIssues();
        boolean hasOutdatedMinor = issues.stream()
            .anyMatch(issue -> issue.getIssueType().equals("OUTDATED_MINOR_VERSION"));
        assertTrue("应该检测到次版本过时", hasOutdatedMinor);
    }

    @Test
    public void testCheckOutdatedVersion_Patch() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        integrations.add(createTestIntegration("module-a", "1.0.0",
            Collections.emptyList(), null));

        // 模拟有更高版本可用
        Map<String, List<IntegrationInfo>> allAvailable = new HashMap<>();
        List<IntegrationInfo> availableVersions = new ArrayList<>();
        availableVersions.add(createTestIntegration("module-a", "1.0.0",
            Collections.emptyList(), null));
        availableVersions.add(createTestIntegration("module-a", "1.0.2",
            Collections.emptyList(), null)); // 更新了2个补丁
        allAvailable.put("com.ecat.test:module-a", availableVersions);

        CompatibilityChecker.CheckResult result =
            checker.checkCompatibility(integrations, allAvailable);

        // 补丁版本落后是INFO级别
        List<CompatibilityChecker.CompatibilityIssue> issues = result.getIssues();
        boolean hasOutdatedPatch = issues.stream()
            .anyMatch(issue -> issue.getIssueType().equals("OUTDATED_PATCH_VERSION"));
        assertTrue("应该检测到补丁版本过时", hasOutdatedPatch);
    }

    // ========== 循环依赖测试 ==========

    @Test
    public void testCheckCircularDependency_SimpleCycle() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        // A -> B -> A (循环)
        IntegrationInfo a = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), null);
        IntegrationInfo b = createTestIntegration("module-b", "1.0.0",
            Arrays.asList("module-a"), null);

        integrations.add(a);
        integrations.add(b);

        List<CompatibilityChecker.CompatibilityIssue> issues =
            checker.checkCircularDependencies(integrations);

        assertFalse("应该检测到循环依赖", issues.isEmpty());
        assertEquals("应该有1个问题", 1, issues.size());
        assertEquals("问题类型应该是CIRCULAR_DEPENDENCY",
            "CIRCULAR_DEPENDENCY", issues.get(0).getIssueType());
        assertEquals("严重程度应该是ERROR",
            CompatibilityChecker.CompatibilityIssue.Severity.ERROR,
            issues.get(0).getSeverity());
    }

    @Test
    public void testCheckCircularDependency_ComplexCycle() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        // A -> B -> C -> A (循环)
        IntegrationInfo a = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), null);
        IntegrationInfo b = createTestIntegration("module-b", "1.0.0",
            Arrays.asList("module-c"), null);
        IntegrationInfo c = createTestIntegration("module-c", "1.0.0",
            Arrays.asList("module-a"), null);

        integrations.add(a);
        integrations.add(b);
        integrations.add(c);

        List<CompatibilityChecker.CompatibilityIssue> issues =
            checker.checkCircularDependencies(integrations);

        assertFalse("应该检测到循环依赖", issues.isEmpty());
    }

    @Test
    public void testCheckCircularDependency_NoCycle() {
        CompatibilityChecker checker = new CompatibilityChecker();

        List<IntegrationInfo> integrations = new ArrayList<>();
        // A -> B -> C (无循环)
        IntegrationInfo c = createTestIntegration("module-c", "1.0.0",
            Collections.emptyList(), null);
        IntegrationInfo b = createTestIntegration("module-b", "1.0.0",
            Arrays.asList("module-c"), null);
        IntegrationInfo a = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-b"), null);

        integrations.add(a);
        integrations.add(b);
        integrations.add(c);

        List<CompatibilityChecker.CompatibilityIssue> issues =
            checker.checkCircularDependencies(integrations);

        assertTrue("没有循环依赖，问题列表应该为空", issues.isEmpty());
    }

    // ========== 严重程度测试 ==========

    @Test
    public void testSeverityLevels() {
        CompatibilityChecker checker = new CompatibilityChecker();

        // 创建一个有各种问题的场景
        List<IntegrationInfo> integrations = new ArrayList<>();

        // 1. 缺失依赖（ERROR）
        IntegrationInfo missingDep = createTestIntegration("module-a", "1.0.0",
            Arrays.asList("module-missing"), null);
        integrations.add(missingDep);

        // 2. 版本约束违反（ERROR）
        IntegrationInfo dep = createTestIntegration("module-b", "1.0.0",
            Collections.emptyList(), null);
        Map<String, String> ranges = new HashMap<>();
        ranges.put("module-b", ">=2.0.0");
        IntegrationInfo violation = createTestIntegration("module-c", "1.0.0",
            Arrays.asList("module-b"), ranges);
        integrations.add(dep);
        integrations.add(violation);

        CompatibilityChecker.CheckResult result =
            checker.checkCompatibility(integrations, null);

        assertTrue("应该有错误", result.hasErrors());

        List<CompatibilityChecker.CompatibilityIssue> errors = result.getErrors();
        for (CompatibilityChecker.CompatibilityIssue error : errors) {
            assertEquals("错误应该是ERROR级别",
                CompatibilityChecker.CompatibilityIssue.Severity.ERROR,
                error.getSeverity());
        }
    }
}
