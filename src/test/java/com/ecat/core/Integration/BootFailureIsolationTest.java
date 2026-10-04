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

package com.ecat.core.Integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport;
import com.ecat.core.Observability.StartupReportHolder;
import com.ecat.core.State.StateManager;
import com.ecat.core.Utils.JarDependencyLoader;
import com.ecat.core.Utils.LoadOrderResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * 启动失败两级分级立法契约测试：单坐标失败（jar 缺失 / 入口类扫描失败 / ecat-config.yml
 * 解析失败 / 依赖缺失闭包）一律隔离点账、boot 照常完成；全局态损坏（integrations.yml
 * YAML 损坏）杀 boot 保留；运行时热加载路经既有回滚链传导解析失败。
 *
 * <p>技法：夹具 jar = JarOutputStream 写入 ecat-config.yml + 按需嵌入
 * {@link GateStubIntegration} 的 .class（顶层类，扫描器跳含 $ 条目故嵌套类不可用）；
 * jar 路径经 {@code user.home} 重定向到临时目录；INTEGRATIONS_CONFIG_PATH 静态反射指向
 * 临时文件（@Before 存原值 @After 还原——静态字段跨测试类共享，不复原会污染同 JVM 后续用例）。
 * 失败明细断言经 {@code tracker.render(0L)} 输出串 contains("&lt;coordinate&gt;:&lt;stage&gt;")
 * 形态（failures 字段 private，StartupLoadTrackerTest 先例即 render 串断言）。
 *
 * <p>红预测：旧形态下 parse-fail 坐标以默认实例混入（无点账）、jar 缺失静默、缺依赖
 * 抛 IllegalStateException 杀 boot、扫描失败 RuntimeException 杀 boot——分别被
 * 点账/不抛/闭包断言抓红。
 */
public class BootFailureIsolationTest {

    private File testDir;
    private String originalUserHome;
    private String originalConfigPath;

    @Before
    public void setUp() throws Exception {
        testDir = new File("target", ".ecat-boot-failure-isolation");
        deleteRecursively(testDir);
        assertTrue("测试目录创建失败: " + testDir, testDir.mkdirs() || testDir.exists());

        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", testDir.getAbsolutePath());

        Field configPathField = IntegrationManager.class.getDeclaredField("INTEGRATIONS_CONFIG_PATH");
        configPathField.setAccessible(true);
        originalConfigPath = (String) configPathField.get(null);
    }

    @After
    public void tearDown() throws Exception {
        System.setProperty("user.home", originalUserHome);
        setStaticConfigPath(originalConfigPath);
        deleteRecursively(testDir);
    }

    // ==================== 分级③ 解析失败隔离 ====================

    /** 坏 yml jar：隔离点账不杀 boot，健康邻坐标照常入列（负向：旧吞异常混默认集成形态已死） */
    @Test
    public void parseFailJar_isolatedAndAccounted_healthyNeighborStillScanned() throws Exception {
        buildFixtureJar("badjar", true, true);
        buildFixtureJar("goodjar", true, false);

        Map<String, Object> itgs = new LinkedHashMap<>();
        itgs.put("com.ecat:badjar", gav("badjar"));
        itgs.put("com.ecat:goodjar", gav("goodjar"));
        Map<String, List<String>> dependencyMap = new HashMap<>();
        List<IntegrationInfo> infos = new ArrayList<>();
        StartupLoadTracker tracker = new StartupLoadTracker();

        IntegrationManager manager = newManager();
        manager.collectIntegrationInfos(itgs, dependencyMap, infos, tracker);   // 不抛=boot 照常

        String report = tracker.render(0L);
        assertTrue("坏坐标应记 parse-fail，实报: " + report,
            report.contains("com.ecat:badjar:" + StartupLoadTracker.STAGE_PARSE_FAIL));
        assertEquals("坏坐标不入列", 1, infos.size());
        assertEquals("com.ecat:goodjar", infos.get(0).getCoordinate());
        assertFalse("坏坐标不进依赖映射", dependencyMap.containsKey("com.ecat:badjar"));
        assertTrue(dependencyMap.containsKey("com.ecat:goodjar"));
    }

    // ==================== 分级① jar 缺失点账 ====================

