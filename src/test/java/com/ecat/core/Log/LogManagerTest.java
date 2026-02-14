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

package com.ecat.core.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * LogManager 单元测试
 * 
 * @author coffee
 */
public class LogManagerTest {

    private LogManager logManager;

    @Before
    public void setUp() {
        logManager = LogManager.getInstance();
        logManager.clearAllForTest();
    }

    @After
    public void tearDown() {
        logManager.clearAllForTest();
    }

    @Test
    public void testGetInstance() {
        LogManager instance1 = LogManager.getInstance();
        LogManager instance2 = LogManager.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    public void testRegisterIntegration() {
        logManager.registerIntegration("test-integration", null);

        assertTrue(logManager.hasBuffer("test-integration"));
        assertNotNull(logManager.getBuffer("test-integration"));
    }

    @Test
    public void testRegisterIntegrationEmptyCoordinate() {
        // 空坐标不应该创建缓冲区
        logManager.registerIntegration("", null);
        assertFalse(logManager.hasBuffer(""));
    }

    @Test
    public void testUnregisterIntegration() {
        logManager.registerIntegration("to-remove", null);
        assertTrue(logManager.hasBuffer("to-remove"));

        logManager.unregisterIntegration("to-remove");
        assertFalse(logManager.hasBuffer("to-remove"));
    }

    @Test
    public void testBroadcast() {
        logManager.registerIntegration("broadcast-test", null);

        LogEntry entry = new LogEntry(System.currentTimeMillis(), "broadcast-test",
                "INFO", "TestLogger", "main", "Test message");
        logManager.broadcast(entry);

        List<LogEntry> history = logManager.getHistory("broadcast-test", 10);
        assertEquals(1, history.size());
        assertEquals("Test message", history.get(0).getMessage());
    }

    @Test
    public void testBroadcastToUnregisteredCoordinate() {
        LogEntry entry = new LogEntry(System.currentTimeMillis(), "not-registered",
                "INFO", "TestLogger", "main", "Test");
        logManager.broadcast(entry);

        List<LogEntry> history = logManager.getHistory("not-registered", 10);
        assertTrue(history.isEmpty());
    }

    @Test
    public void testBroadcastNullEntry() {
        logManager.registerIntegration("test", null);
        logManager.broadcast(null);

        assertEquals(0, logManager.getTotalLogCount());
    }

    @Test
    public void testGetHistory() {
        logManager.registerIntegration("history-test", null);

        for (int i = 1; i <= 5; i++) {
            LogEntry entry = new LogEntry(i, "history-test", "DEBUG", "Logger", "thread", "msg" + i);
            logManager.broadcast(entry);
        }

        List<LogEntry> history = logManager.getHistory("history-test", 3);
        assertEquals(3, history.size());
        assertEquals("msg3", history.get(0).getMessage());
        assertEquals("msg5", history.get(2).getMessage());
    }

    @Test
    public void testGetHistoryNonExistent() {
        List<LogEntry> history = logManager.getHistory("non-existent", 10);
        assertTrue(history.isEmpty());
    }

    @Test
    public void testGetRegisteredCoordinates() {
        logManager.registerIntegration("coord1", null);
        logManager.registerIntegration("coord2", null);

        Set<String> coords = logManager.getRegisteredCoordinates();
        assertEquals(2, coords.size());
        assertTrue(coords.contains("coord1"));
        assertTrue(coords.contains("coord2"));
    }

    @Test
    public void testGetTotalLogCount() {
        logManager.registerIntegration("count-test", null);

        for (int i = 0; i < 5; i++) {
            logManager.broadcast(new LogEntry(i, "count-test", "INFO", "L", "t", "m"));
        }

        assertEquals(5, logManager.getTotalLogCount());
    }

    @Test
    public void testGetBufferSize() {
        assertEquals(1000, logManager.getBufferSize());
    }

    @Test
    public void testClearAllForTest() {
        logManager.registerIntegration("test1", null);
        logManager.registerIntegration("test2", null);
        logManager.broadcast(new LogEntry(1, "test1", "INFO", "L", "t", "m"));

        logManager.clearAllForTest();

        assertEquals(0, logManager.getRegisteredCoordinates().size());
        assertEquals(0, logManager.getTotalLogCount());
    }
}
