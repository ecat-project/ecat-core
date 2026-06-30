package com.ecat.core.Bus.event;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.*;

/**
 * BusEvent 信封单测——工厂构造、字段、null 校验。
 */
public class BusEventTest {

    /** 测试用 BusPayload——BusEvent 要求 T extends BusPayload，String 不满足该类型上界。 */
    private static final class TestPayload implements BusPayload {
        TestPayload(String value) {
        }
    }

    @Test
    public void ofSetsMetadata() {
        EventContext ctx = EventContext.root(EventContext.Source.DEVICE_POLL, null);
        TestPayload payload = new TestPayload("payload");
        BusEvent<TestPayload> e = BusEvent.of("device.data.update", payload, ctx);
        assertEquals("device.data.update", e.getType());
        assertSame(payload, e.getPayload());
        assertNotNull(e.getFiredAt());
        assertNotNull(e.getUuid());
        assertSame(ctx, e.getContext());
    }

    @Test
    public void nullRequiredThrows() {
        EventContext ctx = EventContext.root(EventContext.Source.DEVICE_POLL, null);
        TestPayload payload = new TestPayload("p");
        try {
            new BusEvent<TestPayload>(null, payload, Instant.now(), "u", ctx);
            fail("type=null 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException ok) { }
        try {
            BusEvent.of("t", payload, null);
            fail("context=null 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException ok) { }
    }
}
