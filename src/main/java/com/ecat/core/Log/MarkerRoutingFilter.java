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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Marker;

/**
 * Appender 级 marker 路由过滤器。
 *
 * <p>logback 自带的 MarkerFilter 只能挂在 LoggerContext（turbo 层）上，而五通道结构需要
 * 「同一事件按 marker 进不同文件通道」的 appender 级分流，故提供此通用过滤器。
 *
 * <p>两种用法（onMatch/onMismatch 在 XML 中配置）：
 * <ul>
 *   <li>独占通道（lifecycle/comm-health）：{@code onMatch=ACCEPT, onMismatch=DENY}
 *       ——只收带本 marker 的事件；</li>
 *   <li>防双写（app/error 通道）：{@code onMatch=DENY, onMismatch=NEUTRAL}
 *       ——已划入专属通道的事件不再落通用通道。</li>
 * </ul>
 *
 * <p>判定用 {@link Marker#contains(String)}：子 marker 引用了通道 marker 同样命中，
 * 与 logback 官方 turbo MarkerFilter 语义一致。
 */
@Getter
@Setter
public class MarkerRoutingFilter extends Filter<ILoggingEvent> {

    /** 路由目标 marker 名（与 {@link LogMarkers} 常量同名） */
    private String marker;

    /** 事件带本 marker 时的裁决，默认 ACCEPT（独占通道用法） */
    private FilterReply onMatch = FilterReply.ACCEPT;

    /** 事件不带本 marker 时的裁决，默认 DENY（独占通道用法） */
    private FilterReply onMismatch = FilterReply.DENY;

    @Override
    public void start() {
        if (marker == null || marker.isEmpty()) {
            // 严格模式：路由名缺失是配置错误，显式失败而非默认全放行
            addError("MarkerRoutingFilter 必须配置 <marker> 路由名（见 LogMarkers 常量）");
            return;
        }
        super.start();
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }
        Marker eventMarker = event.getMarker();
        if (eventMarker != null && eventMarker.contains(marker)) {
            return onMatch;
        }
        return onMismatch;
    }
}
