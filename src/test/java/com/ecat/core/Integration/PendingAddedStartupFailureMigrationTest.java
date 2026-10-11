package com.ecat.core.Integration;

import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport;
import com.ecat.core.State.StateManager;
import com.ecat.core.Version.CoreVersionMismatchException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 启动装载失败后 PENDING_ADDED 出边契约：失败即回 STOPPED 并解锁，成功路径仍翻 RUNNING。
 */
public class PendingAddedStartupFailureMigrationTest {

    private static final String COORDINATE = "com.ecat:fuyang";
    private static final String MISSING_DEPENDENCY = "com.ecat:dependency";

    private File testDir;
    private String originalUserHome;
    private String originalConfigPath;
    private IntegrationManager manager;

    @Before
    public void setUp() throws Exception {
        testDir = new File("target", ".ecat-pending-added-migration");
        deleteRecursively(testDir);
        assertTrue(testDir.mkdirs());

        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", testDir.getAbsolutePath());
        Field configPathField = IntegrationManager.class.getDeclaredField("INTEGRATIONS_CONFIG_PATH");
        configPathField.setAccessible(true);
        originalConfigPath = (String) configPathField.get(null);
        configPathField.set(null, new File(testDir, "integrations.yml").getPath());

        manager = new IntegrationManager(mock(EcatCore.class), new IntegrationRegistry(),
                mock(StateManager.class));
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
    public void depMissingMigrationUnlocksStatusAndRemoveSucceeds() throws Exception {
        writeConfig();
        StartupLoadTracker tracker = new StartupLoadTracker();
        tracker.recordFailure(COORDINATE, StartupLoadTracker.STAGE_DEP_MISSING,
                "缺少依赖 [" + MISSING_DEPENDENCY + "]");

        manager.migrateFailedPendingAddedIntegrations(tracker);

        Map<String, Object> node = readYmlNode();
        assertEquals("STOPPED", node.get("state"));
        assertEquals(false, node.get("enabled"));
        assertEquals("1.0.0", node.get("version"));
        assertEquals("type=dep-missing; 缺少依赖 [" + MISSING_DEPENDENCY + "]",
                node.get("failureReason"));

        IntegrationStatus status = manager.getIntegrationStatus(COORDINATE);
        assertEquals(IntegrationState.STOPPED, status.getState());
        assertFalse(status.isEnabled());
        assertFalse(status.isLocked());
        assertTrue(status.isCanRemove());
        assertTrue(status.isCanEnable());
        assertTrue("API message 应携带缺失依赖明细: " + status.getMessage(),
                status.getMessage().contains("type=dep-missing")
                        && status.getMessage().contains(MISSING_DEPENDENCY));

        IntegrationStatus removed = manager.removeIntegration(COORDINATE);
        assertEquals(IntegrationState.PENDING_REMOVED, removed.getState());
        Map<String, Object> removedNode = readYmlNode();
        assertEquals(true, removedNode.get("_deleted"));
        assertEquals("PENDING_REMOVED", removedNode.get("state"));
    }

    @Test
    public void versionGateFailureKeepsStructuredConstraintDetail() throws Exception {
        writeConfig();
        StartupLoadTracker tracker = new StartupLoadTracker();
        CoreVersionMismatchException failure = new CoreVersionMismatchException(
                COORDINATE, ">=5.0.0", "4.0.0", "requires_core >=5.0.0 not satisfied");
        tracker.recordFailure(COORDINATE, StartupLoadTracker.STAGE_REQUIRES_CORE,
                "requires_core=" + failure.getRequiresCore()
                        + ", actual_core=" + failure.getActualCoreVersion()
                        + ", reason=" + failure.getMessage());

        manager.migrateFailedPendingAddedIntegrations(tracker);

        Map<String, Object> node = readYmlNode();
        assertEquals("STOPPED", node.get("state"));
        assertEquals(false, node.get("enabled"));
        String reason = (String) node.get("failureReason");
        assertTrue(reason, reason.contains("type=requires-core")
                && reason.contains("requires_core=>=5.0.0")
                && reason.contains("actual_core=4.0.0"));
    }

    @Test
    public void successfulLoadStillPromotesPendingAddedToRunning() throws Exception {
        writeConfig();
        IntegrationRegistry registry = new IntegrationRegistry();
        registry.register(COORDINATE, new LoadedIntegration(integrationInfo()));
        setRegistry(registry);

        invokeUpdateLoadedIntegrationsState();

        Map<String, Object> node = readYmlNode();
        assertEquals("RUNNING", node.get("state"));
        assertEquals(true, node.get("enabled"));
        assertNull("成功路径必须清理旧失败原因", node.get("failureReason"));
        assertEquals(IntegrationState.RUNNING, manager.getIntegrationStatus(COORDINATE).getState());
    }

    @Test
    public void loadIntegrationsMigratesPendingAddedWhenDependencyMissing() throws Exception {
        buildDependencyMissingFixtureJar();
        writeConfig();

        manager.loadIntegrations();

        Map<String, Object> node = readYmlNode();
        assertEquals("STOPPED", node.get("state"));
        assertEquals(false, node.get("enabled"));
        String reason = (String) node.get("failureReason");
        assertTrue(reason, reason.contains("type=dep-missing")
                && reason.contains(MISSING_DEPENDENCY));
        assertTrue(manager.getIntegrationStatus(COORDINATE).isCanRemove());
    }

    private void setRegistry(IntegrationRegistry registry) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField("integrationRegistry");
        field.setAccessible(true);
        field.set(manager, registry);
    }

