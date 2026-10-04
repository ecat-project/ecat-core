/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;

/**
 * LoadJarUtils.listLoadedJarFileNames 名册查询单测(T-1-6 §4.4,D39 批准的只读方法):
 * 全活 loader 并集(注册 loader 的 getURLs+parent 链)、嵌套 jar: URL 的最内层 .jar
 * 基名提取、共享集成同 loader 重复注册按实例身份去重、非 .jar URL 零贡献。
 *
 * <p>夹具=JarOutputStream 现造小 jar,loader 拓扑按生产形态搭(parent→ecat 层→child),
 * 全同步零 sleep。</p>
 */
public class LoadJarUtilsLoadedNamesTest {

    private Path tempDir;
    private URLClassLoader parentLoader;
    private LoadJarUtils loadJarUtils;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("loadjar-names-test");
        // 构造器 core 参数未消费(仅 restartClassLoader 参与装配),测试传 null 合法
        parentLoader = new URLClassLoader(
                new URL[] {fixtureJar("fixture-parent-2.0.jar").toUri().toURL()}, null);
        loadJarUtils = new LoadJarUtils(null, parentLoader);
    }

    @After
    public void tearDown() throws Exception {
        if (parentLoader != null) {
            parentLoader.close();
        }
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void enumeratesRegistryAndParentChainWithDedup() throws Exception {
        // 生产拓扑:child 挂 ecat 层之下;同一 child loader 注册两个 jar(共享集成复用形态)
        URLClassLoader child = new URLClassLoader(new URL[0], loadJarUtils.getEcatCoreClassLoader());
        loadJarUtils.loadJar(fixtureJar("fixture-child-a-1.0.jar").toAbsolutePath().toString(), null, child, null);
        loadJarUtils.loadJar(fixtureJar("fixture-child-b-1.0.jar").toAbsolutePath().toString(), null, child, null);

        Set<String> names = loadJarUtils.listLoadedJarFileNames();

        // 精确全集:child 两 jar(主 jar jar: URL 形态)+ parent 链上父 loader 一 jar;
        // 同 loader 双键注册与空 URL 的 ecat 两层零贡献,非 .jar URL 不入册
        Set<String> expected = new HashSet<>(java.util.Arrays.asList(
                "fixture-child-a-1.0", "fixture-child-b-1.0", "fixture-parent-2.0"));
        assertEquals("名册须恰等预期全集(去重后),实得: " + names, expected, names);
    }

    @Test
    public void emptyRegistryLayersContributeNothing() {
        Set<String> names = loadJarUtils.listLoadedJarFileNames();

        // 仅 parent 链一 jar:ecat/ecat-dependent 两层 URL[0] 空贡献
        assertEquals(new HashSet<>(java.util.Collections.singletonList("fixture-parent-2.0")), names);
    }

    private Path fixtureJar(String name) throws Exception {
        Path jar = tempDir.resolve(name);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            out.putNextEntry(new JarEntry("content.txt"));
            out.write(new byte[] {1});
            out.closeEntry();
        }
        return jar;
    }
}
