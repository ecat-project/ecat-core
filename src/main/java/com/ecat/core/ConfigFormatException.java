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

package com.ecat.core;

/**
 * 持久化数据格式版本与预期不符时的显式失败信号。
 * <p>
 * 适用三类场景：
 * <ul>
 *   <li>加载持久化 entry 时 version 缺失/非字符串/格式坏（fail-closed，中止 core 启动）；</li>
 *   <li>createEntry 显式带版本但与集成声明不符（或格式非法）；</li>
 *   <li>迁移门控判定版本越界。</li>
 * </ul>
 * <p>
 * 与 requires_core 约束失败（CoreVersionMismatchException 表达的域）是两个独立域：本异常
 * 只表达「数据格式版本」失败。异常消息格式是契约——三字段渲染 + 修法指引，供操作员
 * 与脚本定位，勿改动渲染结构。
 *
 * @author coffee
 */
public class ConfigFormatException extends RuntimeException {

    /** 出错位置（人读定位），如 "com.ecat:x(entryId=8f3a...)" 或 ".ecat-data/core/config_entries"。 */
    private final String location;

    /** 期望的格式/值，如 "4.0" 或 "major.minor 字符串"。 */
    private final String expected;

    /** 实际读到的值描述（类型:值），如 "Integer:1"、"Double:4.0"、"缺失"。 */
    private final String actual;

    public ConfigFormatException(String location, String expected, String actual) {
        super("配置格式版本异常: 位置=" + location + " 期望=" + expected + " 实际=" + actual
                + "。修法指引: 存量格式版本由归一执行器在停机窗统一改写，运行时不接受旧格式");
        this.location = location;
        this.expected = expected;
        this.actual = actual;
    }

    public String getLocation() {
        return location;
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }
}
