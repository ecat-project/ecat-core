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

import com.ecat.core.Const;
import com.ecat.core.Utils.Mdc.MdcContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import static org.junit.Assert.*;

/**
 * Log 类单元测试
 *
 * <p>验证坐标模式、MDC 上下文、有效坐标解析等核心功能。
 * 参考历史版本 LogTest 修订适配。
 */
public class LogTest {

    private Log log;

    @Before
    public void setUp() {
        log = new Log("TestLogger");
        MDC.clear();
        Log.setCoordinateMode(Log.CoordinateMode.LOG_FIRST);
    }

    @After
    public void tearDown() {
        MDC.clear();
        Log.setCoordinateMode(Log.CoordinateMode.LOG_FIRST);
    }

    // ==================== 坐标模式测试 ====================

    @Test
    public void testCoordinateModeGetterSetter() {
        Log.setCoordinateMode(Log.CoordinateMode.LOG_FIRST);
        assertEquals(Log.CoordinateMode.LOG_FIRST, Log.getCoordinateMode());

        Log.setCoordinateMode(Log.CoordinateMode.MDC_FIRST);
        assertEquals(Log.CoordinateMode.MDC_FIRST, Log.getCoordinateMode());

        Log.setCoordinateMode(Log.CoordinateMode.LOG_ONLY);
        assertEquals(Log.CoordinateMode.LOG_ONLY, Log.getCoordinateMode());

        Log.setCoordinateMode(Log.CoordinateMode.MDC_ONLY);
        assertEquals(Log.CoordinateMode.MDC_ONLY, Log.getCoordinateMode());
    }

    @Test
    public void testSetNullCoordinateModeDoesNotChange() {
        Log.setCoordinateMode(Log.CoordinateMode.MDC_FIRST);
        Log.setCoordinateMode(null);
        assertEquals("null 不应改变当前模式", Log.CoordinateMode.MDC_FIRST, Log.getCoordinateMode());
    }

    // ==================== MDC 上下文测试 ====================

    @Test
    public void testSetIntegrationContext() {
        String testCoordinate = "com.ecat:test-plugin";
        Log.setIntegrationContext(testCoordinate);
        assertEquals(testCoordinate, MdcContext.getCoordinate());
    }

    @Test
    public void testClearIntegrationContext() {
        Log.setIntegrationContext("com.ecat:test-plugin");
        Log.clearIntegrationContext();
        assertNull(MdcContext.getCoordinate());
    }

    @Test
    public void testGetCurrentIntegrationContext() {
        assertNull("初始上下文应为 null", Log.getCurrentIntegrationContext());

        Log.setIntegrationContext("com.ecat:test-plugin");
        assertEquals("com.ecat:test-plugin", Log.getCurrentIntegrationContext());

        Log.clearIntegrationContext();
        assertNull(Log.getCurrentIntegrationContext());
    }

    // ==================== 构造函数测试 ====================

    @Test
    public void testConstructorWithNameOnly() {
        Log logByName = new Log("MyLogger");
        assertNull("Log(String) 的 coordinate 应为 null", logByName.getCoordinate());
        assertNotNull("getName() 不应为 null", logByName.getName());
    }

    @Test
    public void testConstructorWithClass() {
        Log logByClass = new Log(LogTest.class);
        assertNotNull("Log(Class) 的 coordinate 不应为 null", logByClass.getCoordinate());
        // 在 IDE 环境下可能检测到坐标，否则降级为 CORE_COORDINATE
        String coord = logByClass.getCoordinate();
        assertTrue("坐标应为有效值", coord.contains(":") || Const.CORE_COORDINATE.equals(coord));
    }

    @Test
    public void testConstructorWithNameAndClass() {
        Log logBoth = new Log("MyLogger", LogTest.class);
        assertNotNull("Log(String, Class) 的 coordinate 不应为 null", logBoth.getCoordinate());
        assertEquals("MyLogger", logBoth.getName());
    }

    // ==================== getEffectiveCoordinate 测试 ====================

    @Test
    public void testLogFirstMode_WithBuiltinCoordinate() {
        Log.setCoordinateMode(Log.CoordinateMode.LOG_FIRST);
        MDC.clear();

        Log testLog = new Log(LogTest.class);
        String effective = testLog.getEffectiveCoordinate();
        assertNotNull("LOG_FIRST 模式下应有有效坐标", effective);
        // 有内置坐标时应使用内置坐标
        if (testLog.getCoordinate() != null && !testLog.getCoordinate().isEmpty()) {
            assertEquals("有内置坐标时优先使用内置坐标", testLog.getCoordinate(), effective);
        } else {
            assertEquals("无内置坐标时降级到 CORE_COORDINATE", Const.CORE_COORDINATE, effective);
        }
    }

    @Test
    public void testLogFirstMode_FallbackToMdc() {
        Log.setCoordinateMode(Log.CoordinateMode.LOG_FIRST);
        // Log(String) 的 coordinate 为 null
        String mdcCoord = "com.ecat:mdc-fallback";
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, mdcCoord);

