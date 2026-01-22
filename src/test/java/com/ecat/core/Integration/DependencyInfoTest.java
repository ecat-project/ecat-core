package com.ecat.core.Integration;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * DependencyInfo 单元测试
 *
 * @author coffee
 * @version 1.0.0
 */
public class DependencyInfoTest {

    // ========== 构造函数测试 ==========

    @Test
    public void testConstructor_OnlyArtifactId() {
        DependencyInfo dep = new DependencyInfo("integration-modbus");

        assertEquals("artifactId", "integration-modbus", dep.getArtifactId());
        assertEquals("默认 groupId 应为 com.ecat", "com.ecat", dep.getGroupId());
        assertEquals("version 应为 *（任意版本）", "*", dep.getVersion());
        assertTrue("应使用默认 groupId", dep.isDefaultGroupId());
        assertFalse("应无版本约束（* 不算约束）", dep.hasVersionConstraint());
    }

    @Test
    public void testConstructor_ArtifactIdAndVersion() {
        DependencyInfo dep = new DependencyInfo("integration-modbus", "^1.0.0");

        assertEquals("artifactId", "integration-modbus", dep.getArtifactId());
        assertEquals("默认 groupId 应为 com.ecat", "com.ecat", dep.getGroupId());
        assertEquals("version", "^1.0.0", dep.getVersion());
        assertTrue("应有版本约束", dep.hasVersionConstraint());
    }

    @Test
    public void testConstructor_AllFields() {
        DependencyInfo dep = new DependencyInfo(
            "com.github.tusky2015",
            "modbus4j",
            "^3.0.0"
        );

        assertEquals("groupId", "com.github.tusky2015", dep.getGroupId());
        assertEquals("artifactId", "modbus4j", dep.getArtifactId());
        assertEquals("version", "^3.0.0", dep.getVersion());
        assertFalse("应不使用默认 groupId", dep.isDefaultGroupId());
        assertTrue("应有版本约束", dep.hasVersionConstraint());
    }

    @Test
    public void testConstructor_NullGroupId() {
        DependencyInfo dep = new DependencyInfo(null, "modbus4j", null);

        assertEquals("null groupId 应使用默认值", "com.ecat", dep.getGroupId());
        assertTrue("应使用默认 groupId", dep.isDefaultGroupId());
    }

    @Test
    public void testConstructor_EmptyVersion() {
        DependencyInfo dep = new DependencyInfo("integration-modbus", "");

        assertEquals("空版本应存储为空字符串", "", dep.getVersion());
        assertFalse("空字符串不应算有版本约束", dep.hasVersionConstraint());
    }

    // ========== Setter 测试 ==========

    @Test
    public void testSetGroupId() {
        DependencyInfo dep = new DependencyInfo("integration-modbus");

        dep.setGroupId("com.github.tusky2015");

        assertEquals("groupId", "com.github.tusky2015", dep.getGroupId());
        assertFalse("应不使用默认 groupId", dep.isDefaultGroupId());
    }

    @Test
    public void testSetGroupId_Null() {
        DependencyInfo dep = new DependencyInfo("com.github.tusky2015", "modbus4j", null);

        dep.setGroupId(null);

        assertEquals("null groupId 应使用默认值", "com.ecat", dep.getGroupId());
        assertTrue("应使用默认 groupId", dep.isDefaultGroupId());
    }

    @Test
    public void testSetVersion() {
        DependencyInfo dep = new DependencyInfo("integration-modbus");

        dep.setVersion("^1.2.0");

        assertEquals("version", "^1.2.0", dep.getVersion());
        assertTrue("应有版本约束", dep.hasVersionConstraint());
    }

    // ========== 工具方法测试 ==========

    @Test
    public void testGetCoordinate() {
        DependencyInfo dep = new DependencyInfo(
            "com.github.tusky2015",
            "modbus4j",
            "^3.0.0"
        );

        assertEquals("coordinate", "com.github.tusky2015:modbus4j", dep.getCoordinate());
    }

    @Test
    public void testGetCoordinate_DefaultGroupId() {
        DependencyInfo dep = new DependencyInfo("integration-modbus");

        assertEquals("coordinate", "com.ecat:integration-modbus", dep.getCoordinate());
    }

    @Test
    public void testToShortString_WithVersion() {
        DependencyInfo dep = new DependencyInfo(
            "com.ecat",
            "integration-modbus",
            "^1.0.0"
        );

        assertEquals("com.ecat:integration-modbus:^1.0.0", dep.toShortString());
    }

    @Test
    public void testToShortString_WithoutVersion() {
        DependencyInfo dep = new DependencyInfo("integration-modbus");

        assertEquals("com.ecat:integration-modbus:*", dep.toShortString());
    }

    // ========== equals and hashCode 测试 ==========

