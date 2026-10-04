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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport;
import com.ecat.core.Observability.StartupReportHolder;
import com.ecat.core.State.StateManager;
import com.ecat.core.Version.CoreVersionMismatchException;
import com.ecat.core.Version.CoreVersionsTestAccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * requires_core 加载门契约测试：门三路判定（不满足/缺失/解析失败→
 * CoreVersionMismatchException，满足→放行）、boot 加载循环坐标隔离点账、
 * 运行时安装/启用两入口既有回滚链传导、unloadIntegration 对未实例化坐标无害、
 * 缺省兜底消亡（IntegrationInfo null 透传）。
 *
 * <p>技法：门逻辑=包级纯函数直驱（与被测方法同包零反射零 mock）；boot/运行时入口=
 * 夹具 jar（JarOutputStream 写 ecat-config.yml + 嵌入顶层类 {@link GateStubIntegration}
 * 的 .class——嵌套类编译产物含 $ 会被扫描器无视）+ user.home 重定向 + INTEGRATIONS_CONFIG_PATH
 * 静态反射指向带文件格式版本戳的临时 yml + CoreVersionsTestAccess 注版本缝。
 * 失败分类断言经 logback ListAppender 捕 startup-report 渲染行（tracker 为 loadIntegrations
 * 局部量，failures 明细唯一出口是 render 日志行）。
 *
 * <p>红预测：无门时 gate-a(^999.0.0) 照常实例化加载（T2 隔离断言红）、
 * add/enable 返回 RUNNING（T3/T4 回滚断言红）、null 约束被 "^1.0.0" 兜底放行（T5 红）。
 *
 * @author coffee
 */
public class CoreRequiresCoreGateTest {

    private static final String ACTUAL_CORE = "4.0.0";

    private File testDir;
    private String originalUserHome;
    private String originalConfigPath;
    private java.util.function.Supplier<String> originalVersionSource;
    private Logger managerLogger;
    private ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender;

    @Before
    public void setUp() throws Exception {
        testDir = new File("target", ".ecat-requires-core-gate");
        deleteRecursively(testDir);
        assertTrue("测试目录创建失败: " + testDir, testDir.mkdirs() || testDir.exists());

        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", testDir.getAbsolutePath());

        Field configPathField = IntegrationManager.class.getDeclaredField("INTEGRATIONS_CONFIG_PATH");
        configPathField.setAccessible(true);
        originalConfigPath = (String) configPathField.get(null);

        // core 版本缝注值：单测从 classes/ 加载无 manifest，门经 CoreVersions.current() 取 actual
        originalVersionSource = CoreVersionsTestAccess.currentSource();
        CoreVersionsTestAccess.injectSource(() -> ACTUAL_CORE);
    }

    @After
    public void tearDown() throws Exception {
        if (managerLogger != null && appender != null) {
            managerLogger.detachAppender(appender);
        }
        System.setProperty("user.home", originalUserHome);
        setStaticConfigPath(originalConfigPath);
        CoreVersionsTestAccess.injectSource(originalVersionSource);
        deleteRecursively(testDir);
    }

    // ==================== T1 门有牙：不满足拒绝 ====================

    /** 约束 ^4.0.0 对 core 3.9.0 不满足 → 拒绝；三要素载荷逐项对得上入参，message 自带修复指引 */
    @Test
    public void gateRejectsUnsatisfiedConstraint_withFullPayload() {
        IntegrationInfo info = info("com.ecat:gate-a", "^4.0.0");

        CoreVersionMismatchException ex = assertThrows(CoreVersionMismatchException.class,
            () -> IntegrationManager.enforceRequiresCore(info, "3.9.0"));

        assertEquals("com.ecat:gate-a", ex.getCoordinate());
        assertEquals("^4.0.0", ex.getRequiresCore());
        assertEquals("3.9.0", ex.getActualCoreVersion());
        assertTrue("message 应含不满足语义与修复指引: " + ex.getMessage(),
            ex.getMessage().contains("不满足") && ex.getMessage().contains("升级 core"));
    }

