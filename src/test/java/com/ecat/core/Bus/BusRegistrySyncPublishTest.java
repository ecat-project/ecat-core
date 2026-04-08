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

package com.ecat.core.Bus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Tests for BusRegistry.publishSync() — synchronous event dispatch.
 * Validates that publishSync runs callbacks on the caller's thread,
 * that publish() remains async, and that wildcard topic matching works.
 */
public class BusRegistrySyncPublishTest {

    private BusRegistry registry;

    @Before
    public void setUp() {
        registry = new BusRegistry();
    }

    @After
    public void tearDown() {
        registry.shutdown();
    }

    /**
     * publishSync must invoke the subscriber on the same thread that called publishSync,
     * NOT on the executorService thread.
     */
    @Test
    public void testSyncPublishCallsSubscriberInSameThread() {
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();

        registry.subscribe("device.status", (topic, data) -> {
            callbackThread.set(Thread.currentThread());
        });

        registry.publishSync("device.status", "online");

        assertNotNull("Subscriber should have been called", callbackThread.get());
        assertSame("publishSync should invoke subscriber on caller thread",
                callerThread, callbackThread.get());
    }

    /**
     * publish must still dispatch asynchronously via executorService.
     * The callback should run on a different thread.
     */
    @Test
    public void testAsyncPublishStillUsesExecutor() throws InterruptedException {
        Thread callerThread = Thread.currentThread();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Thread> callbackThread = new AtomicReference<>();

        registry.subscribe("device.data", (topic, data) -> {
            callbackThread.set(Thread.currentThread());
            latch.countDown();
        });

        registry.publish("device.data", "some-payload");

        assertTrue("Async publish should complete within 5 seconds",
                latch.await(5, TimeUnit.SECONDS));

        assertNotNull("Subscriber should have been called", callbackThread.get());
        assertNotSame("publish should invoke subscriber on a different thread (async)",
                callerThread, callbackThread.get());
    }

    /**
     * publishSync must support wildcard topic matching, just like publish().
     * Subscribing to "device.*" should match "device.status".
     */
    @Test
    public void testSyncPublishMatchesWildcardTopics() {
        AtomicReference<String> receivedTopic = new AtomicReference<>();
        AtomicReference<Object> receivedData = new AtomicReference<>();

        registry.subscribe("device.*", (topic, data) -> {
            receivedTopic.set(topic);
            receivedData.set(data);
        });

        registry.publishSync("device.status", 42);

        assertEquals("Wildcard should match 'device.status'",
                "device.status", receivedTopic.get());
        assertEquals("Event data should be passed through",
                42, receivedData.get());
    }
}
