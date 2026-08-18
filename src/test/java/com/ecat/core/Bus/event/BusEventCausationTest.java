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

package com.ecat.core.Bus.event;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.time.Instant;

import org.junit.After;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * BusEvent 因果字段测试：{@code of()} 自动捕获发布线程当次执行的 traceId（ULID）作为
 * causationId；发布线程无 traceId 时如实为 null；老 5 参构造器保持 causationId=null（兼容）。
 */
public class BusEventCausationTest {

    private static final class Payload implements BusPayload {
    }

    @After
    public void clearMdc() {
        MDC.clear();
    }

    @Test
    public void ofCapturesCurrentTraceIdAsCausationId() {
        String traceId = TraceContext.generateTraceId();
        TraceContext.setTraceId(traceId);
        BusEvent<Payload> event = BusEvent.of("test.topic", new Payload(), EventContext.root(EventContext.Source.DEVICE_POLL, null));
        assertThat("of() 须捕获发布线程 traceId", event.getCausationId(), is(traceId));
    }

    @Test
    public void ofLeavesCausationNullWhenNoTraceId() {
        MDC.clear();
        BusEvent<Payload> event = BusEvent.of("test.topic", new Payload(), EventContext.root(EventContext.Source.SYSTEM, null));
        assertThat("无 traceId 时如实 null（不编造）", event.getCausationId(), nullValue());
    }

    @Test
    public void legacyConstructorKeepsCausationNull() {
        BusEvent<Payload> event = new BusEvent<>("test.topic", new Payload(), Instant.now(),
                "uuid-1", EventContext.root(EventContext.Source.SYSTEM, null));
        assertThat("老 5 参构造器 causationId=null（uuid 语义不变，兼容）",
                event.getCausationId(), nullValue());
        assertThat(event.getUuid(), is("uuid-1"));
    }
}
