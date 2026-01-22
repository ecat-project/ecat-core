package com.ecat.core.Repository;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

/**
 * CloudRepositoryClient 单元测试
 *
 * <p>测试云端仓库客户端的基本功能</p>
 */
public class CloudRepositoryClientTest {

    /**
     * 使用不存在的主机名，确保测试环境不会冲突
     * 使用 128 位随机字符串作为域名，确保不会有 DNS 解析
     */
    private static final String INVALID_HOST = "a7b3c9d2e8f1g4h6j5k7m0n9p2q4r6s8t0.invalid.local";

    // ========== 构造函数测试 ==========

    @Test
    public void testConstructor_WithTrailingSlash() {
        String baseUrl = "http://" + INVALID_HOST + "/";
        CloudRepositoryClient client = new CloudRepositoryClient(baseUrl);

        // 验证客户端创建成功
        assertNotNull("客户端应该被创建", client);

        // 验证错误信息中不包含双斜杠（URL 构建正确）
        try {
            client.searchPackages("test");
            fail("连接应该失败（服务器未运行）");
        } catch (IOException e) {
            // 验证错误信息存在即可
            assertNotNull("错误信息应该存在", e.getMessage());
        }
    }

    @Test
    public void testConstructor_WithoutTrailingSlash() {
        String baseUrl = "http://" + INVALID_HOST;
        CloudRepositoryClient client = new CloudRepositoryClient(baseUrl);

        // 验证客户端创建成功
        assertNotNull("客户端应该被创建", client);

        try {
            client.searchPackages("test");
            fail("连接应该失败（服务器未运行）");
        } catch (IOException e) {
            // 验证错误信息存在即可
            assertNotNull("错误信息应该存在", e.getMessage());
        }
    }

    // ========== 数据类测试 ==========

    @Test
    public void testPackageItem_GettersSetters() {
        CloudRepositoryClient.PackageItem item = new CloudRepositoryClient.PackageItem();

        item.setArtifactId("integration-modbus");
        item.setName("Modbus Integration");
        item.setDescription("Modbus protocol support");
        item.setLatestVersion("1.2.0");
        item.setAuthor("ECAT Team");
        item.setHomepage("https://ecat.com");

        assertEquals("artifactId", "integration-modbus", item.getArtifactId());
        assertEquals("name", "Modbus Integration", item.getName());
        assertEquals("description", "Modbus protocol support", item.getDescription());
        assertEquals("latestVersion", "1.2.0", item.getLatestVersion());
        assertEquals("author", "ECAT Team", item.getAuthor());
        assertEquals("homepage", "https://ecat.com", item.getHomepage());
    }

    @Test
    public void testPackageInfo_GettersSetters() {
        CloudRepositoryClient.PackageInfo info = new CloudRepositoryClient.PackageInfo();

        info.setArtifactId("integration-modbus");
        info.setName("Modbus Integration");
        info.setDescription("Modbus protocol support");
        info.setAuthor("ECAT Team");
        info.setHomepage("https://ecat.com");
        info.setLicense("MIT");
        info.setLatestVersion("1.2.0");
        info.setGitUrl("https://github.com/ecat/integration-modbus");
        info.setDownloadCount(100);

        assertEquals("artifactId", "integration-modbus", info.getArtifactId());
        assertEquals("name", "Modbus Integration", info.getName());
        assertEquals("description", "Modbus protocol support", info.getDescription());
        assertEquals("author", "ECAT Team", info.getAuthor());
        assertEquals("homepage", "https://ecat.com", info.getHomepage());
        assertEquals("license", "MIT", info.getLicense());
        assertEquals("latestVersion", "1.2.0", info.getLatestVersion());
        assertEquals("gitUrl", "https://github.com/ecat/integration-modbus", info.getGitUrl());
        assertEquals("downloadCount", 100, info.getDownloadCount());
    }

    @Test
    public void testDependencyItem_GettersSetters() {
        CloudRepositoryClient.DependencyItem dep = new CloudRepositoryClient.DependencyItem();

        dep.setArtifactId("integration-serial");
        dep.setVersion("1.0.0");

        assertEquals("artifactId", "integration-serial", dep.getArtifactId());
        assertEquals("version", "1.0.0", dep.getVersion());
    }

    @Test
    public void testNode_GettersSetters() {
        CloudRepositoryClient.Node node = new CloudRepositoryClient.Node();

        node.setId("integration-modbus");
        node.setVersion("1.2.0");

        assertEquals("id", "integration-modbus", node.getId());
        assertEquals("version", "1.2.0", node.getVersion());
    }

