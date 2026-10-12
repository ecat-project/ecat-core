package com.ecat.core.Integration;

import com.ecat.core.ConfigEntry.ConfigEntry;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Unit tests for IntegrationBase.ready field and onAllExistEntriesLoaded hook.
 *
 * @author coffee
 */
public class OnAllExistEntriesLoadedTest {

    // ==================== Stub ====================

    /** Minimal concrete IntegrationBase for testing default behavior. */
    private static class StubIntegration extends IntegrationBase {
        @Override
        public void onInit() { }
        @Override
        public void onStart() { }
        @Override
        public void onPause() { }
    }

    /** Subclass that overrides onAllExistEntriesLoaded WITHOUT calling super. */
    private static class NoSuperIntegration extends StubIntegration {
        @Override
        public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
            // intentionally does NOT call super -- ready stays false
        }
    }

    /** Subclass that overrides onAllExistEntriesLoaded WITH calling super. */
    private static class WithSuperIntegration extends StubIntegration {
        @Override
        public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
            // custom logic before super
            // then delegate to super which sets ready = true
            super.onAllExistEntriesLoaded(entries);
        }
    }

    /** Subclass that records execution order of custom logic vs super. */
    private static class OrderCheckIntegration extends StubIntegration {
        final List<String> orderLog = new ArrayList<String>();

        @Override
        public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
            orderLog.add("custom-start");
            // custom consistency checks would go here
            orderLog.add("custom-end");
            super.onAllExistEntriesLoaded(entries);
            orderLog.add("after-super");
        }
    }

    // ==================== Helper ====================

    private static ConfigEntry buildTestEntry(String id) {
        return new ConfigEntry.Builder()
                .entryId(id)
                .coordinate("com.ecat:test")
                .uniqueId("unique-" + id)
                .title("Test Entry " + id)
                .enabled(true)
                .createTime(ZonedDateTime.now())
                .updateTime(ZonedDateTime.now())
                .version(1)
                .build();
    }

    // ==================== Tests ====================

    @Test
    public void testDefaultReadyIsFalse() {
        StubIntegration stub = new StubIntegration();
        assertFalse("ready should be false before onAllExistEntriesLoaded", stub.isReady());
    }

    @Test
    public void testDefaultOnAllExistEntriesLoadedSetsReady() {
        StubIntegration stub = new StubIntegration();
        List<ConfigEntry> entries = new ArrayList<ConfigEntry>();
        entries.add(buildTestEntry("e1"));
        entries.add(buildTestEntry("e2"));

        stub.onAllExistEntriesLoaded(entries);

        assertTrue("ready should be true after onAllExistEntriesLoaded", stub.isReady());
    }

    @Test
    public void testOnAllExistEntriesLoadedWithEmptyList() {
        StubIntegration stub = new StubIntegration();

        stub.onAllExistEntriesLoaded(Collections.<ConfigEntry>emptyList());

        assertTrue("ready should be true even with empty entries list", stub.isReady());
    }

    @Test
    public void testReadyVisibilityAcrossThreads() throws Exception {
        final StubIntegration stub = new StubIntegration();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean seenByThread = new AtomicBoolean(false);

        // Writer thread sets ready
        Thread writer = new Thread(new Runnable() {
            @Override
            public void run() {
                stub.onAllExistEntriesLoaded(Collections.<ConfigEntry>emptyList());
                latch.countDown();
            }
        });

        // Reader thread waits then reads
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                seenByThread.set(stub.isReady());
            }
        });

        writer.start();
        reader.start();
        writer.join(5000);
        reader.join(5000);

        assertTrue("volatile ready should be visible across threads", seenByThread.get());
    }

    @Test
    public void testOverrideWithoutSuperKeepsNotReady() {
        NoSuperIntegration integration = new NoSuperIntegration();
        List<ConfigEntry> entries = new ArrayList<ConfigEntry>();
        entries.add(buildTestEntry("e1"));

        integration.onAllExistEntriesLoaded(entries);

        assertFalse("ready should remain false when override does not call super",
                integration.isReady());
    }

    @Test
    public void testOverrideWithSuperSetsReady() {
        WithSuperIntegration integration = new WithSuperIntegration();
        List<ConfigEntry> entries = new ArrayList<ConfigEntry>();
        entries.add(buildTestEntry("e1"));

        integration.onAllExistEntriesLoaded(entries);

        assertTrue("ready should be true when override calls super", integration.isReady());
    }

    @Test
    public void testCustomLogicBeforeSuper() {
        OrderCheckIntegration integration = new OrderCheckIntegration();
        List<ConfigEntry> entries = new ArrayList<ConfigEntry>();
        entries.add(buildTestEntry("e1"));

        integration.onAllExistEntriesLoaded(entries);

        List<String> log = integration.orderLog;
        assertEquals("order log should have 3 entries", 3, log.size());
        assertEquals("custom-start", log.get(0));
        assertEquals("custom-end", log.get(1));
        assertEquals("after-super", log.get(2));
        assertTrue("ready should be true after super call", integration.isReady());
    }
}
