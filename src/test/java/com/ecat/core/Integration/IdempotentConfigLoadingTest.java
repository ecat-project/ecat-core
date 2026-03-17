package com.ecat.core.Integration;

import com.ecat.core.ConfigEntry.ConfigEntry;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests demonstrating two real-world idempotent config loading patterns.
 *
 * <p>When the framework loads persisted ConfigEntry instances, the loading order
 * between "integration config" and "device config" is unpredictable. Integrations
 * must handle both orders gracefully. These tests verify two common patterns:</p>
 *
 * <h3>Pattern 1: Pending Cache Mode</h3>
 * Device configs arriving before the integration config are cached in a pending list.
 * When the integration config finally arrives (or the hook fires), pending devices
 * are activated in bulk.
 *
 * <h3>Pattern 2: Flag + Periodic Check Mode</h3>
 * Devices are created immediately, but carry a condition-checker Runnable. A
 * periodic scheduler invokes the checker; the device can only produce values when
 * the integration-level flag has been set.
 *
 * @author coffee
 */
public class IdempotentConfigLoadingTest {

    // ==================== Stub Implementations ====================

    /**
     * Simulates an integration that caches device configs until the integration
     * config (containing a cloud token) arrives.
     *
     * <p>Usage: call {@link #createEntry(ConfigEntry)} for each persisted entry
     * (in any order), then call {@link #onAllExistEntriesLoaded(List)} after all
     * entries have been processed.</p>
     */
    private static class PendingCacheIntegration extends IntegrationBase {

        private String cloudToken;
        private final List<String> pendingDevices = new ArrayList<String>();
        private final List<String> activatedDevices = new ArrayList<String>();

        @Override
        public void onInit() { }

        @Override
        public void onStart() { }

        @Override
        public void onPause() { }

        @Override
        public ConfigEntry createEntry(ConfigEntry entry) {
            String type = (String) entry.getData().get("type");
            if ("integration".equals(type)) {
                // Integration config arrives -- extract token and activate pending
                this.cloudToken = (String) entry.getData().get("token");
                tryActivatePending();
            } else {
                // Device config arrives
                if (this.cloudToken == null) {
                    // No token yet -- cache for later
                    pendingDevices.add(entry.getEntryId());
                } else {
                    // Token already available -- activate immediately
                    activatedDevices.add(entry.getEntryId());
                }
            }
            return entry;
        }

        @Override
        public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
            // Final check: activate any devices still pending
            tryActivatePending();

            if (cloudToken == null) {
                // Integration config missing -- log warning but still call super
                // so that ready=true and the integration can operate in degraded mode
                System.out.println("[PendingCache] WARN: integration config missing, "
                        + pendingDevices.size() + " devices remain pending");
            }

            super.onAllExistEntriesLoaded(entries);
        }