    /** jar 缺失：点账可见（负向：旧静默 continue 形态已死） */
    @Test
    public void missingJar_accountedAndSkipped() throws Exception {
        // user.home 已重定向且未落任何夹具 jar → 指向不存在路径
        Map<String, Object> itgs = new LinkedHashMap<>();
        itgs.put("com.ecat:ghost", gav("ghost"));
        Map<String, List<String>> dependencyMap = new HashMap<>();
        List<IntegrationInfo> infos = new ArrayList<>();
        StartupLoadTracker tracker = new StartupLoadTracker();

        newManager().collectIntegrationInfos(itgs, dependencyMap, infos, tracker);

        String report = tracker.render(0L);
        assertTrue("缺失坐标应记 jar-missing，实报: " + report,
            report.contains("com.ecat:ghost:" + StartupLoadTracker.STAGE_JAR_MISSING));
        assertTrue(infos.isEmpty());
    }

    // ==================== 分级④ 依赖缺失闭包隔离 ====================

    /**
     * A 依赖 B（B 不在列）、D 依赖 A、E 独立 → A+D 传递闭包隔离、E 照常；
     * 不抛 IllegalStateException（负向：旧缺依赖杀 boot 崩溃形态已根治）。
     */
    @Test
    public void missingDependencyBlocksTransitiveClosure_withoutKillingBoot() {
        List<IntegrationInfo> infos = new ArrayList<>();
        infos.add(info("groupA", "a", Arrays.asList("groupB:b")));
        infos.add(info("groupD", "d", Arrays.asList("groupA:a")));
        infos.add(info("groupE", "e", null));

        Map<String, List<String>> dependencyMap = new LinkedHashMap<>();
        dependencyMap.put("groupA:a", Arrays.asList("groupB:b"));
        dependencyMap.put("groupD:d", Arrays.asList("groupA:a"));
        dependencyMap.put("groupE:e", new ArrayList<String>());

        LoadOrderResult result = JarDependencyLoader.getLoadOrder(infos, dependencyMap);   // 不抛 ISE

        Map<String, String> blocked = result.getBlockedCoordinates();
        assertEquals("A(种子)+D(传播)两级隔离", 2, blocked.size());
        assertTrue("种子原因含缺失坐标: " + blocked.get("groupA:a"),
            blocked.get("groupA:a").contains("缺少依赖") && blocked.get("groupA:a").contains("groupB:b"));
        assertTrue("传播原因含隔离源: " + blocked.get("groupD:d"),
            blocked.get("groupD:d").contains("groupA:a") && blocked.get("groupD:d").contains("因依赖缺失被隔离"));

        // 种子先于传播（LinkedHashMap 保序）
        Set<String> blockedOrder = blocked.keySet();
        assertEquals("种子 A 先记账", "groupA:a", blockedOrder.iterator().next());

        assertEquals("loadOrder 仅含独立坐标 E", 1, result.getLoadOrder().size());
        assertEquals("groupE:e", result.getLoadOrder().get(0).getCoordinate());

        assertClosureInvariants(result.getLoadOrder(), blocked, dependencyMap);
    }

    // ==================== 全局态：YAML 损坏杀 boot 保留 ====================

    /** integrations.yml 损坏 → YAMLException 上抛杀 boot（负向：全局态分级未松动） */
    @Test
    public void corruptIntegrationsYml_stillKillsBoot() throws Exception {
        File cfg = new File(testDir, "integrations.yml");
        writeString(cfg, "version: \"4.0\"\nintegrations: [unclosed");
        setStaticConfigPath(cfg.getPath());

        YAMLException ex = assertThrows(YAMLException.class, newManager()::loadIntegrations);
        assertNotNull("解析错误消息应可见", ex.getMessage());
    }

    // ==================== 分级② 扫描失败隔离 ====================

    /** 无入口类 jar：scan-fail 点账隔离不杀 boot，健康邻坐标照常（负向：旧 System.err+RuntimeException 杀 boot 形态已死） */
    @Test
    public void entryScanFail_isolatedNotKillingBoot() throws Exception {
        buildFixtureJar("noentry", false, false);   // 只有 ecat-config.yml，无入口类
        buildFixtureJar("goodjar2", true, false);

        Map<String, Object> itgs = new LinkedHashMap<>();
        itgs.put("com.ecat:noentry", gav("noentry"));
        itgs.put("com.ecat:goodjar2", gav("goodjar2"));
        Map<String, List<String>> dependencyMap = new HashMap<>();
        List<IntegrationInfo> infos = new ArrayList<>();
        StartupLoadTracker tracker = new StartupLoadTracker();

        IntegrationManager manager = newManager();
        manager.collectIntegrationInfos(itgs, dependencyMap, infos, tracker);   // 不抛=boot 照常

        String report = tracker.render(0L);
        assertTrue("无入口类坐标应记 scan-fail，实报: " + report,
            report.contains("com.ecat:noentry:" + StartupLoadTracker.STAGE_SCAN_FAIL));
        assertEquals(1, infos.size());
        assertEquals("com.ecat:goodjar2", infos.get(0).getCoordinate());
    }

