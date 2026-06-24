/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ecat.core.ConfigFlow;

/**
 * ConfigFlow ABORT reason 词表（HA config-flow 词汇）。
 * <p>供 {@link ConfigFlowService#drive} 在 R5 去重命中时构造 {@link ConfigFlowResult#abort(String)} 的 reason，
 * 以及 broker（{@code ZeroconfDiscoveryIntegration} 等）据此分级日志（去重=正常→DEBUG，真错误→WARN）。
 *
 * <ul>
 *   <li>{@link #ALREADY_CONFIGURED}：同 uniqueId 的 entry 已持久化存在（已添加设备重复发现）。</li>
 *   <li>{@link #ALREADY_IN_PROGRESS}：同 uniqueId 已有活跃 flow 在跑。</li>
 * </ul>
 *
 * @author coffee
 */
public final class AbortReason {

    /** 同 uniqueId 的 entry 已存在（已添加设备的重复发现/导入）。 */
    public static final String ALREADY_CONFIGURED = "already_configured";

    /** 同 uniqueId 已有活跃 flow 在进行中。 */
    public static final String ALREADY_IN_PROGRESS = "already_in_progress";

    private AbortReason() {
    }
}
