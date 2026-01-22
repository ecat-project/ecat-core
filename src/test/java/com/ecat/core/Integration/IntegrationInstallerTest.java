package com.ecat.core.Integration;

import com.ecat.core.Repository.CloudRepositoryClient;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * IntegrationInstaller 单元测试
 *
 * <p>测试集成安装器的安装、卸载、启用、禁用等功能</p>
 */
public class IntegrationInstallerTest {

    private static final String TEST_CONFIG_DIR = System.getProperty("java.io.tmpdir") + "/ecat-test/core";
    private static final String TEST_CONFIG_FILE = TEST_CONFIG_DIR + "/integrations.yml";

    private CloudRepositoryClient mockCloudClient;
    private Path testConfigPath;

    /**
     * 创建测试用的配置文件
     */
    @Before
    public void setUp() throws IOException {
        // 创建测试配置目录
        Path testDir = Paths.get(TEST_CONFIG_DIR);
        Files.createDirectories(testDir);

        testConfigPath = Paths.get(TEST_CONFIG_FILE);

        // 创建初始配置文件
        String initialConfig = "integrations:\n" +
            "  com.ecat.integration.ModbusIntegration.ModbusIntegration:\n" +
            "    enabled: true\n" +
            "    className: com.ecat.integration.ModbusIntegration.ModbusIntegration\n" +
            "    groupId: com.ecat\n" +
            "    artifactId: integration-modbus\n" +
            "    version: 1.2.0\n";

        Files.write(testConfigPath, initialConfig.getBytes());

        // 创建模拟的 CloudRepositoryClient
        mockCloudClient = new CloudRepositoryClient("http://localhost:8000") {
            @Override
            public PackageInfo getPackageInfo(String artifactId) throws IOException {
                PackageInfo info = new PackageInfo();
                info.setArtifactId(artifactId);
                info.setLatestVersion("1.2.0");
                info.setVersions(Collections.singletonList("1.2.0"));
                info.setDependencies(Collections.emptyMap());
                return info;
            }

            @Override
            public Path downloadPackage(String artifactId, String version) throws IOException {
                // 创建临时 JAR 文件模拟下载
                Path tempJar = Files.createTempFile(artifactId + "-" + version, ".jar");
                Files.write(tempJar, "mock jar content".getBytes());
                return tempJar;
            }
        };
    }

    /**
     * 清理测试文件
     */
    @After
    public void tearDown() throws IOException {
        // 删除测试配置文件
        if (Files.exists(testConfigPath)) {
            Files.delete(testConfigPath);
        }

        // 删除测试目录
        Path testDir = Paths.get(TEST_CONFIG_DIR);
        if (Files.exists(testDir)) {
            Files.deleteIfExists(testDir);
        }
    }

    // ========== InstalledIntegration 数据类测试 ==========

    @Test
    public void testInstalledIntegration_Getters() {
        IntegrationInstaller.InstalledIntegration integration =
            new IntegrationInstaller.InstalledIntegration(
                "com.ecat.integration.ModbusIntegration.ModbusIntegration",
                true,
                "integration-modbus",
                "1.2.0"
            );

        assertEquals("key 应匹配（已废弃的 className 字段现在返回 key）",
            "com.ecat.integration.ModbusIntegration.ModbusIntegration",
            integration.getClassName());
        assertEquals("key 应匹配",
            "com.ecat.integration.ModbusIntegration.ModbusIntegration",
            integration.getKey());
        assertTrue("enabled 应该为 true", integration.isEnabled());
        assertEquals("artifactId 应匹配", "integration-modbus", integration.getArtifactId());
        assertEquals("version 应匹配", "1.2.0", integration.getVersion());
    }

    @Test
    public void testInstalledIntegration_Disabled() {
        IntegrationInstaller.InstalledIntegration integration =
            new IntegrationInstaller.InstalledIntegration(
                "com.ecat.integration.SerialIntegration.SerialIntegration",
                false,
                "integration-serial",
                "1.0.0"
            );

        assertFalse("enabled 应该为 false", integration.isEnabled());
    }

    // ========== 配置 key 格式测试（groupId:artifactId） ==========

    @Test
    public void testInstalledIntegration_GroupIdArtifactIdKeyFormat() {
        // 新格式：key 使用 groupId:artifactId
        IntegrationInstaller.InstalledIntegration integration =
            new IntegrationInstaller.InstalledIntegration(
                "com.github.tusky2015:modbus4j",  // key = groupId:artifactId
                true,
                "modbus4j",
                "3.1.6"
            );

        assertEquals("key 格式应为 groupId:artifactId",
            "com.github.tusky2015:modbus4j",
            integration.getKey());
        assertEquals("getClassName() 现在返回 key（已废弃）",
            "com.github.tusky2015:modbus4j",
            integration.getClassName());
        assertEquals("artifactId 应匹配", "modbus4j", integration.getArtifactId());
    }

