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

import com.ecat.core.EcatCore;
import com.ecat.core.State.StateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * integrations.yml 读缓存（F6）契约测试。
 *
 * <p>被测不变量：
 * <ul>
 *   <li><b>命中</b>：stat 未变时重复读不再 IO+解析（{@code configParseCount} 不增）；</li>
 *   <li><b>调用方隔离</b>：读出口是深拷贝私有副本，调用方改返回值不污染缓存快照；</li>
 *   <li><b>失效（原子替换）</b>：tmp+rename 同字节长度替换后，新内容立即可见——stat 戳
 *       （inode/mtime/size）必然变化，缓存不可喂旧值；</li>
 *   <li><b>写后立即可见</b>：{@code saveIntegrationConfig} 写路径主动换新缓存，随后的读命中且
 *       内容即新配置（零次重解析）；</li>
 *   <li><b>删除重建</b>：文件删除后读返回空（并废弃缓存），重建后读反映磁盘内容。</li>
 * </ul>
 *
 * <p>红预测：去掉缓存（每次全量重读）时，命中/写后可见两断言因 {@code configParseCount} 持续
 * 递增而失败；去掉深拷贝时，隔离断言因调用方改动回流缓存而失败。
 *
 * @author coffee
 */
public class IntegrationsYmlReadCacheTest {

    private IntegrationManager manager;
    private File testDir;
    private File configFile;
    /** 静态路径字段先值，@After 恢复，避免泄漏到同 JVM 的后续测试。 */
    private static String originalConfigPath;
    private static String originalItemPath;

    @Before
    public void setUp() throws Exception {
        testDir = new File("target", ".ecat-yml-read-cache");
        deleteRecursively(testDir);
        assertTrue("测试目录创建失败: " + testDir, testDir.mkdirs() || testDir.exists());
        configFile = new File(testDir, "core/integrations.yml");
        assertTrue(configFile.getParentFile().mkdirs() || configFile.getParentFile().exists());

        manager = new IntegrationManager(mock(EcatCore.class), new IntegrationRegistry(), mock(StateManager.class));

        originalConfigPath = (String) getStaticField("INTEGRATIONS_CONFIG_PATH");
        originalItemPath = (String) getStaticField("INTEGRATION_ITEM_PATH");
        setStaticField("INTEGRATIONS_CONFIG_PATH", configFile.getAbsolutePath());
        setStaticField("INTEGRATION_ITEM_PATH", testDir.getAbsolutePath() + "/integrations/%s.yml");
    }

    @After
    public void tearDown() throws Exception {
        if (originalConfigPath != null) {
            setStaticField("INTEGRATIONS_CONFIG_PATH", originalConfigPath);
        }
        if (originalItemPath != null) {
            setStaticField("INTEGRATION_ITEM_PATH", originalItemPath);
        }
        deleteRecursively(testDir);
    }

    @Test
    public void repeatReadWithUnchangedFile_hitsCacheAfterFirstParse() {
        atomicWrite(configFile, yamlOf("aaaa"));

        Map<String, Map<String, Object>> first = manager.loadIntegrationsConfig();
        Map<String, Map<String, Object>> second = manager.loadIntegrationsConfig();
        Map<String, Map<String, Object>> third = manager.loadIntegrationsConfig();

        assertEquals("内容一致", "aaaa", stateOf(first));
        assertEquals("内容一致", "aaaa", stateOf(second));
        assertEquals("stat 未变的重复读只解析一次（后续命中缓存）", 1, manager.configParseCount.get());
    }

    @Test
    public void returnedMapIsCallerPrivate_mutationDoesNotPolluteCache() {
        atomicWrite(configFile, yamlOf("aaaa"));
        Map<String, Map<String, Object>> first = manager.loadIntegrationsConfig();

        // 调用方读改写路径（saveIntegrationConfig 语义）：在返回值上直接改
        first.put("polluted-root", new java.util.HashMap<String, Object>());
        first.computeIfAbsent("integrations", k -> new java.util.LinkedHashMap<String, Object>())
            .put("polluted:key", new java.util.HashMap<String, Object>());

        Map<String, Map<String, Object>> second = manager.loadIntegrationsConfig();
        assertFalse("外层改动不得回流缓存", second.containsKey("polluted-root"));
        assertFalse("integrations 节点改动不得回流缓存",
            ((Map<?, ?>) second.get("integrations")).containsKey("polluted:key"));
        assertEquals("改动不回流，第二次读仍是缓存命中", 1, manager.configParseCount.get());
    }

