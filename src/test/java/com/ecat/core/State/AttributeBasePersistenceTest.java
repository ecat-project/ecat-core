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

package com.ecat.core.State;

import com.ecat.core.Device.DeviceBase;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

public class AttributeBasePersistenceTest {

    @Test
    public void testPersistable_defaultFalse() {
        NumericAttribute attr = new NumericAttribute("test", AttributeClass.TEMPERATURE,
            null, null, 1, false, false);
        assertFalse(attr.isPersistable());
    }

    @Test
    public void testPersistable_setTrue() {
        NumericAttribute attr = new NumericAttribute("test", AttributeClass.TEMPERATURE,
            null, null, 1, false, false);
        attr.setPersistable(true);
        assertTrue(attr.isPersistable());
    }

    @Test
    public void testDefaultValue_defaultNull() {
        NumericAttribute attr = new NumericAttribute("test", AttributeClass.TEMPERATURE,
            null, null, 1, false, false);
        assertNull(attr.getDefaultValue());
    }

    @Test
    public void testDefaultValue_setAndGet() {
        NumericAttribute attr = new NumericAttribute("test", AttributeClass.TEMPERATURE,
            null, null, 1, false, true);
        attr.setDefaultValue(99.9);
        assertEquals(Double.valueOf(99.9), attr.getDefaultValue());
    }

    @Test
    public void testDefaultValue_viaConstructor() {
        NumericAttribute attr = new NumericAttribute("test", AttributeClass.TEMPERATURE,
            null, null, 1, false, true, true, 42.0, null);
        assertTrue(attr.isPersistable());
        assertEquals(Double.valueOf(42.0), attr.getDefaultValue());
    }

    @Test
    public void testRestore_doubleValue() {
        NumericAttribute attr = new NumericAttribute("test", AttributeClass.TEMPERATURE,
            null, null, 1, false, false);

        PersistedState state = new PersistedState();
        state.value = 25.5;
        state.statusCode = 1;
        state.updateTimeEpochMs = 1710000000000L;
        state.nativeUnitStr = null;

        attr.restore(state);

        assertEquals(Double.valueOf(25.5), attr.getValue());
        assertEquals(AttributeStatus.NORMAL, attr.getStatus());
        assertNotNull(attr.getUpdateTime());
    }

    @Test
    public void testRestore_bigDecimalValue() {
        // fastjson2 deserializes as BigDecimal for Object fields
        NumericAttribute attr = new NumericAttribute("test", AttributeClass.TEMPERATURE,
            null, null, 1, false, false);

        PersistedState state = new PersistedState();
        state.value = new BigDecimal("25.5");
        state.statusCode = 1;
        state.updateTimeEpochMs = 1710000000000L;
        state.nativeUnitStr = null;

        attr.restore(state);

        assertEquals(Double.valueOf(25.5), attr.getValue());
    }

    @Test
    public void testRestore_numberNarrowing_floatValue() {
        FloatAttribute attr = new FloatAttribute("test", AttributeClass.TEMPERATURE,
            null, null, 1, false, false);

        PersistedState state = new PersistedState();
        state.value = 3.14;
        state.statusCode = 1;
        state.updateTimeEpochMs = 1000L;
        state.nativeUnitStr = null;

        attr.restore(state);

        assertEquals(Float.valueOf(3.14f), attr.getValue(), 0.001f);
    }

    @Test
    public void testRestore_booleanValue() {
        BinaryAttribute attr = new BinaryAttribute("test", AttributeClass.STATUS, false);

        PersistedState state = new PersistedState();
        state.value = true;
        state.statusCode = 1;
        state.updateTimeEpochMs = 1000L;
        state.nativeUnitStr = null;

        attr.restore(state);

        assertEquals(Boolean.TRUE, attr.getValue());
    }

    @Test
    public void testRestore_stringValue() {
        TextAttribute attr = new TextAttribute("test", AttributeClass.TEXT, null, null, false);

        PersistedState state = new PersistedState();
        state.value = "hello";
        state.statusCode = 102;
        state.updateTimeEpochMs = 1000L;
        state.nativeUnitStr = null;

        attr.restore(state);

        assertEquals("hello", attr.getValue());
        assertEquals(AttributeStatus.ALARM, attr.getStatus());
    }

    @Test
    public void testRestore_nullValue() {
        TextAttribute attr = new TextAttribute("test", AttributeClass.TEXT, null, null, false);
        attr.updateValue("existing");

        PersistedState state = new PersistedState();
        state.value = null;
        state.statusCode = -1;
        state.updateTimeEpochMs = 0L;
        state.nativeUnitStr = null;

        attr.restore(state);

        assertNull(attr.getValue());
        assertEquals(AttributeStatus.EMPTY, attr.getStatus());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRestore_typeMismatch_throws() {
        TextAttribute attr = new TextAttribute("test", AttributeClass.TEXT, null, null, false);

        PersistedState state = new PersistedState();
        state.value = 123.45;
        state.statusCode = 1;
        state.updateTimeEpochMs = 1000L;
        state.nativeUnitStr = null;

        attr.restore(state);
    }

    @Test
    public void testRestoreFromDefault() {
        NumericAttribute attr = new NumericAttribute("test", AttributeClass.TEMPERATURE,
            null, null, 1, false, true);
        attr.setDefaultValue(0.0);

        attr.restoreFromDefault();

        assertEquals(Double.valueOf(0.0), attr.getValue());
        assertEquals(AttributeStatus.NORMAL, attr.getStatus());
        assertNotNull(attr.getUpdateTime());
    }

    @Test
    public void testRestore_zeroUpdateTime() {
        NumericAttribute attr = new NumericAttribute("test", AttributeClass.TEMPERATURE,
            null, null, 1, false, false);

        PersistedState state = new PersistedState();
        state.value = 10.0;
        state.statusCode = 1;
        state.updateTimeEpochMs = 0L;
        state.nativeUnitStr = null;

        attr.restore(state);

        assertEquals(Double.valueOf(10.0), attr.getValue());
        assertNull(attr.getUpdateTime()); // 0 means no updateTime
    }

    /**
     * bugs/20260824-001500 回归：重启恢复场景——属性已注册设备（id 非空）但从未发生过事件
     * （eventContext==null），restore 必须能重建 lastState（AttrState 构造的 context 必填
     * 由恢复源兜底），否则每次重启所有 persistable 属性状态静默丢失（异常被错误限频去重掩盖）。
     */
    @Test
    public void testRestore_noEventContext_rebuildsLastState() {
        DeviceBase device = mock(DeviceBase.class);
        when(device.getId()).thenReturn("dev-restore-1");

        TextAttribute attr = new TextAttribute("cylinder_id", AttributeClass.TEXT, null, null, true);
        attr.setDevice(device);

        PersistedState state = new PersistedState();
        state.version = 2;
        state.value = "GBW-E-0825";
        state.statusCode = 1;
        state.updateTimeEpochMs = 1787620333669L;

        attr.restore(state);

        AttrState<?> restored = attr.getState();
        assertNotNull("重启恢复后 state 必须可读（否则持久化闭环断裂）", restored);
        assertEquals("GBW-E-0825", restored.getValue());
    }
}
