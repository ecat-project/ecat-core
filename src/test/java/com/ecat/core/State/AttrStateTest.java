package com.ecat.core.State;

import com.ecat.core.Bus.event.EventContext;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * AttrState 不可变快照的单测——验证字段持有、null 校验、集合防御性拷贝。
 */
public class AttrStateTest {

    private EventContext ctx() {
        return EventContext.root(EventContext.Source.DEVICE_POLL, null);
    }

    @Test
    public void builderSetsAllFields() {
        AttrState<?> s = AttrState.builder()
                .deviceId("dev-1").attrId("temp")
                .value(23.5).status(AttributeStatus.NORMAL)
                .displayPrecision(1).displayValue("23.5")
                .lastUpdated(Instant.ofEpochMilli(1000L))
                .lastChanged(Instant.ofEpochMilli(900L))
                .context(ctx())
                .build();
        assertEquals("dev-1", s.getDeviceId());
        assertEquals("temp", s.getAttrId());
        assertEquals(23.5, s.getValue());
        assertEquals(AttributeStatus.NORMAL, s.getStatus());
        assertEquals(1, s.getDisplayPrecision());
        assertEquals("23.5", s.getDisplayValue());
        assertEquals(Instant.ofEpochMilli(1000L), s.getLastUpdated());
        assertEquals(Instant.ofEpochMilli(900L), s.getLastChanged());
        assertNotNull(s.getContext());
    }

    @Test
    public void nullRequiredFieldsThrows() {
        try {
            AttrState.builder().attrId("a").status(AttributeStatus.NORMAL).context(ctx()).build();
            fail("deviceId 缺失应抛 IllegalArgumentException");
        } catch (IllegalArgumentException ok) { }
        try {
            AttrState.builder().deviceId("d").attrId("a").status(AttributeStatus.NORMAL).build();
            fail("context 缺失应抛 IllegalArgumentException");
        } catch (IllegalArgumentException ok) { }
    }

    @Test
    public void collectionValueIsDefensivelyCopied() {
        List<String> src = new ArrayList<String>(Arrays.asList("a", "b"));
        AttrState<?> s = AttrState.builder()
                .deviceId("d").attrId("a").value(src)
                .status(AttributeStatus.NORMAL).context(ctx())
                .build();
        // 修改源集合不影响快照
        src.add("c");
        @SuppressWarnings("unchecked")
        List<String> stored = (List<String>) s.getValue();
        assertEquals(2, stored.size());
        // 存储的是不可变视图
        try {
            stored.add("x");
            fail("快照内集合应为不可变");
        } catch (UnsupportedOperationException ok) { }
    }
}
