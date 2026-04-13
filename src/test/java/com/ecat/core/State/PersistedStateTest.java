package com.ecat.core.State;

import com.alibaba.fastjson2.JSON;
import org.junit.Test;
import static org.junit.Assert.*;

public class PersistedStateTest {

    @Test
    public void testSerializeDeserialize_DoubleValue() {
        PersistedState state = new PersistedState();
        state.value = 42.5;
        state.statusCode = 1;
        state.updateTimeEpochMs = 1710000000000L;
        state.nativeUnitStr = "AirVolumeUnit.PPM";

        String json = JSON.toJSONString(state);
        PersistedState restored = JSON.parseObject(json, PersistedState.class);

        // fastjson2 反序列化 Object 字段时，浮点数变为 BigDecimal
        assertTrue(restored.value instanceof Number);
        assertEquals(42.5, ((Number) restored.value).doubleValue(), 0.001);
        assertEquals(1, restored.statusCode);
        assertEquals(1710000000000L, restored.updateTimeEpochMs);
        assertEquals("AirVolumeUnit.PPM", restored.nativeUnitStr);
    }

    @Test
    public void testSerializeDeserialize_BooleanValue() {
        PersistedState state = new PersistedState();
        state.value = true;
        state.statusCode = 1;
        state.updateTimeEpochMs = 0L;
        state.nativeUnitStr = null;

        String json = JSON.toJSONString(state);
        PersistedState restored = JSON.parseObject(json, PersistedState.class);

        assertEquals(true, restored.value);
        assertNull(restored.nativeUnitStr);
    }

    @Test
    public void testSerializeDeserialize_StringValue() {
        PersistedState state = new PersistedState();
        state.value = "on";
        state.statusCode = 1;
        state.updateTimeEpochMs = 1000L;
        state.nativeUnitStr = null;

        String json = JSON.toJSONString(state);
        PersistedState restored = JSON.parseObject(json, PersistedState.class);

        assertEquals("on", restored.value);
    }

    @Test
    public void testSerializeDeserialize_NullValue() {
        PersistedState state = new PersistedState();
        state.value = null;
        state.statusCode = -1;
        state.updateTimeEpochMs = 0L;
        state.nativeUnitStr = null;

        String json = JSON.toJSONString(state);
        PersistedState restored = JSON.parseObject(json, PersistedState.class);

        assertNull(restored.value);
        assertEquals(-1, restored.statusCode);
    }
}
