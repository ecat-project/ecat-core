package com.ecat.core.Integration;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * IntegrationRegistry 单元测试
 *
 * @author coffee
 */
public class IntegrationRegistryTest {

    /**
     * 测试：使用完整坐标查找集成
     */
    @Test
    public void testGetIntegration_FullCoordinate() {
        IntegrationRegistry registry = new IntegrationRegistry();
        MockIntegration integration = new MockIntegration();
        registry.register("com.ecat:integration-test", integration);

        IntegrationBase result = registry.getIntegration("com.ecat:integration-test");
        assertNotNull("应找到集成", result);
        assertSame("应返回同一个实例", integration, result);
    }

    /**
     * 测试：使用纯 artifactId 查找集成（向后兼容）
     */
    @Test
    public void testGetIntegration_ArtifactIdOnly_BackwardCompatibility() {
        IntegrationRegistry registry = new IntegrationRegistry();
        MockIntegration integration = new MockIntegration();
        registry.register("com.ecat:integration-modbus", integration);

        // 使用旧格式 artifactId 查找
        IntegrationBase result = registry.getIntegration("integration-modbus");
        assertNotNull("应找到集成（向后兼容）", result);
        assertSame("应返回同一个实例", integration, result);
    }

    /**
     * 测试：查找不存在的集成
     */
    @Test
    public void testGetIntegration_NotFound() {
        IntegrationRegistry registry = new IntegrationRegistry();

        IntegrationBase result = registry.getIntegration("non-existent");
        assertNull("不存在的集成应返回 null", result);
    }

    /**
     * 测试：优先精确匹配
     */
    @Test
    public void testGetIntegration_Priority_ExactMatch() {
        IntegrationRegistry registry = new IntegrationRegistry();
        MockIntegration integration1 = new MockIntegration();
        MockIntegration integration2 = new MockIntegration();

        // 注册两个集成
        registry.register("com.ecat:integration-test", integration1);
        registry.register("integration-test", integration2);

        // 精确匹配应优先
        IntegrationBase result = registry.getIntegration("integration-test");
        assertSame("应返回精确匹配的集成", integration2, result);
    }

    /**
     * 测试：第三方集成的 groupId
     */
    @Test
    public void testGetIntegration_ThirdPartyGroupId() {
        IntegrationRegistry registry = new IntegrationRegistry();
        MockIntegration integration = new MockIntegration();
        registry.register("com.github.tusky2015:modbus4j", integration);

        // 使用完整坐标查找
        IntegrationBase result = registry.getIntegration("com.github.tusky2015:modbus4j");
        assertNotNull("应找到第三方集成", result);
        assertSame("应返回同一个实例", integration, result);
    }

    /**
     * 测试：使用纯 artifactId 查找第三方集成失败
     */
    @Test
    public void testGetIntegration_ThirdParty_ArtifactIdOnly_NotSupported() {
        IntegrationRegistry registry = new IntegrationRegistry();
        MockIntegration integration = new MockIntegration();
        registry.register("com.github.tusky2015:modbus4j", integration);

        // 使用纯 artifactId 查找第三方集成
        // 向后兼容只支持 com.ecat: 前缀
        IntegrationBase result = registry.getIntegration("modbus4j");
        assertNull("第三方集成的纯 artifactId 查找应返回 null", result);
    }

    // ========== Mock 类 ==========

    private static class MockIntegration extends IntegrationBase {
        @Override
        public void onInit() {
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onPause() {
        }

        @Override
        public void onRelease() {
        }
    }
}
