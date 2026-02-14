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

package com.ecat.core.Utils.Mdc;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * MdcContext 单元测试
 * 
 * @author coffee
 */
public class MdcContextTest {

    @Before
    @After
    public void clearMdc() {
        MDC.clear();
    }

    @Test
    public void testCoordinateKey() {
        assertEquals("integration.coordinate", MdcContext.INTEGRATION_COORDINATE_KEY);
    }

    @Test
    public void testCapture() {
        MDC.put("key1", "value1");
        MDC.put("key2", "value2");

        Map<String, String> captured = MdcContext.capture();

        assertEquals(2, captured.size());
        assertEquals("value1", captured.get("key1"));
        assertEquals("value2", captured.get("key2"));
    }

    @Test
    public void testCaptureEmpty() {
        Map<String, String> captured = MdcContext.capture();
        assertTrue(captured == null || captured.isEmpty());
    }

    @Test
    public void testRestoreWithContext() {
        MDC.put("existing", "value");

        Map<String, String> newContext = new HashMap<>();
        newContext.put("newKey", "newValue");

        try (MdcContext.RestoreContext ignored = MdcContext.restore(newContext)) {
            assertEquals("newValue", MDC.get("newKey"));
        }

        // 退出后恢复原状态
        assertEquals("value", MDC.get("existing"));
        assertNull(MDC.get("newKey"));
    }

    @Test
    public void testRestoreWithNullContext() {
        MDC.put("existing", "value");

        try (MdcContext.RestoreContext ignored = MdcContext.restore()) {
            // null context 不设置新值
        }

        // 原状态恢复
        assertEquals("value", MDC.get("existing"));
    }

    @Test
    public void testRunWithContext() {
        MDC.put("before", "beforeValue");

        Map<String, String> newContext = new HashMap<>();
        newContext.put("during", "duringValue");

        AtomicReference<String> duringValue = new AtomicReference<>();
        MdcContext.runWithContext(newContext, () -> {
            duringValue.set(MDC.get("during"));
        });

        assertEquals("duringValue", duringValue.get());
        // 执行后恢复原状态
        assertEquals("beforeValue", MDC.get("before"));
        assertNull(MDC.get("during"));
    }

    @Test
    public void testRunWithNullContext() {
        AtomicBoolean executed = new AtomicBoolean(false);
        MdcContext.runWithContext(null, () -> executed.set(true));
        assertTrue(executed.get());
    }

    @Test
    public void testWithContextFunction() {
        // 先设置上下文
        MDC.put("funcKey", "funcValue");

        // 在上下文存在时创建包装函数（此时会捕获上下文）
        java.util.function.Function<String, String> wrappedFunc = MdcContext.withContext(input -> {
            return MDC.get("funcKey");
        });

        // 清除当前上下文
        MDC.clear();
        assertNull(MDC.get("funcKey"));

        // 使用包装函数执行，应该能获取到之前捕获的上下文
        String result = wrappedFunc.apply("test");

        assertEquals("funcValue", result);
    }

    @Test
    public void testSetAndGetCoordinate() {
        MdcContext.setCoordinate("test-coord");
        assertEquals("test-coord", MdcContext.getCoordinate());
    }

    @Test
    public void testSetNullCoordinate() {
        MdcContext.setCoordinate("existing");
        MdcContext.setCoordinate(null);

        // null 不覆盖
        assertEquals("existing", MdcContext.getCoordinate());
    }

    @Test
    public void testClearCoordinate() {
        MdcContext.setCoordinate("to-clear");
        MdcContext.clearCoordinate();
        assertNull(MdcContext.getCoordinate());
    }

    @Test
    public void testCoordinateAcrossContexts() {
        // 设置初始坐标
        MdcContext.setCoordinate("coord1");

        // 捕获上下文
        Map<String, String> captured = MdcContext.capture();

        // 在新线程/任务中恢复上下文
        AtomicReference<String> taskCoord = new AtomicReference<>();
        MdcContext.runWithContext(captured, () -> {
            taskCoord.set(MdcContext.getCoordinate());
        });

        assertEquals("coord1", taskCoord.get());
    }
}
