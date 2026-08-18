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

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * 日志通道 marker 常量（五通道结构缝）。
 *
 * <p>生命周期（lifecycle.log）与通讯健康（comm-health.log）两个通道按 marker 路由，
 * marker 名与 logback.xml 里 {@code MarkerRoutingFilter} 的 {@code <marker>} 配置共享此唯一来源，
 * 防止配置与发射方各写一份字符串漂移。
 *
 * <p>发射方（打这种日志的业务代码）属于后续「执行模型」阶段的任务：设备/集成启停、
 * 连接状态机转移、周期健康摘要等；本类先架好通道入口，不改动存量业务日志调用。
 */
public final class LogMarkers {

    /** 生命周期通道：集成/设备启停、enable/disable、配置变更、entry 增删（INFO，180 天） */
    public static final Marker LIFECYCLE = MarkerFactory.getMarker("LIFECYCLE");

    /** 通讯健康通道：连接状态转移 + 每设备周期摘要 + 熔断开合（INFO/WARN，90 天） */
    public static final Marker COMM = MarkerFactory.getMarker("COMM");

    /** 错误限频汇总行专用 marker：{@link ErrorRateLimitFilter} 对它无条件放行（防递归、汇总自身不限频） */
    public static final Marker RATE_LIMIT_SUMMARY = MarkerFactory.getMarker("ECAT_RATE_LIMIT_SUMMARY");

    private LogMarkers() {
    }
}