    // ==================== T2 boot 隔离：单坐标拒载不陪葬 ====================

    /**
     * boot 端到端：A(^999.0.0) 被门拒载并记 requires-core 点账，B(*) 照常加载，
     * loadIntegrations 不抛（boot 照常完成、快照落账）。
     */
    @Test
    public void bootIsolatesGateFailedCoordinate_neighborStillLoads() throws Exception {
        File cfg = new File(testDir, "integrations.yml");
        writeString(cfg, "version: \"4.0\"\n"
            + "integrations:\n"
            + "  com.ecat:gate-a:\n"
            + "    groupId: com.ecat\n"
            + "    artifactId: gate-a\n"
            + "    version: \"1.0.0\"\n"
            + "    enabled: true\n"
            + "  com.ecat:gate-b:\n"
            + "    groupId: com.ecat\n"
            + "    artifactId: gate-b\n"
            + "    version: \"1.0.0\"\n"
            + "    enabled: true\n");
        setStaticConfigPath(cfg.getPath());
        buildFixtureJar("gate-a", "^999.0.0");
        buildFixtureJar("gate-b", "*");

        managerLogger = (Logger) LoggerFactory.getLogger(IntegrationManager.class);
        appender = new ListAppender<>();
        appender.start();
        managerLogger.addAppender(appender);

        IntegrationManager manager = newManager();
        manager.loadIntegrations();   // 不抛=boot 照常完成

        // 失败分类可见：startup-report 行含 <coord>:requires-core 且仅此一笔
        String report = appender.list.stream()
            .map(e -> e.getFormattedMessage())
            .filter(msg -> msg.contains("startup-report:"))
            .findFirst()
            .orElse("");
        assertTrue("startup-report 应含 requires-core 失败明细: " + report,
            report.contains("com.ecat:gate-a:" + StartupLoadTracker.STAGE_REQUIRES_CORE));
        assertTrue("failed 计数应为 1（仅 gate-a）: " + report, report.contains("failed=1"));

        IntegrationRegistry registry = readRegistryField(manager);
        assertNull("被拒坐标不得实例化", registry.getIntegration("com.ecat:gate-a"));
        assertNotNull("合法约束坐标照常加载", registry.getIntegration("com.ecat:gate-b"));

        // boot 完成=快照落账；被拒坐标不得按成功加载记账
        StartupReportHolder.Snapshot snapshot = StartupReportHolder.getLatest();
        assertNotNull("boot 完成应落启动快照", snapshot);
        assertTrue("合法坐标应计入分项耗时",
            snapshot.getPerIntegrationMs().containsKey("com.ecat:gate-b"));
        assertTrue("被拒坐标不得按成功加载记账",
            !snapshot.getPerIntegrationMs().containsKey("com.ecat:gate-a"));
    }

    // ==================== T3 运行时安装：既有回滚链传导 ====================

    /** addIntegration 遇门拒：STOPPED + 消息含三要素 + 无实例残留 + yml 回写停用 */
    @Test
    public void addIntegrationGateFailure_rollsBackViaExistingCatch() throws Exception {
        File cfg = new File(testDir, "integrations.yml");
        writeString(cfg, "version: \"4.0\"\nintegrations: {}\n");
        setStaticConfigPath(cfg.getPath());
        buildFixtureJar("gate-a", "^999.0.0");

        Map<String, Object> config = new HashMap<>();
        config.put("groupId", "com.ecat");
        config.put("artifactId", "gate-a");
        config.put("version", "1.0.0");

        IntegrationManager manager = newManager();
        IntegrationStatus status = manager.addIntegration("com.ecat:gate-a", config);

        assertEquals(IntegrationState.STOPPED, status.getState());
        assertTrue("消息应含「热加载失败」与三要素: " + status.getMessage(),
            status.getMessage().contains("热加载失败")
                && status.getMessage().contains("com.ecat:gate-a")
                && status.getMessage().contains("^999.0.0")
                && status.getMessage().contains(ACTUAL_CORE));

        IntegrationRegistry registry = readRegistryField(manager);
        assertNull("回滚后不得残留实例", registry.getIntegration("com.ecat:gate-a"));

        String ymlAfter = new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
        assertTrue("回滚应把该坐标写回 integrations.yml 停用态: " + ymlAfter,
            ymlAfter.contains("com.ecat:gate-a") && ymlAfter.contains("enabled: false"));
    }

