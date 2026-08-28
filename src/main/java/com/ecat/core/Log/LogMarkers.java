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
 * 日志 marker 常量。
 *
 * <p>历史注记：本类曾预置 LIFECYCLE / COMM 两个通道 marker（lifecycle.log / comm-health.log
 * 按 marker 路由，发射方规划属「后续执行模型阶段」）。该执行模型随传输自持执行终态
 * （19 号 v2）的 W7 引擎整删一并退役——通道、marker 与路由过滤器于 2026-08-28 删除，
 * 断连可见性由各传输 SDK 的断连状态转移行承载。现存的唯一 marker 服务于错误限频汇总行。
 */
public final class LogMarkers {

    /** 错误限频汇总行专用 marker：{@link ErrorRateLimitFilter} 对它无条件放行（防递归、汇总自身不限频） */
    public static final Marker RATE_LIMIT_SUMMARY = MarkerFactory.getMarker("ECAT_RATE_LIMIT_SUMMARY");

    private LogMarkers() {
    }
}
