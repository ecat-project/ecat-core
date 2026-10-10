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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * ClassUrlTools.decodeUrlPath 的 path 语义解码契约测试：
 * %XX（中文/空格）解码、字面 "+" 保持加号（path 中空格只编码为 %20，
 * 表单语义的 "+"=空格 会把含加号目录的 jar 路径解错）。
 * 断言形态与两个调用方（ResourceLoader/IntegrationCoordinateHelper）一致：
 * getPath() → 剥 "file:" 前缀 → 作为磁盘路径使用。
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
}
