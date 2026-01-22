package com.ecat.core.Integration;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

/**
 * IntegrationInfo 单元测试
 *
 * @author coffee
 * @version 1.0.0
 */
public class IntegrationInfoTest {

    /**
     * 测试：当 dependencyInfoList 为 null 时，getDependencyInfoList() 应返回 null
     */
    @Test
    public void testConstructor_NullDependencyInfoList() {
        IntegrationInfo info = new IntegrationInfo(
            "test-artifact", false, null, true, "TestClass",
            "com.ecat", "1.0.0", new com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport(),
            "^1.0.0"  // requiresCore 默认值
        );

        assertNull("dependencyInfoList 应为 null", info.getDependencyInfoList());
        assertEquals("requiresCore 应为默认值", "^1.0.0", info.getRequiresCore());
    }

    /**
     * 测试：当 dependencyInfoList 不为空时，getDependencyInfoList() 应返回正确的列表
     */
    @Test
    public void testConstructor_WithDependencyInfoList() {
        List<DependencyInfo> dependencyInfoList = Arrays.asList(
            new DependencyInfo("integration-common", "^1.0.0"),
            new DependencyInfo("integration-modbus", "^2.0.0")
        );

        IntegrationInfo info = new IntegrationInfo(
            "test-artifact", false, dependencyInfoList, true, "TestClass",
            "com.ecat", "1.0.0", new com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport(),
            "^1.0.0"  // requiresCore 默认值
        );

        assertNotNull("dependencyInfoList 不应为 null", info.getDependencyInfoList());
        assertEquals("dependencyInfoList 应有 2 个元素", 2, info.getDependencyInfoList().size());
        // 验证 coordinate 格式
        assertEquals("第一个依赖的 coordinate 应为 com.ecat:integration-common",
            "com.ecat:integration-common", info.getDependencyInfoList().get(0).getCoordinate());
        assertEquals("第二个依赖的 coordinate 应为 com.ecat:integration-modbus",
            "com.ecat:integration-modbus", info.getDependencyInfoList().get(1).getCoordinate());
    }

    /**
     * 测试：getCoordinate() 返回正确的坐标
     */
    @Test
    public void testGetCoordinate() {
        IntegrationInfo info = new IntegrationInfo(
            "test-artifact", false, null, true, "TestClass",
            "com.ecat", "1.0.0", new com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport(),
            "^1.0.0"  // requiresCore 默认值
        );

        assertEquals("getCoordinate() 应返回 groupId:artifactId", "com.ecat:test-artifact", info.getCoordinate());
    }

    /**
     * 测试：getCoordinate() 当 groupId 为 null 时使用默认值
     */
    @Test
    public void testGetCoordinate_NullGroupId() {
        IntegrationInfo info = new IntegrationInfo(
            "test-artifact", false, null, true, "TestClass",
            null, "1.0.0", new com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport(),
            "^1.0.0"  // requiresCore 默认值
        );

        assertEquals("getCoordinate() 应使用默认 groupId", "com.ecat:test-artifact", info.getCoordinate());
    }
}