    private void invokeUpdateLoadedIntegrationsState() throws Exception {
        Method method = IntegrationManager.class.getDeclaredMethod("updateLoadedIntegrationsState");
        method.setAccessible(true);
        method.invoke(manager);
    }

    private static IntegrationInfo integrationInfo() {
        return new IntegrationInfo("fuyang", false, new ArrayList<>(), true, "Stub",
                "com.ecat", "1.0.0", new WebPlatformSupport(), "^1.0.0");
    }

    private static final class LoadedIntegration extends GateStubIntegration {
        private LoadedIntegration(IntegrationInfo info) {
            loadOption = new IntegrationLoadOption(null);
            loadOption.setIntegrationInfo(info);
        }
    }

    private void buildDependencyMissingFixtureJar() throws IOException {
        String config = "requires_core: \"^1.0.0\"\n"
                + "dependencies:\n"
                + "  - groupId: com.ecat\n"
                + "    artifactId: dependency\n";
        File jarFile = new File(testDir,
                ".m2/repository/com/ecat/fuyang/1.0.0/fuyang-1.0.0.jar");
        jarFile.getParentFile().mkdirs();
        try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(jarFile))) {
            jarOut.putNextEntry(new JarEntry("ecat-config.yml"));
            jarOut.write(config.getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
            copyEntryClass(jarOut);
        }
    }

    private static void copyEntryClass(JarOutputStream jarOut) throws IOException {
        Class<?> stub = GateStubIntegration.class;
        String entryName = stub.getName().replace('.', '/') + ".class";
        try (InputStream input = stub.getResourceAsStream("/" + entryName)) {
            if (input == null) {
                throw new IOException("GateStubIntegration.class 不在 test-classes");
            }
            jarOut.putNextEntry(new JarEntry(entryName));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                jarOut.write(buffer, 0, read);
            }
            jarOut.closeEntry();
        }
    }

    private void writeConfig() throws IOException {
        String yml = "version: \"4.0\"\n"
                + "integrations:\n"
                + "  " + COORDINATE + ":\n"
                + "    groupId: com.ecat\n"
                + "    artifactId: fuyang\n"
                + "    version: \"1.0.0\"\n"
                + "    enabled: true\n"
                + "    state: PENDING_ADDED\n";
        Files.write(new File(testDir, "integrations.yml").toPath(),
                yml.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYmlNode() throws IOException {
        try (InputStream input = new FileInputStream(new File(testDir, "integrations.yml"))) {
            Map<String, Object> config = new Yaml().load(input);
            return (Map<String, Object>) ((Map<String, Object>) config.get("integrations"))
                    .get(COORDINATE);
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
        assertTrue("测试文件删除失败: " + file, file.delete());
    }
}