    @Test
    public void atomicReplaceWithSameByteLength_invalidatesCacheAndServesNewContent() throws Exception {
        atomicWrite(configFile, yamlOf("aaaa"));
        Map<String, Map<String, Object>> before = manager.loadIntegrationsConfig();
        assertEquals("aaaa", stateOf(before));
        assertEquals(1, manager.configParseCount.get());
        long sizeBefore = Files.size(configFile.toPath());

        // 同字节长度原子替换（AAAA→BBBB）：mtime/inode 变化必须触发失效，缓存不得喂旧值
        atomicWrite(configFile, yamlOf("bbbb"));
        assertEquals("测试夹具自检：两版内容须同字节数（隔离 size 信号，逼失效走 mtime/inode）",
            sizeBefore, Files.size(configFile.toPath()));

        Map<String, Map<String, Object>> after = manager.loadIntegrationsConfig();
        assertEquals("替换后读必须看到新内容", "bbbb", stateOf(after));
        assertEquals("失效后重解析一次", 2, manager.configParseCount.get());
    }

    @Test
    public void saveIntegrationConfig_writeThenReadSeesNewConfigViaCacheRefresh() {
        manager.saveIntegrationConfig("com.ecat:int-a", integrationCfg("int-a"));
        Map<String, Map<String, Object>> first = manager.loadIntegrationsConfig();
        assertTrue("写后读立即可见新配置", ((Map<?, ?>) first.get("integrations")).containsKey("com.ecat:int-a"));

        manager.saveIntegrationConfig("com.ecat:int-b", integrationCfg("int-b"));
        Map<String, Map<String, Object>> second = manager.loadIntegrationsConfig();
        Map<?, ?> integrations = (Map<?, ?>) second.get("integrations");
        assertTrue("第二次写后读同时可见新旧键", integrations.containsKey("com.ecat:int-a"));
        assertTrue("第二次写后读同时可见新旧键", integrations.containsKey("com.ecat:int-b"));

        // 写路径主动换新缓存：全程（含 save 内部的读）零次磁盘解析——文件由写方创建而非外部放置
        assertEquals("写后读全走缓存刷新，无重解析", 0, manager.configParseCount.get());
    }

    @Test
    public void fileDeletedThenRecreated_readsReflectDiskNotStaleCache() throws Exception {
        atomicWrite(configFile, yamlOf("aaaa"));
        assertEquals("aaaa", stateOf(manager.loadIntegrationsConfig()));

        assertTrue("夹具自检：删除文件", configFile.delete());
        Map<String, Map<String, Object>> empty = manager.loadIntegrationsConfig();
        assertTrue("文件删除后读返回空配置", empty == null || empty.isEmpty());

        atomicWrite(configFile, yamlOf("cccc"));
        Map<String, Map<String, Object>> recreated = manager.loadIntegrationsConfig();
        assertEquals("重建后读反映磁盘内容（不喂删除前的陈旧缓存）", "cccc", stateOf(recreated));
    }

    // ==================== 夹具 ====================

    /** 与生产写方同款原子替换：tmp + ATOMIC_MOVE（真实外部替换场景）。 */
    private static void atomicWrite(File target, String content) {
        try {
            Path tmp = Files.createTempFile(target.getParentFile().toPath(), target.getName(), ".tmp");
            try (OutputStream out = new FileOutputStream(tmp.toFile())) {
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            try {
                Files.move(tmp, target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("夹具写入失败: " + target, e);
        }
    }

    /** 固定字节数的合法配置（state 值同长度可互换：aaaa/bbbb/cccc）。 */
    private static String yamlOf(String stateValue) {
        return "# integrations\n"
            + "integrations:\n"
            + "  com.ecat:fixture:\n"
            + "    groupId: com.ecat\n"
            + "    artifactId: fixture\n"
            + "    version: 1.0.0\n"
            + "    enabled: true\n"
            + "    state: " + stateValue + "\n";
    }

    @SuppressWarnings("unchecked")
    private static String stateOf(Map<String, Map<String, Object>> config) {
        Map<String, Object> integrations = (Map<String, Object>) config.get("integrations");
        Map<String, Object> entry = (Map<String, Object>) integrations.get("com.ecat:fixture");
        return (String) entry.get("state");
    }

    private static java.util.Map<String, Object> integrationCfg(String artifactId) {
        java.util.Map<String, Object> cfg = new java.util.LinkedHashMap<String, Object>();
        cfg.put("groupId", "com.ecat");
        cfg.put("artifactId", artifactId);
        cfg.put("version", "1.0.0");
        cfg.put("enabled", true);
        return cfg;
    }

    private static Object getStaticField(String name) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
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