    // ==================== 分级④ 端到端：boot 完成且不加载 ====================

    /**
     * 唯一集成声明依赖不存在的坐标 → 该集成 dep-missing 隔离，boot 照常完成（不抛、
     * ALL_LOADED 链路走完、快照落账），registry 无实例（未进加载循环）。
     */
    @Test
    public void depMissingIntegration_bootCompletes_withoutLoading() throws Exception {
        File cfg = new File(testDir, "integrations.yml");
        writeString(cfg, "version: \"4.0\"\n"
            + "integrations:\n"
            + "  com.ecat:lonely:\n"
            + "    groupId: com.ecat\n"
            + "    artifactId: lonely\n"
            + "    version: \"1.0.0\"\n"
            + "    enabled: true\n");
        setStaticConfigPath(cfg.getPath());
        buildFixtureJar("lonely", true, false, "com.ecat:ghost");

        IntegrationManager manager = newManager();
        manager.loadIntegrations();   // 不抛=boot 完成（旧形态此处 IllegalStateException→RuntimeException 杀 boot）

        IntegrationRegistry registry = readRegistryField(manager);
        IntegrationBase instance = registry.getIntegration("com.ecat:lonely");
        assertNull("被隔离坐标不得实例化加载", instance);

        StartupReportHolder.Snapshot snapshot = StartupReportHolder.getLatest();
        assertNotNull("boot 完成应落启动快照", snapshot);
        assertFalse("被隔离坐标不得按成功加载记账",
            snapshot.getPerIntegrationMs().containsKey("com.ecat:lonely"));
    }

    // ==================== 运行时回滚传导（buildIntegrationInfo 路） ====================

    /** addIntegration 遇坏 yml jar：既有 catch 回滚 → STOPPED + 解析失败消息可见 + yml 回写停用 */
    @Test
    public void addIntegrationWithBadJar_rollsBackWithParseFailMessage() throws Exception {
        File cfg = new File(testDir, "integrations.yml");
        writeString(cfg, "version: \"4.0\"\nintegrations: {}\n");
        setStaticConfigPath(cfg.getPath());
        buildFixtureJar("badjar3", true, true);

        Map<String, Object> config = new HashMap<>();
        config.put("groupId", "com.ecat");
        config.put("artifactId", "badjar3");
        config.put("version", "1.0.0");

        IntegrationStatus status = newManager().addIntegration("com.ecat:badjar3", config);

        assertEquals(IntegrationState.STOPPED, status.getState());
        assertTrue("回滚消息应含解析失败链路消息: " + status.getMessage(),
            status.getMessage().contains("解析集成 ecat-config.yml 失败"));
        String ymlAfter = new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
        assertTrue("回滚应把该坐标写回停用态: " + ymlAfter, ymlAfter.contains("enabled: false"));
    }

    // ==================== 协同不变量（§3.2） ====================

    /** 四条协同不变量：不相交 / blocked ⊆ 已声明 / loadOrder 依赖闭包自洽 / reason 可溯源缺失 */
    private static void assertClosureInvariants(List<IntegrationInfo> loadOrder,
            Map<String, String> blocked, Map<String, List<String>> dependencyMap) {
        Set<String> orderCoords = new HashSet<>();
        for (IntegrationInfo info : loadOrder) {
            assertFalse("blocked ∩ loadOrder = ∅，冲突: " + info.getCoordinate(),
                blocked.containsKey(info.getCoordinate()));
            orderCoords.add(info.getCoordinate());
        }
        for (IntegrationInfo info : loadOrder) {
            List<String> deps = dependencyMap.get(info.getCoordinate());
            if (deps != null) {
                for (String dep : deps) {
                    assertTrue("loadOrder 闭包性质: " + info.getCoordinate() + " 的依赖 "
                        + dep + " 应在 loadOrder 内", orderCoords.contains(dep));
                }
            }
        }
        for (Map.Entry<String, String> blockedEntry : blocked.entrySet()) {
            assertTrue("blocked ⊆ 已声明坐标: " + blockedEntry.getKey(),
                dependencyMap.containsKey(blockedEntry.getKey()));
            assertTrue("reason 可溯源到缺失依赖: " + blockedEntry.getValue(),
                blockedEntry.getValue().contains("缺少依赖")
                    || blockedEntry.getValue().contains("因依赖缺失被隔离"));
        }
    }

