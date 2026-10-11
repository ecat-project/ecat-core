/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Utils;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Error 族不能穿透 loadJar 的 Exception 回滚边界。 */
public class LoadJarUtilsErrorWrapTest {

    private Path tempDir;
    private URLClassLoader parentLoader;
    private LoadJarUtils loadJarUtils;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("loadjar-error-wrap");
        parentLoader = new URLClassLoader(new URL[0], null);
        loadJarUtils = new LoadJarUtils(null, parentLoader);
    }

    @After
    public void tearDown() throws Exception {
        if (parentLoader != null) {
            parentLoader.close();
        }
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> path.toFile().delete());
    }

    @Test
    public void classFormatErrorIsWrappedAsRuntimeException() throws Exception {
        Path jar = tempDir.resolve("broken-class-1.0.0.jar");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            out.putNextEntry(new JarEntry("broken/Linkage.class"));
            out.write("not-java-class-file".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> loadJarUtils.loadJar(jar.toAbsolutePath().toString(), null,
                loadJarUtils.getEcatCoreClassLoader(), "broken.Linkage"));

        assertTrue("消息必须定位 jar: " + error.getMessage(),
            error.getMessage().contains(jar.toAbsolutePath().toString()));
        assertTrue("LinkageError 必须保留为 cause: " + error.getCause(),
            error.getCause() instanceof LinkageError);
        assertSame(ClassFormatError.class, error.getCause().getClass());
    }
}
