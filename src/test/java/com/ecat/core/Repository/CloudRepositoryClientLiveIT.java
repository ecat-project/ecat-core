package com.ecat.core.Repository;

import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * CloudRepositoryClient 活体用例（T-4-5 全链演练客户端腿，D39 批准落位）
 *
 * <p>双门控设计，常规构建零影响：
 * ① 命名 *IT 不在 surefire 默认包含集（*Test/Test* /*Tests/*TestCase），
 *    普通 {@code mvn test} 根本不执行本类；
 * ② 即便被 {@code -Dtest=} 显式点名，未注入演练系统属性时 {@link Assume} 全部短路跳过。</p>
 *
 * <p>演练期由 runbook（T-4-5 设计 §5.4 S6）注入真实服务地址与期望值；
 * expected sha256 从服务端 DB 取出经系统属性传入，期望值不由被测输出反推。</p>
 *
 * <pre>
 * mvn test -Dtest=CloudRepositoryClientLiveIT \
 *   -Decat.drill.url=https://mvn.ecat.bellyking.top \
 *   -Decat.drill.group=com.ecat -Decat.drill.artifact=ruoyi \
 *   -Decat.drill.version=5.99.0-drill1 \
 *   -Decat.drill.jar.sha256=&lt;S4 取出的 jar_sha256&gt;
 * </pre>
 *
 * <p>参数分组（S6 拆参数裁定）：batch/dependencies 用注册仓坐标
 * {@code com.ecat:ruoyi}；search/download 走 {@code com.ecat:ruoyi-admin}
 * （子模块行 artifact-first 服务）。本类只定义单一 A 属性，
 * runbook 按 -Dtest 方法过滤分两组注入（零代码改动）。</p>
 *
 * @author coffee
 * @version 1.0.0
 */
public class CloudRepositoryClientLiveIT {

    /** 演练服务地址，例 https://mvn.ecat.bellyking.top；未提供 = 全部用例跳过 */
    private static final String URL = System.getProperty("ecat.drill.url");
    /** groupId，例 com.ecat */
    private static final String G = System.getProperty("ecat.drill.group");
    /** artifactId：batch/dependencies 组=ruoyi（注册仓坐标）；search/download 组=ruoyi-admin */
    private static final String A = System.getProperty("ecat.drill.artifact");
    /** 演练版本，例 5.99.0-drill1 */
    private static final String V = System.getProperty("ecat.drill.version");
    /** 期望 jar sha256（runbook 从服务端 DB 取出注入） */
    private static final String JAR_SHA256 = System.getProperty("ecat.drill.jar.sha256");

    private static void assumeDrillConfigured() {
        Assume.assumeTrue("未提供 -Decat.drill.url，活体用例短路跳过（常规构建零影响）",
                URL != null && !URL.trim().isEmpty());
        Assume.assumeTrue("未提供 -Decat.drill.group/artifact，活体用例短路跳过",
                G != null && !G.trim().isEmpty() && A != null && !A.trim().isEmpty());
    }

    @Test
    public void search_pageSize_echo() throws IOException {
        assumeDrillConfigured();
        CloudRepositoryClient client = new CloudRepositoryClient(URL);

        CloudRepositoryClient.PackageSearchResult result = client.searchPackages(A, 1, 1);

        assertNotNull("搜索响应应可解析", result);
        assertTrue("total 应可解析（≥0）", result.getTotal() >= 0);
        assertEquals("pageSize 应按请求回显", 1, result.getPageSize());
    }

    @Test
    public void batch_query_roundtrip() throws IOException {
        assumeDrillConfigured();
        CloudRepositoryClient client = new CloudRepositoryClient(URL);

        java.util.Map<String, CloudRepositoryClient.PackageInfo> map =
                client.batchQueryPackages(Collections.singletonList(G + ":" + A));

        assertNotNull("batch-query 响应应可解析", map);
        assertTrue("注册仓坐标 " + G + ":" + A + " 应在响应 map 中",
                map.containsKey(G + ":" + A));
    }

    @Test
    public void dependencies_parseable() throws IOException {
        assumeDrillConfigured();
        CloudRepositoryClient client = new CloudRepositoryClient(URL);

        CloudRepositoryClient.DependencyGraph graph = client.getDependencies(G + ":" + A, V);

        assertNotNull("dependencies 应 200 且 DependencyGraph 非 null", graph);
        // 依赖非空档断言（有 ecat-config.yml 的仓）由 runbook 对该仓另行 HTTP 断言，
        // 本用例只锁「可解析」语义——ruoyi 无配置文件，依赖列表为空是合法形态
    }

    @Test
    public void download_matches_sha256() throws IOException {
        assumeDrillConfigured();
        Assume.assumeTrue("未提供 -Decat.drill.jar.sha256，下载腿短路跳过",
                JAR_SHA256 != null && !JAR_SHA256.trim().isEmpty());
        CloudRepositoryClient client = new CloudRepositoryClient(URL);

        Path jar = client.downloadPackage(G + ":" + A, V);

        assertNotNull("下载应返回落盘路径", jar);
        assertTrue("下载文件应存在: " + jar, Files.exists(jar));
        assertEquals("下载字节 sha256 应与服务端 DB 哈希逐字节一致",
                JAR_SHA256.toLowerCase(), sha256OfFile(jar));
    }

    private static String sha256OfFile(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // JLS 规定 JVM 必须提供 SHA-256,缺席属运行环境违约,响亮失败不静默
            throw new IllegalStateException("JVM 必须提供 SHA-256 算法", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
