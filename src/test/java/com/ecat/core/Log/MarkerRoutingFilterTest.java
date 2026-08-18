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

package com.ecat.core.Log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.Status;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link MarkerRoutingFilter} 单元测试。
 *
 * <p>验证 appender 级 marker 路由的三种判定（命中/未命中/未启动）与严格启动约束
 * （未配置 marker 名必须拒绝启动，不允许静默放行所有事件）。
 */
public class MarkerRoutingFilterTest {

    private LoggerContext context;
    private Logger logger;

    @Before
    public void setUp() {
        context = new LoggerContext();
        context.setName("marker-routing-filter-test");
        logger = context.getLogger("com.ecat.test.Routing");
    }

    @After
    public void tearDown() {
        context.stop();
    }

    private LoggingEvent event(Marker... markers) {
        LoggingEvent event = new LoggingEvent("fqcn", logger, Level.INFO, "msg", null, null);
        if (markers.length > 0) {
            event.setMarker(markers[0]);
        }
        return event;
    }

    @Test
    public void markerMatchReturnsOnMatch() {
        MarkerRoutingFilter filter = new MarkerRoutingFilter();
        filter.setMarker("LIFECYCLE");
        filter.setOnMatch(FilterReply.ACCEPT);
        filter.setOnMismatch(FilterReply.DENY);
        filter.start();

        assertEquals(FilterReply.ACCEPT, filter.decide(event(LogMarkers.LIFECYCLE)));
    }

    @Test
    public void noMarkerReturnsOnMismatch() {
        MarkerRoutingFilter filter = new MarkerRoutingFilter();
        filter.setMarker("LIFECYCLE");
        filter.setOnMatch(FilterReply.ACCEPT);
        filter.setOnMismatch(FilterReply.DENY);
        filter.start();

        assertEquals(FilterReply.DENY, filter.decide(event()));
    }

    @Test
    public void differentMarkerReturnsOnMismatch() {
        MarkerRoutingFilter filter = new MarkerRoutingFilter();
        filter.setMarker("LIFECYCLE");
        filter.setOnMatch(FilterReply.ACCEPT);
        filter.setOnMismatch(FilterReply.DENY);
        filter.start();

        assertEquals(FilterReply.DENY, filter.decide(event(LogMarkers.COMM)));
    }

    @Test
    public void childMarkerMatchesParentName() {
        // logback marker 语义：marker.contains 尊重引用层级，子 marker 事件也命中父名路由
        MarkerRoutingFilter filter = new MarkerRoutingFilter();
        filter.setMarker("LIFECYCLE");
        filter.setOnMatch(FilterReply.ACCEPT);
        filter.setOnMismatch(FilterReply.DENY);
        filter.start();

        org.slf4j.Marker child = MarkerFactory.getMarker("marker-routing-child");
        child.add(LogMarkers.LIFECYCLE);

        assertEquals(FilterReply.ACCEPT, filter.decide(event(child)));
    }

    @Test
    public void notStartedFilterIsNeutral() {
        MarkerRoutingFilter filter = new MarkerRoutingFilter();
        filter.setMarker("LIFECYCLE");
        // 未调用 start()：不能拦截事件，交由后续过滤链决定

        assertEquals(FilterReply.NEUTRAL, filter.decide(event(LogMarkers.LIFECYCLE)));
    }

    @Test
    public void startWithoutMarkerNameRefusesToStart() {
        // 严格模式：路由名缺失属于配置错误，必须显式失败，不允许默认全放行
        MarkerRoutingFilter filter = new MarkerRoutingFilter();
        filter.setContext(context);
        filter.start();

        assertFalse(filter.isStarted());
        assertTrue(context.getStatusManager().getCopyOfStatusList().stream()
                .anyMatch(status -> status.getLevel() == Status.ERROR));
    }
}