    // ==================== T4 运行时启用：同款回滚 ====================

    /** enableIntegration 遇门拒（无实例路径）：STOPPED + 消息含三要素 + yml 保持停用 */
    @Test
    public void enableIntegrationGateFailure_returnsStoppedWithoutYmlPromotion() throws Exception {
        File cfg = new File(testDir, "integrations.yml");
        writeString(cfg, "version: \"4.0\"\n"
            + "integrations:\n"
            + "  com.ecat:gate-a:\n"
            + "    groupId: com.ecat\n"
            + "    artifactId: gate-a\n"
            + "    version: \"1.0.0\"\n"
            + "    enabled: false\n"
            + "    state: STOPPED\n");
        setStaticConfigPath(cfg.getPath());
        buildFixtureJar("gate-a", "^999.0.0");

        IntegrationManager manager = newManager();
        IntegrationStatus status = manager.enableIntegration("com.ecat:gate-a");

        assertEquals(IntegrationState.STOPPED, status.getState());
        assertTrue("消息应含「启用失败」与三要素: " + status.getMessage(),
            status.getMessage().contains("启用失败")
                && status.getMessage().contains("com.ecat:gate-a")
                && status.getMessage().contains("^999.0.0")
                && status.getMessage().contains(ACTUAL_CORE));

        IntegrationRegistry registry = readRegistryField(manager);
        assertNull("失败路径不得残留实例", registry.getIntegration("com.ecat:gate-a"));

        String ymlAfter = new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
        assertTrue("yml 应保持停用态（失败不晋升 RUNNING）: " + ymlAfter,
            ymlAfter.contains("enabled: false") && ymlAfter.contains("STOPPED"));
    }

    // ==================== T5 缺省兜底已死：缺失形态拒绝 ====================

    /** (a) null 约束→缺失形态拒绝；(b) IntegrationInfo 对 null 保持 null 透传 */
    @Test
    public void missingConstraint_rejectedAsMissingForm_defaultFallbackDead() {
        IntegrationInfo info = info("com.ecat:no-decl", null);

        CoreVersionMismatchException ex = assertThrows(CoreVersionMismatchException.class,
            () -> IntegrationManager.enforceRequiresCore(info, "3.1.0"));
        assertTrue("缺失形态 message 应含未声明语义与修复指引: " + ex.getMessage(),
            ex.getMessage().contains("未声明 requires_core") && ex.getMessage().contains("显式声明"));
        assertNull("缺失形态载荷 requiresCore=null", ex.getRequiresCore());

        IntegrationInfo constructed = new IntegrationInfo("no-decl", false, null, true,
            "Stub", "com.ecat", "1.0.0", new WebPlatformSupport(), null);
        assertNull("构造器不得再兜底默认约束", constructed.getRequiresCore());
    }

    // ==================== T6 六语法门级判定 ====================

