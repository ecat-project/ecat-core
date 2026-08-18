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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * jar 依赖扫描缓存（F4）契约测试。
 *
 * <p>用真实 jar 夹具（内含 ecat-config.yml，与 {@code readPartialIntegrationInfoFromJar} 的
 * 读取面一致）直接驱动 {@link IntegrationManager#readJarDependencyCoordinates(File)}——
 * 该方法是 getIntegrationStatus / findDependents / saveInitialDependencySnapshot 共用的
 * 重复扫描收口点。
 *
 * <p>被测不变量：
 * <ul>
 *   <li><b>命中</b>：同 jar 重复读不再开 JarFile+解析（{@code jarScanCount} 不增），返回独立可变列表；</li>
 *   <li><b>失效</b>：同路径 jar 被替换（同版本重部署，stat 变化）后读到新依赖列表并重扫；</li>
 *   <li><b>显式失效</b>：{@code invalidateJarDependencyCache} 后强制重扫。</li>
 * </ul>
 *
 * <p>红预测：去掉缓存时命中断言因 {@code jarScanCount} 持续递增而失败。
 *
 * @author coffee
 */
public class JarDependencyScanCacheTest {

    private IntegrationManager manager;
    private File testDir;

    @Before
    public void setUp() {
        testDir = new File("target", ".ecat-jar-dep-cache");
        deleteRecursively(testDir);
        assertTrue("测试目录创建失败: " + testDir, testDir.mkdirs() || testDir.exists());

        manager = new IntegrationManager(mock(EcatCore.class), new IntegrationRegistry(), mock(StateManager.class));
    }

    @After
    public void tearDown() {
        deleteRecursively(testDir);
    }

    @Test
    public void repeatReadOfSameJar_hitsCacheAfterFirstScan() throws Exception {
        File jar = buildJar("fixture-a", "com.ecat:dep-a", "com.ecat:dep-b");

        List<String> first = manager.readJarDependencyCoordinates(jar);
        List<String> second = manager.readJarDependencyCoordinates(jar);

        assertEquals(java.util.Arrays.asList("com.ecat:dep-a", "com.ecat:dep-b"), first);
        assertEquals("命中返回内容一致", first, second);
        assertEquals("同 jar 重复读只扫描一次（后续命中缓存）", 1, manager.jarScanCount.get());
    }

    @Test
    public void hitReturnsIndependentCopy_callerMutationDoesNotPolluteCache() throws Exception {
        File jar = buildJar("fixture-b", "com.ecat:dep-a");
        List<String> first = manager.readJarDependencyCoordinates(jar);
        first.clear(); // 调用方改动返回列表（findDependencies 语义上拥有该列表）

        List<String> second = manager.readJarDependencyCoordinates(jar);
        assertEquals("命中拷出可变副本，调用方清空不回流缓存",
            java.util.Collections.singletonList("com.ecat:dep-a"), second);
    }

    @Test
    public void jarReplacedOnSamePath_cacheInvalidatedAndNewDepsServed() throws Exception {
        File jar = buildJar("fixture-c", "com.ecat:dep-a");
        assertEquals(java.util.Collections.singletonList("com.ecat:dep-a"),
            manager.readJarDependencyCoordinates(jar));
        assertEquals(1, manager.jarScanCount.get());

        // 同版本重部署：同路径覆写不同依赖（mtime/size 变化 → stat 失效）
        buildJar("fixture-c", "com.ecat:dep-x", "com.ecat:dep-y", "com.ecat:dep-z");
        assertEquals(java.util.Arrays.asList("com.ecat:dep-x", "com.ecat:dep-y", "com.ecat:dep-z"),
            manager.readJarDependencyCoordinates(jar));
        assertEquals("替换后重扫一次", 2, manager.jarScanCount.get());
    }

    @Test
    public void explicitInvalidation_forcesRescan() throws Exception {
        File jar = buildJar("fixture-d", "com.ecat:dep-a");
        manager.readJarDependencyCoordinates(jar);
        manager.readJarDependencyCoordinates(jar);
        assertEquals(1, manager.jarScanCount.get());

        manager.invalidateJarDependencyCache();
        manager.readJarDependencyCoordinates(jar);
        assertEquals("显式失效后强制重扫", 2, manager.jarScanCount.get());
    }

    // ==================== 夹具 ====================

    /**
     * 构造含 ecat-config.yml（声明依赖列表）的真实 jar。
     *
     * @param artifactId 夹具名（保证不同夹具内容不同 → 字节数不同）
     * @param depCoordinates 依赖坐标（groupId:artifactId）
     */
    private File buildJar(String artifactId, String... depCoordinates) throws Exception {
        StringBuilder yml = new StringBuilder();
        yml.append("requires_core: \"^1.0.0\"\n");
        yml.append("dependencies:\n");
        for (String dep : depCoordinates) {
            String[] parts = dep.split(":");
            yml.append("  - groupId: ").append(parts[0]).append("\n");
            yml.append("    artifactId: ").append(parts[1]).append("\n");
        }

        File jarFile = new File(testDir, artifactId + "-1.0.0.jar");
        try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(jarFile))) {
            JarEntry entry = new JarEntry("ecat-config.yml");
            jarOut.putNextEntry(entry);
            jarOut.write(yml.toString().getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
        }
        return jarFile;
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
