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

import static org.junit.Assert.*;

/**
 * MdcCoordinateConverter 单元测试
 * 
 * @author coffee
 */
public class MdcCoordinateConverterTest {

    @Before
    @After
    public void clearMdc() {
        MDC.clear();
    }

    @Test
    public void testCoordinateKey() {
        assertEquals("integration.coordinate", MdcCoordinateConverter.COORDINATE_KEY);
    }

    @Test
    public void testSetAndGetCoordinate() {
        MdcCoordinateConverter.setCoordinate("test-integration");
        assertEquals("test-integration", MdcCoordinateConverter.getCoordinate());
    }

    @Test
    public void testSetNullCoordinate() {
        MdcCoordinateConverter.setCoordinate("existing");
        MdcCoordinateConverter.setCoordinate(null);

        // null 不应该覆盖已存在的值
        assertEquals("existing", MdcCoordinateConverter.getCoordinate());
    }

    @Test
    public void testSetEmptyCoordinate() {
        MdcCoordinateConverter.setCoordinate("existing");
        MdcCoordinateConverter.setCoordinate("");

        // 空字符串不应该覆盖已存在的值
        assertEquals("existing", MdcCoordinateConverter.getCoordinate());
    }

    @Test
    public void testGetCoordinateWhenNotSet() {
        assertNull(MdcCoordinateConverter.getCoordinate());
    }

    @Test
    public void testClearCoordinate() {
        MdcCoordinateConverter.setCoordinate("to-clear");
        assertEquals("to-clear", MdcCoordinateConverter.getCoordinate());

        MdcCoordinateConverter.clearCoordinate();
        assertNull(MdcCoordinateConverter.getCoordinate());
    }

    @Test
    public void testMultipleOperations() {
        assertNull(MdcCoordinateConverter.getCoordinate());

        MdcCoordinateConverter.setCoordinate("first");
        assertEquals("first", MdcCoordinateConverter.getCoordinate());

        MdcCoordinateConverter.setCoordinate("second");
        assertEquals("second", MdcCoordinateConverter.getCoordinate());

        MdcCoordinateConverter.clearCoordinate();
        assertNull(MdcCoordinateConverter.getCoordinate());

        MdcCoordinateConverter.setCoordinate("third");
        assertEquals("third", MdcCoordinateConverter.getCoordinate());
    }

    @Test
    public void testMdcIntegration() {
        // 测试与 SLF4J MDC 的集成
        MdcCoordinateConverter.setCoordinate("mdc-test");

        // 验证通过 MDC 直接获取
        assertEquals("mdc-test", MDC.get(MdcCoordinateConverter.COORDINATE_KEY));

        // 验证通过 MDC 直接清除
        MDC.remove(MdcCoordinateConverter.COORDINATE_KEY);
        assertNull(MdcCoordinateConverter.getCoordinate());
    }
}
