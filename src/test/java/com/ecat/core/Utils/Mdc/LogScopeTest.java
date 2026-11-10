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
import org.junit.Test;
import org.slf4j.MDC;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * {@link LogScope} 恢复语义锁：close 恢复外层旧值（非删）——嵌套 scope 分层；
 * 外层缺席时关闭才移除键。
 */
public class LogScopeTest {

    @After
    public void tearDown() {
        MDC.clear();
    }

    @Test
    public void nestedScopeRestoresOuterValueNotDelete() {
        try (LogScope outer = LogScope.entry("outer-entry")) {
            try (LogScope inner = LogScope.entry("inner-entry")) {
                assertEquals("inner-entry", MDC.get(MdcContext.ENTRY_ID_KEY));
            }
            assertEquals("内层关闭须恢复外层旧值（非删）", "outer-entry", MDC.get(MdcContext.ENTRY_ID_KEY));
        }
        assertNull("最外层关闭后键回到缺席", MDC.get(MdcContext.ENTRY_ID_KEY));
    }

    @Test
    public void scopeOnAbsentKeyRemovesOnClose() {
        assertNull(MDC.get(MdcContext.DEVICE_ID_KEY));
        try (LogScope scope = LogScope.device("device-x")) {
            assertEquals("device-x", MDC.get(MdcContext.DEVICE_ID_KEY));
        }
        assertNull("外层本就缺席，关闭=移除（恢复缺席原状）", MDC.get(MdcContext.DEVICE_ID_KEY));
    }
}
