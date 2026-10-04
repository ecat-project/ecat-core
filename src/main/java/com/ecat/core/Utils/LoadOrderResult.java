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

package com.ecat.core.Utils;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.ecat.core.Integration.IntegrationInfo;

/**
 * getLoadOrder 返回载体：可加载序（依赖先行）+ 隔离坐标→原因映射。
 *
 * <p>协同不变量（BootFailureIsolationTest 对账，消费方 loadIntegrations 依赖其成立）：
 * blocked 与 loadOrder 坐标集不相交；blocked ⊆ 本轮已声明坐标；
 * loadOrder 内任一坐标的全部声明依赖 ∈ loadOrder 坐标集（闭包性质——依赖缺失的
 * 传递闭包整体隔离后的剩余集必自洽）；blocked 非空时每条 reason 都可溯源到某个
 * 缺失依赖坐标。
 */
@Getter
public class LoadOrderResult {

    /** 可加载坐标序（依赖先行；既有反转语义保留） */
    private final List<IntegrationInfo> loadOrder;

    /** 隔离坐标 → 原因（LinkedHashMap 有序：种子先于传播、先到先记；只读视图防调用方突变） */
    private final Map<String, String> blockedCoordinates;

    public LoadOrderResult(List<IntegrationInfo> loadOrder, Map<String, String> blockedCoordinates) {
        this.loadOrder = Collections.unmodifiableList(loadOrder);
        this.blockedCoordinates = Collections.unmodifiableMap(blockedCoordinates);
    }
}