    // ==================== 夹具 ====================

    private IntegrationManager newManager() {
        return new IntegrationManager(mock(EcatCore.class), new IntegrationRegistry(),
            mock(StateManager.class));
    }

    /** integrationRegistry 无公开读口，测试经反射取出（IntegrationManagerTest setPrivateField 先例同法） */
    private static IntegrationRegistry readRegistryField(IntegrationManager manager) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField("integrationRegistry");
        field.setAccessible(true);
        return (IntegrationRegistry) field.get(manager);
    }

    private static void setStaticConfigPath(String path) throws Exception {
        Field configPathField = IntegrationManager.class.getDeclaredField("INTEGRATIONS_CONFIG_PATH");
        configPathField.setAccessible(true);
        configPathField.set(null, path);
    }

    /** itgs 条目值（groupId:artifactId 形态坐标，enabled 打开） */
    private static Map<String, Object> gav(String artifactId) {
        Map<String, Object> config = new HashMap<>();
        config.put("groupId", "com.ecat");
        config.put("artifactId", artifactId);
        config.put("version", "1.0.0");
        config.put("enabled", true);
        return config;
    }

    private static IntegrationInfo info(String groupId, String artifactId, List<String> depCoordinates) {
        List<DependencyInfo> deps = null;
        if (depCoordinates != null) {
            deps = new ArrayList<>();
            for (String coordinate : depCoordinates) {
                String[] parts = coordinate.split(":");
                deps.add(new DependencyInfo(parts[0], parts[1], "*"));
            }
        }
        return new IntegrationInfo(artifactId, false, deps, true, "Stub", groupId, "1.0.0",
            new WebPlatformSupport(), "^1.0.0");
    }

    /**
     * 构造夹具 jar，落位 fake user.home 的本地仓路径（boot 扫描 / getJarPath 同款拼接）。
     *
     * @param withEntryClass true=嵌入 GateStubIntegration.class（false=NO_ENTRY_CLASS 场景）
     * @param corruptYml true=ecat-config.yml 写成未闭合 flow 序列（解析失败场景）
     * @param depCoordinates 声明依赖（dep-missing 场景）
     */
    private File buildFixtureJar(String artifactId, boolean withEntryClass, boolean corruptYml,
            String... depCoordinates) throws IOException {
        StringBuilder yml = new StringBuilder();
        yml.append("requires_core: \"^1.0.0\"\n");
        if (depCoordinates.length > 0) {
            yml.append("dependencies:\n");
            for (String dep : depCoordinates) {
                String[] parts = dep.split(":");
                yml.append("  - groupId: ").append(parts[0]).append("\n");
                yml.append("    artifactId: ").append(parts[1]).append("\n");
            }
        }
        String ymlContent = corruptYml
            ? "requires_core: \"^1.0.0\"\ndependencies: [\"com.ecat:broken\""
            : yml.toString();

        File jarFile = new File(testDir,
            ".m2/repository/com/ecat/" + artifactId + "/1.0.0/" + artifactId + "-1.0.0.jar");
        jarFile.getParentFile().mkdirs();
        try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(jarFile))) {
            jarOut.putNextEntry(new JarEntry("ecat-config.yml"));
            jarOut.write(ymlContent.getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
            if (withEntryClass) {
                copyEntryClass(jarOut);
            }
        }
        return jarFile;
    }

    /** GateStubIntegration.class 经资源流拷入夹具 jar（包路径=二进制名，扫描器按路径还原类名加载） */
    private static void copyEntryClass(JarOutputStream jarOut) throws IOException {
        Class<?> stub = GateStubIntegration.class;
        String entryName = stub.getName().replace('.', '/') + ".class";
        try (InputStream in = stub.getResourceAsStream("/" + entryName)) {
            assertNotNull("GateStubIntegration.class 须在 test-classes（先行 mvnd test-compile）", in);
            jarOut.putNextEntry(new JarEntry(entryName));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                jarOut.write(buffer, 0, read);
            }
            jarOut.closeEntry();
        }
    }

    private static void writeString(File file, String content) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