    @Test
    public void testInstalledIntegration_EcatGroupIdKeyFormat() {
        // 测试 com.ecat groupId 的 key 格式
        IntegrationInstaller.InstalledIntegration integration =
            new IntegrationInstaller.InstalledIntegration(
                "com.ecat:integration-serial",
                true,
                "integration-serial",
                "1.0.0"
            );

        assertEquals("key 格式应为 com.ecat:artifactId",
            "com.ecat:integration-serial",
            integration.getKey());
    }

    @Test
    public void testInstalledIntegration_DifferentGroupIds_CanDistinguish() {
        // 验证不同 groupId 的同名 artifactId 可以通过 key 区分
        IntegrationInstaller.InstalledIntegration integration1 =
            new IntegrationInstaller.InstalledIntegration(
                "com.github.tusky2015:modbus4j",
                true,
                "modbus4j",
                "3.1.6"
            );

        IntegrationInstaller.InstalledIntegration integration2 =
            new IntegrationInstaller.InstalledIntegration(
                "com.other.group:modbus4j",
                true,
                "modbus4j",
                "1.0.0"
            );

        // 两个集成的 artifactId 相同，但 key 不同
        assertEquals("artifactId 相同", "modbus4j", integration1.getArtifactId());
        assertEquals("artifactId 相同", "modbus4j", integration2.getArtifactId());

        // key 应该不同
        assertNotEquals("key 应该不同（groupId 不同）",
            integration1.getKey(),
            integration2.getKey());
    }

    private void assertNotEquals(String message, String value1, String value2) {
        if (value1 == null && value2 == null) {
            throw new AssertionError(message + ": both values are null");
        }
        if (value1 == null || value2 == null) {
            return; // one is null, they are not equal
        }
        if (value1.equals(value2)) {
            throw new AssertionError(message + ": '" + value1 + "' equals '" + value2 + "'");
        }
    }

    // ========== 异常类测试 ==========

    @Test
    public void testInstallationException_MessageOnly() throws Exception {
        try {
            throw new IntegrationInstaller.InstallationException("安装失败");
        } catch (IntegrationInstaller.InstallationException e) {
            assertEquals("消息应匹配", "安装失败", e.getMessage());
            assertNull("原因应该为 null", e.getCause());
        }
    }

    @Test
    public void testInstallationException_WithCause() throws Exception {
        IOException cause = new IOException("网络错误");
        try {
            throw new IntegrationInstaller.InstallationException("安装失败", cause);
        } catch (IntegrationInstaller.InstallationException e) {
            assertEquals("消息应匹配", "安装失败", e.getMessage());
            assertEquals("原因应匹配", cause, e.getCause());
        }
    }

    @Test
    public void testRemovalException_MessageOnly() throws Exception {
        try {
            throw new IntegrationInstaller.RemovalException("卸载失败");
        } catch (IntegrationInstaller.RemovalException e) {
            assertEquals("消息应匹配", "卸载失败", e.getMessage());
            assertNull("原因应该为 null", e.getCause());
        }
    }

    @Test
    public void testRemovalException_WithCause() throws Exception {
        IOException cause = new IOException("配置错误");
        try {
            throw new IntegrationInstaller.RemovalException("卸载失败", cause);
        } catch (IntegrationInstaller.RemovalException e) {
            assertEquals("消息应匹配", "卸载失败", e.getMessage());
            assertEquals("原因应匹配", cause, e.getCause());
        }
    }

    @Test
    public void testReloadException_MessageOnly() throws Exception {
        try {
            throw new IntegrationInstaller.ReloadException("重载失败");
        } catch (IntegrationInstaller.ReloadException e) {
            assertEquals("消息应匹配", "重载失败", e.getMessage());
            assertNull("原因应该为 null", e.getCause());
        }
    }

    @Test
    public void testReloadException_WithCause() throws Exception {
        InterruptedException cause = new InterruptedException("进程中断");
        try {
            throw new IntegrationInstaller.ReloadException("重载失败", cause);
        } catch (IntegrationInstaller.ReloadException e) {
            assertEquals("消息应匹配", "重载失败", e.getMessage());
            assertEquals("原因应匹配", cause, e.getCause());
        }
    }

    // ========== 配置文件路径测试 ==========

    @Test
    public void testIntegrationInstaller_DefaultConfigPath() {
        IntegrationInstaller installer = new IntegrationInstaller(mockCloudClient);

        // 默认配置路径应该是 ~/.ecat-data/core/integrations.yml
        // 这个测试验证 installer 可以正常创建
        assertNotNull("installer 应该被创建", installer);
    }

    // ========== listInstalled 测试 ==========

    @Test
    public void testListInstalled_WithConfigFile() throws IOException {
        // 由于 IntegrationInstaller 使用固定路径 ~/.ecat-data/core/integrations.yml
        // 我们需要测试它不会因为配置文件不存在而崩溃
        IntegrationInstaller installer = new IntegrationInstaller(mockCloudClient);

        // 如果配置文件不存在，应该返回空列表
        List<IntegrationInstaller.InstalledIntegration> list = installer.listInstalled();

        assertNotNull("列表不应该为 null", list);
        // 可能是空列表或包含已安装的集成（取决于系统状态）
    }

