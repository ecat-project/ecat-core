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

import java.util.regex.Pattern;

/**
 * "major.minor" 格式版本词汇的唯一权威（格式判定 + 数值比较）。
 * <p>
 * 持久化解析（旧计数器/未加引号形态拦截）、createEntry 盖戳校验、迁移门控等
 * 消费点一律经本类判定与比较，禁各自手写解析——格式规则只此一份。
 * <p>
 * 注意区分三个 "版本" 概念：本类表达条目数据格式版本（"major.minor" 两段）；
 * {@link ConfigVersion} 表达 integrations.yml 文件版本（"major.minor.patch" 三段，
 * 与 CLI 版本同步）；集成自身版本另属集成元数据。三者语义独立，互不复用。
 *
 * @author coffee
 */
public final class FormatVersion {

    /** 合法格式：两段非负整数，禁前导零。 */
    public static final Pattern FORMAT = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    private FormatVersion() {
        // 工具类，禁止实例化
    }

    /**
     * 判定是否为 well-formed 的 "major.minor" 格式版本。
     *
     * @param v 待判定字符串
     * @return null 或不匹配两段格式时 false，否则 true
     */
    public static boolean isWellFormed(String v) {
        return v != null && FORMAT.matcher(v).matches();
    }

    /**
     * 要求 well-formed，否则抛 {@link ConfigFormatException}（持久化数据非法路径）。
     *
     * @param v        待校验值
     * @param location 出错位置（人读定位），透传进异常
     */
    public static void requireWellFormed(String v, String location) {
        if (!isWellFormed(v)) {
            throw new ConfigFormatException(location, "major.minor 字符串", String.valueOf(v));
        }
    }

    /**
     * 数值比较：先比 major 再比 minor（按数值不按字典序，4.10 &gt; 4.9）。
     *
     * @param a 左值
     * @param b 右值
     * @return 负数/零/正数，语义同 {@link Comparable}
     * @throws IllegalArgumentException 任一参非 well-formed（程序员错误，属调用方缺陷而非数据问题）
     */
    public static int compare(String a, String b) {
        if (!isWellFormed(a) || !isWellFormed(b)) {
            throw new IllegalArgumentException(
                    "格式版本比较要求两参均已 well-formed: a=" + a + ", b=" + b);
        }
        int dotA = a.indexOf('.');
        int dotB = b.indexOf('.');
        int byMajor = Integer.compare(
                Integer.parseInt(a.substring(0, dotA)), Integer.parseInt(b.substring(0, dotB)));
        if (byMajor != 0) {
            return byMajor;
        }
        return Integer.compare(
                Integer.parseInt(a.substring(dotA + 1)), Integer.parseInt(b.substring(dotB + 1)));
    }
}