    /**
     * 六语法以 actual=3.1.0 逐形态判定：* / 精确 3.1.0 / 区间 / ~3.0.0（现实现=[3.0.0,4.0.0)）/
     * ^3.1.0 放行；`3.0.0 || 9.9.9` 按第一支 3.0.0 精确 ≠ 3.1.0 拒绝。
     */
    @Test
    public void sixConstraintSyntaxForms_judgedPerVersionRangeSemantics() {
        String[][] passForms = {
            {"*", "通配放行一切"},
            {"3.1.0", "精确命中放行"},
            {">=3.0.0,<3.2.0", "双端区间命中放行"},
            {"~3.0.0", "X.0.0 形态按现实现=[3.0.0,4.0.0) 放行"},
            {"^3.1.0", "caret 下界含等放行"},
        };
        for (String[] form : passForms) {
            IntegrationInfo info = info("com.ecat:syntax", form[0]);
            IntegrationManager.enforceRequiresCore(info, "3.1.0");   // 不抛=放行
        }

        CoreVersionMismatchException rejected = assertThrows(CoreVersionMismatchException.class,
            () -> IntegrationManager.enforceRequiresCore(info("com.ecat:syntax", "3.0.0 || 9.9.9"), "3.1.0"));
        assertTrue("或约束按第一支判: " + rejected.getMessage(),
            rejected.getMessage().contains("不满足") && rejected.getMessage().contains("3.0.0 || 9.9.9"));
    }

    // ==================== T7 unload 无害性 ====================

    /** 对未实例化坐标直调 unloadIntegration：无异常、registry 零变化（门失败坐标回滚无害性） */
    @Test
    public void unloadIntegrationOnUnloadedCoordinate_harmless() throws Exception {
        IntegrationManager manager = newManager();
        IntegrationRegistry registry = readRegistryField(manager);
        assertNull("前置：坐标未注册", registry.getIntegration("com.ecat:never-there"));

        manager.unloadIntegration("com.ecat:never-there");   // 不抛=无害

        assertNull("registry 零变化", registry.getIntegration("com.ecat:never-there"));
    }

    // ==================== T8 解析失败形态 ====================

    /** 非法约束串：解析失败形态上浮，cause=IllegalArgumentException 原因链保留 */
    @Test
    public void illegalConstraint_reportsParseFailureForm_withCauseChain() {
        CoreVersionMismatchException ex = assertThrows(CoreVersionMismatchException.class,
            () -> IntegrationManager.enforceRequiresCore(info("com.ecat:bad-syntax", "abc"), "3.1.0"));

        assertTrue("cause 应为 IllegalArgumentException", ex.getCause() instanceof IllegalArgumentException);
        assertTrue("message 应含无法解析语义与原文: " + ex.getMessage(),
            ex.getMessage().contains("无法解析") && ex.getMessage().contains("abc"));
        assertEquals("abc", ex.getRequiresCore());
        assertEquals("3.1.0", ex.getActualCoreVersion());
    }

    // ==================== 夹具 ====================

    private IntegrationManager newManager() {
        return new IntegrationManager(mock(EcatCore.class), new IntegrationRegistry(),
            mock(StateManager.class));
    }

    /** integrationRegistry 无公开读口，测试经反射取出（BootFailureIsolationTest readRegistryField 同法） */
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

    private static IntegrationInfo info(String coordinate, String requiresCore) {
        int colon = coordinate.indexOf(':');
        String groupId = coordinate.substring(0, colon);
        String artifactId = coordinate.substring(colon + 1);
        return new IntegrationInfo(artifactId, false, null, true, "Stub", groupId, "1.0.0",
            new WebPlatformSupport(), requiresCore);
    }

    /** 夹具 jar 落位 fake user.home 本地仓路径，ecat-config.yml 带指定 requires_core + 入口类 .class */
    private File buildFixtureJar(String artifactId, String requiresCore) throws IOException {
        String yml = "requires_core: \"" + requiresCore + "\"\n";
        File jarFile = new File(testDir,
            ".m2/repository/com/ecat/" + artifactId + "/1.0.0/" + artifactId + "-1.0.0.jar");
        jarFile.getParentFile().mkdirs();
        try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(jarFile))) {
            jarOut.putNextEntry(new JarEntry("ecat-config.yml"));
            jarOut.write(yml.getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();

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
        return jarFile;
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
