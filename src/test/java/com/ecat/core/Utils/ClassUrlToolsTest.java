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

package com.ecat.core.Utils;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * ClassUrlTools.decodeUrlPath 的 path 语义解码契约测试：
 * %XX（中文/空格）解码、字面 "+" 保持加号（path 中空格只编码为 %20，
 * 表单语义的 "+"=空格 会把含加号目录的 jar 路径解错）。
 * 断言形态与两个调用方（ResourceLoader/IntegrationCoordinateHelper）一致：
 * getPath() → 剥 "file:" 前缀 → 作为磁盘路径使用。
 * 另锁 canonicalJarUrl 的本地 jar URL 构造契约（file 部分来自 File.toURI()）。
 */
public class ClassUrlToolsTest {

    @Test
    public void plusChineseAndSpaceInJarPathAllSurvive() throws Exception {
        // 真实磁盘路径同时含 中文 + 加号 + 空格
        Path dir = Files.createTempDirectory("classurl-plus-test");
        Path jarDir = dir.resolve("我的+目录 app");
        Files.createDirectories(jarDir);
        File jar = jarDir.resolve("fixture.jar").toFile();
        Files.write(jar.toPath(), new byte[] {1});

        // 生产形态：URLClassLoader 资源 URL = jar:file:/…!/entry（file 部分为编码态）
        URL classUrl = new URL("jar:" + jar.toURI().toURL() + "!/com/example/Foo.class");

        URL decoded = ClassUrlTools.decodeUrlPath(classUrl);
        String path = decoded.getPath();
        int separatorIndex = path.indexOf("!/");
        String jarPath = path.substring(5, separatorIndex); // 同调用方剥 "file:" 前缀

        assertTrue("含 +/中文/空格 的路径须逐字还原并可打开: " + jarPath, new File(jarPath).exists());
    }

    @Test
    public void nullPassthrough() {
        assertNull(ClassUrlTools.decodeUrlPath(null));
    }

    @Test
    public void canonicalJarUrlEncodesPathByFileUri() throws Exception {
        Path dir = Files.createTempDirectory("classurl-jar-test");
        File jar = dir.resolve("space + dir").resolve("fixture.jar").toFile();
        Files.createDirectories(jar.getParentFile().toPath());
        Files.write(jar.toPath(), new byte[] {1});

        URL url = ClassUrlTools.canonicalJarUrl(jar);

        assertEquals(new URL("jar:" + jar.toURI() + "!/"), url);
        assertFalse("URL 中不得出现未编码空格: " + url, url.toString().contains(" "));
    }

    @Test
    public void canonicalJarUrlRejectsNullFile() throws Exception {
        try {
            ClassUrlTools.canonicalJarUrl(null);
            fail("null jar 文件应显式拒绝");
        } catch (NullPointerException expected) {
            assertEquals("jarFile", expected.getMessage());
        }
    }

    @Test
    public void physicalCodeSourcePathResolvesFileUrl() throws Exception {
        Path dir = Files.createTempDirectory("classurl-file-source");

        Path resolved = ClassUrlTools.physicalCodeSourcePath(dir.toUri().toURL());

        assertEquals(dir.toAbsolutePath(), resolved.toAbsolutePath());
    }

    @Test
    public void physicalCodeSourcePathResolvesCanonicalJarUrl() throws Exception {
        Path dir = Files.createTempDirectory("classurl-jar-source");
        File jar = dir.resolve("中文 + 目录").resolve("fixture.jar").toFile();
        Files.createDirectories(jar.getParentFile().toPath());
        Files.write(jar.toPath(), new byte[] {1});

        URL codeSource = ClassUrlTools.canonicalJarUrl(jar);
        Path resolved = ClassUrlTools.physicalCodeSourcePath(codeSource);

        assertEquals(jar.toPath().toAbsolutePath(), resolved.toAbsolutePath());
    }

    @Test
    public void windowsDriveJarUrlFollowsPlatformUriConversion() throws Exception {
        URL codeSource = new URL("jar:file:/C:/Users/ecat/library.jar!/");

        Path resolved = ClassUrlTools.physicalCodeSourcePath(codeSource);

        assertEquals(Paths.get(new URI("file:/C:/Users/ecat/library.jar")), resolved);
    }

    @Test
    public void jarConnectionInnerUrlWinsOverExternalString() throws Exception {
        Path dir = Files.createTempDirectory("classurl-inner-url");
        File jar = dir.resolve("real-bridge.jar").toFile();
        Files.write(jar.toPath(), new byte[] {1});
        final URL innerFileUrl = jar.toURI().toURL();

        URL externalLie = new URL(null, "jar:file:/C:/Users/ecat/library.jar!/",
            new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) throws IOException {
                    return new JarURLConnection(url) {
                        @Override
                        public URL getJarFileURL() {
                            return innerFileUrl;
                        }

                        @Override
                        public void connect() {
                            // openConnection 只建连接对象；本用例不触发 jar I/O。
                        }

                        @Override
                        public java.util.jar.JarFile getJarFile() {
                            throw new UnsupportedOperationException("测试连接不打开 jar 文件");
                        }
                    };
                }
            });

        Path resolved = ClassUrlTools.physicalCodeSourcePath(externalLie);

        assertEquals(jar.toPath().toAbsolutePath(), resolved.toAbsolutePath());
    }

    @Test
    public void physicalCodeSourcePathRejectsNull() throws Exception {
        try {
            ClassUrlTools.physicalCodeSourcePath(null);
            fail("null 代码源应显式拒绝");
        } catch (NullPointerException expected) {
            assertEquals("location", expected.getMessage());
        }
    }

    @Test
    public void physicalCodeSourcePathRejectsUnsupportedProtocol() throws Exception {
        URL location = new URL("https://example.com/ecat.jar");
        try {
            ClassUrlTools.physicalCodeSourcePath(location);
            fail("非 file/jar 代码源应显式拒绝");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("不支持的代码源协议"));
        }
    }
}
