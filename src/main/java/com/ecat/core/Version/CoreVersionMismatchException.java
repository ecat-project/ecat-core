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

package com.ecat.core.Version;

import lombok.Getter;

/**
 * requires_core 加载门失败异常：集成声明的 core 版本约束未被当前 core 版本满足（或缺失/
 * 无法解析）时由加载门抛出，拒绝该集成加载。
 *
 * <p>为什么是 RuntimeException：加载门的三个接手方（启动加载循环、运行时安装、运行时启用）
 * 的既有回滚链全是 {@code catch (Exception e)}，非受检即可零改动接入既有隔离/回滚语义；
 * 异常自身有类型、有三要素载荷、有明确接手方，与「明确异常」纪律不冲突。
 *
 * <p>message 三形态（不满足/缺失/解析失败）均自带三要素与修复指引，日志即诊断；
 * 载荷三字段供程序化消费（REST 响应、启动报告）。
 *
 * @author coffee
 */
public class CoreVersionMismatchException extends RuntimeException {

    /** 集成坐标 groupId:artifactId */
    @Getter
    private final String coordinate;

    /** 集成声明的约束原文（null=缺失形态；非法串=原文透传供诊断） */
    @Getter
    private final String requiresCore;

    /** 门判定时读到的 core 实际版本 */
    @Getter
    private final String actualCoreVersion;

    public CoreVersionMismatchException(String coordinate, String requiresCore,
                                        String actualCoreVersion, String message) {
        super(message);
        this.coordinate = coordinate;
        this.requiresCore = requiresCore;
        this.actualCoreVersion = actualCoreVersion;
    }

    public CoreVersionMismatchException(String coordinate, String requiresCore,
                                        String actualCoreVersion, String message, Throwable cause) {
        super(message, cause);
        this.coordinate = coordinate;
        this.requiresCore = requiresCore;
        this.actualCoreVersion = actualCoreVersion;
    }
}