        Log testLog = new Log("NoCoordinateLogger");
        String effective = testLog.getEffectiveCoordinate();
        assertEquals("无内置坐标时应降级到 MDC", mdcCoord, effective);
    }

    @Test
    public void testLogFirstMode_FallbackToCore() {
        Log.setCoordinateMode(Log.CoordinateMode.LOG_FIRST);
        MDC.clear();

        Log testLog = new Log("NoCoordinateLogger");
        String effective = testLog.getEffectiveCoordinate();
        assertEquals("无内置坐标且无 MDC 时应降级到 CORE_COORDINATE", Const.CORE_COORDINATE, effective);
    }

    @Test
    public void testMdcFirstMode_WithMdc() {
        Log.setCoordinateMode(Log.CoordinateMode.MDC_FIRST);
        String mdcCoord = "com.ecat:mdc-priority";
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, mdcCoord);

        Log testLog = new Log(LogTest.class);
        String effective = testLog.getEffectiveCoordinate();
        assertEquals("MDC_FIRST 模式下 MDC 优先", mdcCoord, effective);
    }

    @Test
    public void testMdcFirstMode_FallbackToBuiltin() {
        Log.setCoordinateMode(Log.CoordinateMode.MDC_FIRST);
        MDC.clear();

        Log testLog = new Log(LogTest.class);
        String effective = testLog.getEffectiveCoordinate();
        // 有内置坐标时应降级到内置坐标
        String builtin = testLog.getCoordinate();
        if (builtin != null && !builtin.isEmpty()) {
            assertEquals("MDC 为空时应降级到内置坐标", builtin, effective);
        } else {
            assertEquals("内置坐标也为空时应降级到 CORE_COORDINATE", Const.CORE_COORDINATE, effective);
        }
    }

    @Test
    public void testMdcFirstMode_FallbackToCore() {
        Log.setCoordinateMode(Log.CoordinateMode.MDC_FIRST);
        MDC.clear();

        Log testLog = new Log("NoCoordinateLogger");
        String effective = testLog.getEffectiveCoordinate();
        assertEquals("无 MDC 且无内置坐标时应降级到 CORE_COORDINATE", Const.CORE_COORDINATE, effective);
    }

    @Test
    public void testLogOnlyMode_IgnoresMdc() {
        Log.setCoordinateMode(Log.CoordinateMode.LOG_ONLY);
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, "com.ecat:should-be-ignored");

        Log testLog = new Log(LogTest.class);
        String effective = testLog.getEffectiveCoordinate();
        // LOG_ONLY 应忽略 MDC
        assertNotNull("LOG_ONLY 模式下不应为 null", effective);
        assertNotEquals("LOG_ONLY 不应使用 MDC 坐标", "com.ecat:should-be-ignored", effective);
    }

    @Test
    public void testLogOnlyMode_FallbackToCore() {
        Log.setCoordinateMode(Log.CoordinateMode.LOG_ONLY);
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, "com.ecat:should-be-ignored");

        Log testLog = new Log("NoCoordinateLogger");
        String effective = testLog.getEffectiveCoordinate();
        assertEquals("无内置坐标时应使用 CORE_COORDINATE", Const.CORE_COORDINATE, effective);
    }

    @Test
    public void testMdcOnlyMode_WithMdc() {
        Log.setCoordinateMode(Log.CoordinateMode.MDC_ONLY);
        String mdcCoord = "com.ecat:mdc-only";
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, mdcCoord);

        Log testLog = new Log(LogTest.class);
        String effective = testLog.getEffectiveCoordinate();
        assertEquals("MDC_ONLY 模式下应使用 MDC", mdcCoord, effective);
    }

    @Test
    public void testMdcOnlyMode_FallbackToCore() {
        Log.setCoordinateMode(Log.CoordinateMode.MDC_ONLY);
        MDC.clear();

        Log testLog = new Log(LogTest.class);
        String effective = testLog.getEffectiveCoordinate();
        assertEquals("MDC_ONLY 无 MDC 时应降级到 CORE_COORDINATE", Const.CORE_COORDINATE, effective);
    }

    // ==================== 日志委托测试 ====================

    @Test
    public void testLoggerDelegationDoesNotThrow() {
        // 验证所有日志级别不会抛出异常
        log.trace("trace message");
        log.debug("debug message");
        log.info("info message");
        log.warn("warn message");
        log.error("error message");

        log.trace("trace {}", "arg");
        log.debug("debug {} {}", "a", "b");
        log.info("info {} {} {}", "a", "b", "c");
        log.warn("warn message", new RuntimeException("test"));
        log.error("error message", new RuntimeException("test"));
    }

    @Test
    public void testGetName() {
        assertEquals("TestLogger", log.getName());
    }

    @Test
    public void testIsEnabledMethods() {
        // 这些方法委托给 SLF4J，只验证不会抛出异常
        log.isTraceEnabled();
        log.isDebugEnabled();
        log.isInfoEnabled();
        log.isWarnEnabled();
        log.isErrorEnabled();
    }
}
