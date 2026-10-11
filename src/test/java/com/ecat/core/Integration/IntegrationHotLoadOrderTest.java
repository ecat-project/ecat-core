/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport;
import com.ecat.core.State.StateManager;
import com.ecat.core.Utils.IntegrationConfigParseException;
import com.ecat.core.Utils.JarDependencyLoader;
import com.ecat.core.Utils.LoadOrderResult;

import com.ecat.core.Integration.DependencyInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** add 热加载必须与启动共用同一全图装载序，且候选依赖悬空时显式失败。 */
public class IntegrationHotLoadOrderTest {

    private File testDir;
    private IntegrationRegistry integrationRegistry;
    private String originalUserHome;
    private String originalConfigPath;

    @Before
    public void setUp() throws Exception {
        testDir = new File("target", ".ecat-hot-load-order");
        deleteRecursively(testDir);
        assertTrue(testDir.mkdirs() || testDir.exists());

        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", testDir.getAbsolutePath());

        Field configPathField = IntegrationManager.class.getDeclaredField("INTEGRATIONS_CONFIG_PATH");
        configPathField.setAccessible(true);
        originalConfigPath = (String) configPathField.get(null);
        writeConfig("com.ecat:hot-root", "com.ecat:hot-child");
    }

    @After
    public void tearDown() throws Exception {
        System.setProperty("user.home", originalUserHome);
        Field configPathField = IntegrationManager.class.getDeclaredField("INTEGRATIONS_CONFIG_PATH");
        configPathField.setAccessible(true);
        configPathField.set(null, originalConfigPath);
        deleteRecursively(testDir);
    }

    @Test
    public void addDerivationMatchesBootForSameGraphAndUsesLiveSnapshot() throws Exception {
        buildFixtureJar("hot-root", null);
        buildFixtureJar("hot-child", "com.ecat:hot-root");

        IntegrationManager manager = newManager();
        LoadOrderResult bootResult = manager.deriveLoadOrder(null, new StartupLoadTracker());
        assertEquals(java.util.Arrays.asList("com.ecat:hot-root", "com.ecat:hot-child"),
            coordinates(bootResult));

        IntegrationInfo liveRootInfo = bootResult.getLoadOrder().get(0);
        integrationRegistry.register(liveRootInfo.getCoordinate(), new LoadedIntegration(liveRootInfo));

        // 启动基线含 child；add 现场只落 root，child 必须经候选参数补回同一闭包。
        writeConfig("com.ecat:hot-root", null);
        IntegrationInfo candidate = candidateInfo("hot-child", "com.ecat:hot-root");
        LoadOrderResult addResult = manager.deriveLoadOrder(candidate, null);

        assertEquals(bootResult.getLoadOrder().size(), addResult.getLoadOrder().size());
        assertEquals(coordinates(bootResult), coordinates(addResult));
        assertSame("已装载成员必须取 registry 装载快照", liveRootInfo,
            addResult.getLoadOrder().get(0));
    }

    @Test
    public void candidateWithMissingDependencyFailsLoud() throws Exception {
        buildFixtureJar("hot-orphan", "com.ecat:hot-missing");
        writeConfig(null, null);

        IntegrationManager manager = newManager();
        IntegrationInfo candidate = candidateInfo("hot-orphan", "com.ecat:hot-missing");
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> manager.deriveLoadOrder(candidate, null));

        assertTrue("失败原因必须指向悬空依赖: " + error.getMessage(),
            error.getMessage().contains("com.ecat:hot-missing"));
    }

    private IntegrationManager newManager() {
        integrationRegistry = new IntegrationRegistry();
        return new IntegrationManager(mock(EcatCore.class), integrationRegistry,
            mock(StateManager.class));
    }

    private static final class LoadedIntegration extends GateStubIntegration {
        private LoadedIntegration(IntegrationInfo info) {
            loadOption = new IntegrationLoadOption(null);
            loadOption.setIntegrationInfo(info);
        }
    }

    private IntegrationInfo candidateInfo(String artifactId, String dependencyCoordinate)
            throws IOException, IntegrationConfigParseException {
        IntegrationInfo info = JarDependencyLoader.readPartialIntegrationInfoFromJar(
            fixtureFile(artifactId));
        info.setGroupId("com.ecat");
        info.setArtifactId(artifactId);
        info.setVersion("1.0.0");
        info.setClassName(GateStubIntegration.class.getName());
        info.setEnabled(true);
        if (dependencyCoordinate != null) {
            String[] parts = dependencyCoordinate.split(":");
            List<DependencyInfo> dependencies = new ArrayList<>();
            dependencies.add(new DependencyInfo(parts[0], parts[1], "*"));
            info.setDependencyInfoList(dependencies);
        }
        return info;
    }

    private void buildFixtureJar(String artifactId, String dependencyCoordinate) throws IOException {
        StringBuilder yml = new StringBuilder("requires_core: \"^1.0.0\"\n");
        if (dependencyCoordinate != null) {
            String[] parts = dependencyCoordinate.split(":");
            yml.append("dependencies:\n")
                .append("  - groupId: ").append(parts[0]).append("\n")
                .append("    artifactId: ").append(parts[1]).append("\n");
        }
        File jarFile = fixtureFile(artifactId);
        jarFile.getParentFile().mkdirs();
        try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(jarFile))) {
            jarOut.putNextEntry(new JarEntry("ecat-config.yml"));
            jarOut.write(yml.toString().getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
            copyEntryClass(jarOut);
        }
    }

    private File fixtureFile(String artifactId) {
        return new File(testDir, ".m2/repository/com/ecat/" + artifactId
            + "/1.0.0/" + artifactId + "-1.0.0.jar");
    }

    private void writeConfig(String rootCoordinate, String childCoordinate) throws IOException {
        StringBuilder yml = new StringBuilder("version: \"4.0\"\nintegrations:");
        if (rootCoordinate == null && childCoordinate == null) {
            yml.append(" {}\n");
        } else {
            yml.append('\n');
        }
        appendConfigEntry(yml, rootCoordinate);
        appendConfigEntry(yml, childCoordinate);
        writeString(new File(testDir, "integrations.yml"), yml.toString());
        Field configPathField;
        try {
            configPathField = IntegrationManager.class.getDeclaredField("INTEGRATIONS_CONFIG_PATH");
            configPathField.setAccessible(true);
            configPathField.set(null, new File(testDir, "integrations.yml").getPath());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void appendConfigEntry(StringBuilder yml, String coordinate) {
        if (coordinate == null) {
            return;
        }
        String artifactId = coordinate.substring(coordinate.indexOf(':') + 1);
        yml.append("  ").append(coordinate).append(":\n")
            .append("    groupId: com.ecat\n")
            .append("    artifactId: ").append(artifactId).append('\n')
            .append("    version: \"1.0.0\"\n")
            .append("    enabled: true\n");
    }

    private static List<String> coordinates(LoadOrderResult result) {
        List<String> values = new ArrayList<>();
        for (IntegrationInfo info : result.getLoadOrder()) {
            values.add(info.getCoordinate());
        }
        return values;
    }

    private static void copyEntryClass(JarOutputStream jarOut) throws IOException {
        Class<?> stub = GateStubIntegration.class;
        String entryName = stub.getName().replace('.', '/') + ".class";
        try (InputStream in = stub.getResourceAsStream("/" + entryName)) {
            if (in == null) {
                throw new IOException("GateStubIntegration.class 不在 test-classes");
            }
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
        file.getParentFile().mkdirs();
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