    @Test
    public void testEquals_SameObject() {
        DependencyInfo dep = new DependencyInfo("integration-modbus", "^1.0.0");

        assertEquals("同一对象应相等", dep, dep);
    }

    @Test
    public void testEquals_EqualObjects() {
        DependencyInfo dep1 = new DependencyInfo("com.ecat", "integration-modbus", "^1.0.0");
        DependencyInfo dep2 = new DependencyInfo("com.ecat", "integration-modbus", "^1.0.0");

        assertEquals("相同字段应相等", dep1, dep2);
        assertEquals("hashCode 应相同", dep1.hashCode(), dep2.hashCode());
    }

    @Test
    public void testEquals_DifferentGroupId() {
        DependencyInfo dep1 = new DependencyInfo("com.ecat", "integration-modbus", "^1.0.0");
        DependencyInfo dep2 = new DependencyInfo("com.github.tusky2015", "integration-modbus", "^1.0.0");

        assertNotEquals("不同 groupId 应不相等", dep1, dep2);
    }

    @Test
    public void testEquals_DifferentArtifactId() {
        DependencyInfo dep1 = new DependencyInfo("integration-modbus", "^1.0.0");
        DependencyInfo dep2 = new DependencyInfo("integration-serial", "^1.0.0");

        assertNotEquals("不同 artifactId 应不相等", dep1, dep2);
    }

    @Test
    public void testEquals_DifferentVersion() {
        DependencyInfo dep1 = new DependencyInfo("integration-modbus", "^1.0.0");
        DependencyInfo dep2 = new DependencyInfo("integration-modbus", "^2.0.0");

        assertNotEquals("不同 version 应不相等", dep1, dep2);
    }

    @Test
    public void testEquals_Null() {
        DependencyInfo dep = new DependencyInfo("integration-modbus");

        assertNotEquals("不应等于 null", dep, null);
    }

    // ========== toString 测试 ==========

    @Test
    public void testToString() {
        DependencyInfo dep = new DependencyInfo(
            "com.github.tusky2015",
            "modbus4j",
            "^3.0.0"
        );

        String result = dep.toString();
        assertTrue("toString 应包含 groupId", result.contains("com.github.tusky2015"));
        assertTrue("toString 应包含 artifactId", result.contains("modbus4j"));
        assertTrue("toString 应包含 version", result.contains("^3.0.0"));
    }

    // ========== 边界情况测试 ==========

    @Test
    public void testHasVersionConstraint_Null() {
        DependencyInfo dep = new DependencyInfo("integration-modbus");

        assertFalse("* 版本应返回 false（* 不算约束）", dep.hasVersionConstraint());
    }

    @Test
    public void testHasVersionConstraint_Empty() {
        DependencyInfo dep = new DependencyInfo("integration-modbus", "");

        assertFalse("空 version 应返回 false", dep.hasVersionConstraint());
    }

    @Test
    public void testIsDefaultGroupId_Custom() {
        DependencyInfo dep = new DependencyInfo(
            "com.github.tusky2015",
            "modbus4j",
            null
        );

        assertFalse("自定义 groupId 应返回 false", dep.isDefaultGroupId());
    }

    // ========== 实际场景测试 ==========

    @Test
    public void testOfficialIntegration_NoVersion() {
        // 官方集成，无版本约束（旧格式兼容）
        DependencyInfo dep = new DependencyInfo("integration-ecat-common");

        assertEquals("com.ecat", dep.getGroupId());
        assertEquals("integration-ecat-common", dep.getArtifactId());
        assertEquals("*", dep.getVersion());  // 默认为 *（任意版本）
        assertTrue(dep.isDefaultGroupId());
        assertFalse(dep.hasVersionConstraint());  // * 不算约束
    }

    @Test
    public void testOfficialIntegration_WithVersion() {
        // 官方集成，有版本约束（新格式）
        DependencyInfo dep = new DependencyInfo(
            "com.ecat",
            "integration-modbus",
            "^1.0.0"
        );

        assertEquals("com.ecat", dep.getGroupId());
        assertEquals("integration-modbus", dep.getArtifactId());
        assertEquals("^1.0.0", dep.getVersion());
        assertTrue(dep.isDefaultGroupId());
        assertTrue(dep.hasVersionConstraint());
    }

    @Test
    public void testThirdPartyIntegration_WithVersion() {
        // 第三方集成，有版本约束
        DependencyInfo dep = new DependencyInfo(
            "com.github.tusky2015",
            "modbus4j",
            "^3.0.0"
        );

        assertEquals("com.github.tusky2015", dep.getGroupId());
        assertEquals("modbus4j", dep.getArtifactId());
        assertEquals("^3.0.0", dep.getVersion());
        assertFalse(dep.isDefaultGroupId());
        assertTrue(dep.hasVersionConstraint());
    }
}