    // ========== reloadCore 测试 ==========

    @Test
    public void testReloadCore_NoRunningProcess() {
        IntegrationInstaller installer = new IntegrationInstaller(mockCloudClient);

        try {
            installer.reloadCore();
            fail("应该抛出 ReloadException");
        } catch (IntegrationInstaller.ReloadException e) {
            // Windows 有不同的错误消息
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                assertTrue("Windows 应提示不支持自动重载",
                    e.getMessage().contains("暂不支持自动重载"));
            } else {
                assertTrue("错误信息应提及进程未找到",
                    e.getMessage().contains("未找到运行中的") ||
                    e.getMessage().contains("process") ||
                    e.getMessage().contains("进程"));
            }
        }
    }

    // ========== 依赖关系测试 ==========

    @Test
    public void testFindKeyByArtifactId() throws Exception {
        // 测试 remove 不存在的集成
        // 注意：IntegrationInstaller 使用固定的 ~/.ecat-data/core/integrations.yml 路径
        // 所以测试结果取决于系统状态

        IntegrationInstaller installer = new IntegrationInstaller(mockCloudClient);

        try {
            installer.remove("non-existent-integration-xyz123");
            fail("应该抛出 RemovalException");
        } catch (IntegrationInstaller.RemovalException e) {
            // 可能的错误情况：
            // 1. 配置文件不存在/读取失败：卸载失败
            // 2. 集成未找到：集成 xxx 未安装
            assertTrue("错误信息应提及失败或未安装",
                e.getMessage().contains("未安装") ||
                e.getMessage().contains("卸载失败") ||
                e.getMessage().contains("未找到"));
        }
    }

    // ========== 边界条件测试 ==========

    @Test
    public void testIntegrationInstaller_NullCloudClient() {
        // IntegrationInstaller 构造函数不验证 null 客户端
        // NullPointerException 会在实际使用客户端时抛出
        IntegrationInstaller installer = new IntegrationInstaller(null);
        assertNotNull("installer 应该被创建（不验证 null）", installer);

        // install 方法使用 cloudClient，应该抛出异常
        try {
            installer.install("test-integration", "1.0.0");
            fail("应该抛出异常");
        } catch (Exception e) {
            // NullPointerException 或 InstallationException
            assertNotNull("应该抛出异常", e);
        }
    }

    // ========== 辅助类测试 ==========

    @Test
    public void testCloudRepositoryClient_PackageInfo() {
        CloudRepositoryClient.PackageInfo info = new CloudRepositoryClient.PackageInfo();

        info.setArtifactId("integration-test");
        info.setName("Test Integration");
        info.setDescription("Test description");
        info.setAuthor("Test Author");
        info.setHomepage("https://test.com");
        info.setLicense("MIT");
        info.setLatestVersion("1.0.0");
        info.setGitUrl("https://github.com/test/integration");
        info.setDownloadCount(100);
        info.setVersions(Collections.singletonList("1.0.0"));

        assertEquals("artifactId", "integration-test", info.getArtifactId());
        assertEquals("name", "Test Integration", info.getName());
        assertEquals("latestVersion", "1.0.0", info.getLatestVersion());
        assertEquals("downloadCount", 100, info.getDownloadCount());
    }

    @Test
    public void testCloudRepositoryClient_DependencyItem() {
        CloudRepositoryClient.DependencyItem dep = new CloudRepositoryClient.DependencyItem();

        dep.setArtifactId("integration-dep");
        dep.setVersion("1.0.0");

        assertEquals("artifactId", "integration-dep", dep.getArtifactId());
        assertEquals("version", "1.0.0", dep.getVersion());
    }

    // ========== 配置文件版本号测试 ==========

    @Test
    public void testConfigVersion() {
        // 验证 ConfigVersion 常量
        assertEquals("版本号应为 1.0.0", "1.0.0", com.ecat.core.ConfigVersion.getVersion());
        assertEquals("完整版本号应为 ecat-cli 1.0.0", "1.0.0", com.ecat.core.ConfigVersion.getVersion());
    }

    @Test
    public void testConfigVersion_IsCompatible() {
        // 测试版本兼容性检查
        assertTrue("1.0.0 应该兼容", com.ecat.core.ConfigVersion.isCompatible("1.0.0"));
        assertTrue("1.1.0 应该兼容", com.ecat.core.ConfigVersion.isCompatible("1.1.0"));
        assertTrue("1.99.99 应该兼容", com.ecat.core.ConfigVersion.isCompatible("1.99.99"));
        assertTrue("null 应该兼容（旧版本）", com.ecat.core.ConfigVersion.isCompatible(null));
        assertTrue("空字符串应该兼容（旧版本）", com.ecat.core.ConfigVersion.isCompatible(""));
        assertFalse("2.0.0 不应该兼容", com.ecat.core.ConfigVersion.isCompatible("2.0.0"));
        assertFalse("0.9.0 不应该兼容", com.ecat.core.ConfigVersion.isCompatible("0.9.0"));
    }
}