        private void tryActivatePending() {
            if (cloudToken != null && !pendingDevices.isEmpty()) {
                activatedDevices.addAll(pendingDevices);
                pendingDevices.clear();
            }
        }
    }

    /**
     * Simulates an integration where devices are created eagerly but can only
     * run when the integration-level config has been loaded.
     *
     * <p>Each device carries a {@link Runnable} condition-checker. The periodic
     * scheduler (not simulated here) would invoke the checker; the device
     * returns true from {@code canRun()} only when the integration flag is set.</p>
     */
    private static class FlagCheckIntegration extends IntegrationBase {

        private volatile boolean integrationReady = false;
        private final List<StubDevice> devices = new ArrayList<StubDevice>();

        @Override
        public void onInit() { }

        @Override
        public void onStart() { }

        @Override
        public void onPause() { }

        @Override
        public ConfigEntry createEntry(ConfigEntry entry) {
            String type = (String) entry.getData().get("type");
            if ("integration".equals(type)) {
                this.integrationReady = true;
            } else {
                // Create device with a condition checker that tests integrationReady
                final FlagCheckIntegration self = this;
                StubDevice device = new StubDevice(new Runnable() {
                    @Override
                    public void run() {
                        if (!self.integrationReady) {
                            throw new IllegalStateException(
                                "Integration config not loaded yet");
                        }
                    }
                });
                devices.add(device);
            }
            return entry;
        }

        @Override
        public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
            if (!integrationReady) {
                // No integration config -- log warning
                System.out.println("[FlagCheck] WARN: integration config missing, "
                        + devices.size() + " devices cannot run until config is provided");
            }
            super.onAllExistEntriesLoaded(entries);
        }
    }

    /**
     * A minimal device stub that wraps a condition-checker Runnable.
     *
     * <p>{@link #canRun()} invokes the checker. If it throws, the device
     * cannot run. If it returns normally, the device is considered operational.</p>
     */
    private static class StubDevice {
        private final Runnable conditionChecker;

        StubDevice(Runnable conditionChecker) {
            this.conditionChecker = conditionChecker;
        }

        /**
         * Try to run the condition checker.
         *
         * @return true if the checker completed without exception
         */
        boolean canRun() {
            try {
                conditionChecker.run();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // ==================== Helpers ====================

    private static final String COORDINATE = "com.ecat.test:stub";

    private ConfigEntry createEntry(String entryId, String coordinate, Map<String, Object> data) {
        return new ConfigEntry.Builder()
                .entryId(entryId)
                .coordinate(coordinate)
                .uniqueId("unique-" + entryId)
                .title("Test " + entryId)
                .data(data)
                .createTime(ZonedDateTime.now())
                .updateTime(ZonedDateTime.now())
                .build();
    }

    private Map<String, Object> buildData(String type, String id) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("type", type);
        data.put("id", id);
        return data;
    }

    // ==================== Group 1: Pending Cache Mode ====================

    /**
     * Pattern 1, Scenario A: Device config arrives first, integration config arrives later.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Device config loaded -- no token yet, cached in pendingDevices</li>
     *   <li>Integration config loaded -- token set, pendingDevices activated</li>
     * </ol>
     * <p>Expected: device in activatedDevices, pendingDevices empty.</p>
     */
    @Test
    public void testPendingCacheMode_DeviceConfigArrivesFirst() {
        PendingCacheIntegration integration = new PendingCacheIntegration();

        // Step 1: device config arrives first
        Map<String, Object> deviceData = buildData("device", "device-001");
        ConfigEntry deviceEntry = createEntry("entry-dev-1", COORDINATE, deviceData);
        integration.createEntry(deviceEntry);

        // Device should be pending (no token yet)
        assertEquals("Device should be in pending list", 1, integration.pendingDevices.size());
        assertTrue("Pending should contain the device entry",
                integration.pendingDevices.contains("entry-dev-1"));
        assertEquals("No activated devices yet", 0, integration.activatedDevices.size());

        // Step 2: integration config arrives
        Map<String, Object> integrationData = new HashMap<String, Object>();
        integrationData.put("type", "integration");
        integrationData.put("token", "cloud-token-abc123");
        ConfigEntry integrationEntry = createEntry("entry-int-1", COORDINATE, integrationData);
        integration.createEntry(integrationEntry);

        // Device should now be activated, pending cleared
        assertEquals("Pending should be empty after activation", 0, integration.pendingDevices.size());
        assertEquals("Device should be activated", 1, integration.activatedDevices.size());
        assertTrue("Activated should contain the device entry",
                integration.activatedDevices.contains("entry-dev-1"));
    }

    /**
     * Pattern 1, Scenario B: Integration config arrives first, device config arrives later.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Integration config loaded -- token set immediately</li>
     *   <li>Device config loaded -- token already available, activated directly</li>
     * </ol>
     * <p>Expected: device activated directly, never enters pendingDevices.</p>
     */
    @Test
    public void testPendingCacheMode_IntegrationConfigArrivesFirst() {
        PendingCacheIntegration integration = new PendingCacheIntegration();

        // Step 1: integration config arrives first
        Map<String, Object> integrationData = new HashMap<String, Object>();
        integrationData.put("type", "integration");
        integrationData.put("token", "cloud-token-xyz789");
        ConfigEntry integrationEntry = createEntry("entry-int-1", COORDINATE, integrationData);
        integration.createEntry(integrationEntry);

        assertEquals("No pending devices yet", 0, integration.pendingDevices.size());
        assertEquals("No activated devices yet", 0, integration.activatedDevices.size());

        // Step 2: device config arrives -- token already available
        Map<String, Object> deviceData = buildData("device", "device-002");
        ConfigEntry deviceEntry = createEntry("entry-dev-2", COORDINATE, deviceData);
        integration.createEntry(deviceEntry);

        // Device should be activated directly, never pending
        assertEquals("Pending should remain empty", 0, integration.pendingDevices.size());
        assertEquals("Device should be activated directly", 1, integration.activatedDevices.size());
        assertTrue("Activated should contain the device entry",
                integration.activatedDevices.contains("entry-dev-2"));
    }

    /**
     * Pattern 1, Scenario C: Only device config exists, hook fires as final check.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Device config loaded -- no token, cached in pendingDevices</li>
     *   <li>Hook onAllExistEntriesLoaded fires -- no integration config available</li>
     * </ol>
     * <p>Expected: ready=true (hook called super), but devices remain in pendingDevices
     * because the token never arrived. The integration operates in degraded mode.</p>
     */
    @Test
    public void testPendingCacheMode_OnAllExistEntriesLoadedFinalCheck() {
        PendingCacheIntegration integration = new PendingCacheIntegration();

        // Step 1: only device config exists
        Map<String, Object> deviceData = buildData("device", "device-003");
        ConfigEntry deviceEntry = createEntry("entry-dev-3", COORDINATE, deviceData);
        integration.createEntry(deviceEntry);

        assertEquals("Device should be pending", 1, integration.pendingDevices.size());
        assertFalse("Integration should not be ready yet", integration.isReady());

        // Step 2: hook fires -- no integration config in the list
        List<ConfigEntry> allEntries = new ArrayList<ConfigEntry>();
        allEntries.add(deviceEntry);
        integration.onAllExistEntriesLoaded(allEntries);

        // ready should be true (super was called)
        assertTrue("ready should be true after hook (super called)",
                integration.isReady());

        // But devices remain pending -- no token available
        assertEquals("Devices should still be pending (no token)",
                1, integration.pendingDevices.size());
        assertEquals("No devices activated", 0, integration.activatedDevices.size());
    }

    // ==================== Group 2: Flag + Periodic Check Mode ====================

    /**
     * Pattern 2, Scenario A: Device config arrives first, integration config arrives later.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Device config loaded -- device created, but cannot run (flag not set)</li>
     *   <li>Integration config loaded -- flag set to true</li>
     *   <li>Periodic check: device.canRun() now returns true</li>
     * </ol>
     */
    @Test
    public void testPeriodicCheckMode_DeviceConfigArrivesFirst() {
        FlagCheckIntegration integration = new FlagCheckIntegration();

        // Step 1: device config arrives first
        Map<String, Object> deviceData = buildData("device", "device-101");
        ConfigEntry deviceEntry = createEntry("entry-dev-101", COORDINATE, deviceData);
        integration.createEntry(deviceEntry);

        assertEquals("One device created", 1, integration.devices.size());
        assertFalse("Device should not be able to run (flag not set)",
                integration.devices.get(0).canRun());

        // Step 2: integration config arrives -- sets flag
        Map<String, Object> integrationData = new HashMap<String, Object>();
        integrationData.put("type", "integration");
        ConfigEntry integrationEntry = createEntry("entry-int-101", COORDINATE, integrationData);
        integration.createEntry(integrationEntry);

        assertTrue("integrationReady flag should be set", integration.integrationReady);

        // Step 3: periodic check -- device can now run
        assertTrue("Device should be able to run after flag is set",
                integration.devices.get(0).canRun());
    }

    /**
     * Pattern 2, Scenario B: Only device config exists, hook fires without integration config.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Device config loaded -- device created, cannot run</li>
     *   <li>Hook onAllExistEntriesLoaded fires -- no integration config, warning logged</li>
     *   <li>ready=true (super called), but device still cannot run</li>
     * </ol>
     */
    @Test
    public void testPeriodicCheckMode_OnAllExistEntriesLoadedWithMissingConfig() {
        FlagCheckIntegration integration = new FlagCheckIntegration();

        // Step 1: only device config loaded
        Map<String, Object> deviceData = buildData("device", "device-102");
        ConfigEntry deviceEntry = createEntry("entry-dev-102", COORDINATE, deviceData);
        integration.createEntry(deviceEntry);

        assertFalse("Device should not be able to run", integration.devices.get(0).canRun());
        assertFalse("Integration flag should not be set", integration.integrationReady);

        // Step 2: hook fires with only device entries
        List<ConfigEntry> allEntries = new ArrayList<ConfigEntry>();
        allEntries.add(deviceEntry);
        integration.onAllExistEntriesLoaded(allEntries);

        // ready should be true (super called)
        assertTrue("ready should be true after hook",
                integration.isReady());

        // But integration flag is still false -- device cannot run
        assertFalse("integrationReady flag should remain false (no integration config)",
                integration.integrationReady);
        assertFalse("Device should still not be able to run after hook",
                integration.devices.get(0).canRun());
    }

    // ==================== Group 3: ready lifecycle ====================

    /**
     * Verify the full lifecycle of the {@code ready} flag:
     * <ol>
     *   <li>false immediately after construction</li>
     *   <li>false after createEntry() calls (hook not yet fired)</li>
     *   <li>true after onAllExistEntriesLoaded() hook fires</li>
     * </ol>
     */
    @Test
    public void testReadyLifecycle_FullStartup() {
        PendingCacheIntegration integration = new PendingCacheIntegration();

        // Phase 1: immediately after construction
        assertFalse("ready should be false immediately after construction",
                integration.isReady());

        // Phase 2: after createEntry calls (simulating framework loading persisted entries)
        Map<String, Object> deviceData = buildData("device", "device-201");
        ConfigEntry deviceEntry = createEntry("entry-dev-201", COORDINATE, deviceData);
        integration.createEntry(deviceEntry);

        Map<String, Object> integrationData = new HashMap<String, Object>();
        integrationData.put("type", "integration");
        integrationData.put("token", "tok-201");
        ConfigEntry integrationEntry = createEntry("entry-int-201", COORDINATE, integrationData);
        integration.createEntry(integrationEntry);

        assertFalse("ready should still be false after createEntry (hook not yet fired)",
                integration.isReady());

        // Phase 3: hook fires -- ready becomes true
        List<ConfigEntry> allEntries = new ArrayList<ConfigEntry>();
        allEntries.add(deviceEntry);
        allEntries.add(integrationEntry);
        integration.onAllExistEntriesLoaded(allEntries);

        assertTrue("ready should be true after onAllExistEntriesLoaded",
                integration.isReady());

        // Verify device was properly activated (side effect of correct ordering)
        assertEquals("No pending devices remain", 0, integration.pendingDevices.size());
        assertEquals("Device should be activated", 1, integration.activatedDevices.size());
    }
}
