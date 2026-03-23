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

package com.ecat.core.ConfigFlow.ConfigItem;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * DynamicEnumConfigItem 单元测试
 */
public class DynamicEnumConfigItemTest {

    /**
     * 模拟无串口时的选项
     */
    private Supplier<Map<String, String>> noPortsSupplier = () -> {
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put("", "-- 无串口信息 --");
        return ports;
    };

    /**
     * 模拟有串口时的选项
     */
    private Supplier<Map<String, String>> withPortsSupplier = () -> {
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put("", "-- 请选择串口 --");
        ports.put("/dev/ttyUSB0", "USB Serial (ttyUSB0)");
        ports.put("/dev/ttyUSB1", "USB Serial (ttyUSB1)");
        return ports;
    };

    @Test
    public void testRequiredWithEmptyString_ShouldFail() {
        DynamicEnumConfigItem item = new DynamicEnumConfigItem("port", true, noPortsSupplier);
        item.displayName("串口设备");

        // 空字符串应该校验失败
        String error = (String) item.validate("");
        assertNotNull("空字符串应该校验失败", error);
        assertTrue("错误信息应包含'必需'", error.contains("必需"));
    }

    @Test
    public void testRequiredWithNull_ShouldFail() {
        DynamicEnumConfigItem item = new DynamicEnumConfigItem("port", true, noPortsSupplier);
        item.displayName("串口设备");

        // null 应该校验失败
        String error = (String) item.validate(null);
        assertNotNull("null 应该校验失败", error);
        assertTrue("错误信息应包含'必需'", error.contains("必需"));
    }

    @Test
    public void testNotRequiredWithEmptyString_ShouldPass() {
        DynamicEnumConfigItem item = new DynamicEnumConfigItem("port", false, noPortsSupplier);

        // 非必填时，空字符串应该通过
        Object error = item.validate("");
        assertNull("非必填时，空字符串应该通过", error);
    }

    @Test
    public void testRequiredWithValidOption_ShouldPass() {
        DynamicEnumConfigItem item = new DynamicEnumConfigItem("port", true, withPortsSupplier);
        item.displayName("串口设备");

        // 选择有效选项应该通过
        Object error = item.validate("/dev/ttyUSB0");
        assertNull("有效选项应该通过校验", error);
    }

    @Test
    public void testRequiredWithInvalidOption_ShouldFail() {
        DynamicEnumConfigItem item = new DynamicEnumConfigItem("port", true, withPortsSupplier);
        item.displayName("串口设备");

        // 选择无效选项应该失败
        String error = (String) item.validate("/dev/ttyS99");
        assertNotNull("无效选项应该校验失败", error);
        assertTrue("错误信息应包含'无效'", error.contains("无效"));
    }
}