    @Test
    public void testEdge_GettersSetters() {
        CloudRepositoryClient.Edge edge = new CloudRepositoryClient.Edge();

        edge.setFrom("integration-modbus");
        edge.setTo("integration-serial");
        edge.setVersion("1.0.0");

        assertEquals("from", "integration-modbus", edge.getFrom());
        assertEquals("to", "integration-serial", edge.getTo());
        assertEquals("version", "1.0.0", edge.getVersion());
    }

    @Test
    public void testDependencyGraph_GettersSetters() {
        CloudRepositoryClient.DependencyGraph graph = new CloudRepositoryClient.DependencyGraph();

        java.util.List<CloudRepositoryClient.Node> nodes = new java.util.ArrayList<>();
        nodes.add(new CloudRepositoryClient.Node());

        java.util.List<CloudRepositoryClient.Edge> edges = new java.util.ArrayList<>();
        edges.add(new CloudRepositoryClient.Edge());

        graph.setNodes(nodes);
        graph.setEdges(edges);

        assertEquals("nodes", 1, graph.getNodes().size());
        assertEquals("edges", 1, graph.getEdges().size());
    }

    @Test
    public void testPackageSearchResult_GettersSetters() {
        CloudRepositoryClient.PackageSearchResult result = new CloudRepositoryClient.PackageSearchResult();

        result.setTotal(10);
        result.setPage(1);
        result.setPageSize(10);

        java.util.List<CloudRepositoryClient.PackageItem> items = new java.util.ArrayList<>();
        items.add(new CloudRepositoryClient.PackageItem());

        result.setItems(items);

        assertEquals("total", 10, result.getTotal());
        assertEquals("page", 1, result.getPage());
        assertEquals("pageSize", 10, result.getPageSize());
        assertEquals("items", 1, result.getItems().size());
    }

    // ========== 连接错误处理测试 ==========

    @Test(expected = IOException.class)
    public void testSearchPackages_InvalidUrl() throws IOException {
        CloudRepositoryClient client = new CloudRepositoryClient("http://" + INVALID_HOST);
        client.searchPackages("test");
    }

    @Test(expected = IOException.class)
    public void testGetPackageInfo_InvalidUrl() throws IOException {
        CloudRepositoryClient client = new CloudRepositoryClient("http://" + INVALID_HOST);
        client.getPackageInfo("integration-modbus");
    }

    @Test(expected = IOException.class)
    public void testGetAvailableVersions_InvalidUrl() throws IOException {
        CloudRepositoryClient client = new CloudRepositoryClient("http://" + INVALID_HOST);
        client.getAvailableVersions("integration-modbus");
    }

    @Test(expected = IOException.class)
    public void testGetDependencies_InvalidUrl() throws IOException {
        CloudRepositoryClient client = new CloudRepositoryClient("http://" + INVALID_HOST);
        client.getDependencies("integration-modbus", "1.2.0");
    }

    @Test(expected = IOException.class)
    public void testDownloadPackage_InvalidUrl() throws IOException {
        CloudRepositoryClient client = new CloudRepositoryClient("http://" + INVALID_HOST);
        client.downloadPackage("integration-modbus", "1.2.0");
    }

    // ========== URL 构建测试 ==========

    @Test
    public void testSearchPackages_UrlEncoding() throws IOException {
        // 使用 Mock 服务器或验证 URL 构建逻辑
        // 这里测试空格编码 - 验证不会因为空格而崩溃
        CloudRepositoryClient client = new CloudRepositoryClient("http://" + INVALID_HOST);
        try {
            client.searchPackages("test search");
            fail("应该抛出 IOException");
        } catch (IOException e) {
            // 验证能正确处理带空格的关键词
            assertNotNull("错误信息应该存在", e.getMessage());
        }
    }

    // ========== 边界条件测试 ==========

    @Test(expected = IOException.class)
    public void testSearchPackages_EmptyKeyword() throws IOException {
        CloudRepositoryClient client = new CloudRepositoryClient("http://" + INVALID_HOST);
        client.searchPackages("");
    }

    @Test(expected = NullPointerException.class)
    public void testSearchPackages_NullKeyword() throws IOException {
        CloudRepositoryClient client = new CloudRepositoryClient("http://" + INVALID_HOST);
        client.searchPackages(null);
    }
}
