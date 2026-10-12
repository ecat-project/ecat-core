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

import java.io.File;
import java.util.Map;

/**
 * integrations.yml 文件格式版本的唯一权威（常量 + 加载校验）。
 *
 * <p>语义钉死：本类版本<b>只</b>表达 integrations.yml root {@code version} 戳——
 * 文件结构本身的格式版本，由 core 写入、core 加载时校验。注意与其他三个 "版本"
 * 概念区分，禁互用：</p>
 * <ul>
 *   <li><b>≠ core 软件版本</b>：core 版本真源是 jar manifest 的 Implementation-Version；</li>
 *   <li><b>≠ 集成软件版本</b>：integrations.yml 各条目内的 {@code version} 键是集成自身
 *       版本（安装链写入，用于拼 jar 路径），与 root {@code version} 层级不同、语义不同；</li>
 *   <li><b>≠ entry 修改计数</b>：config_entries/*.yml 的 {@code version} 是条目修改计数
 *       （int，更新自增）；显式文件戳无迁移通道，只认当前值；3.3 无戳结构仅由
 *       {@link #requireLoadable3x(Object, Object)} 的加载期兼容口识别。</li>
 * </ul>
 *
 * <p>integrations.yml 格式示例（root {@code version} 为文件格式版本戳）：</p>
 * <pre>
 * version: "4.0"   # 文件格式版本戳（本类常量，加载时校验）
 * core:
 *   groupId: com.ecat
 *   artifactId: ecat-core
 *   version: 1.0.0
 * integrations:
 *   com.ecat:xxx:
 *     version: 3.1.0   # 集成软件版本，与 root version 层级不同语义不同
 *     ...
 * </pre>
 *
 * @author coffee
 */
public final class ConfigVersion {

    /**
     * integrations.yml 文件格式版本号（"major.minor" 两段，{@link FormatVersion} 词汇）。
     *
     * <p>与 entry 声明格式版本数值巧合、语义独立，互不引用。</p>
     */
    public static final String CURRENT_VERSION = "4.0";

    /** 校验异常定位串：root version 戳在 integrations.yml 中的位置（人读定位）。 */
    private static final String STAMP_LOCATION =
        new File(".ecat-data/core/integrations.yml").getAbsolutePath() + " root version";

    private ConfigVersion() {
        // 工具类，禁止实例化
    }

    /**
     * 获取当前 integrations.yml 文件格式版本号。
     *
     * <p>本值是文件格式版本，任何将其当 core 软件版本（依赖/升级/前置校验场景）的用法
     * 都是缺陷——core 软件版本真源是 jar manifest。</p>
     *
     * @return 版本号，如 "4.0"
     */
    public static String getVersion() {
        return CURRENT_VERSION;
    }

    /**
     * 加载期校验 3.3 存量 integrations.yml：无 root version 戳但保留 3.3 的
     * {@code integrations:} 结构时放行。
     *
     * <p>这不是恢复“任意无戳都兼容”的旧宽门：空文件、无 {@code integrations} 根、
     * 旧戳/未来戳/坏形态仍由 {@link #requireSupported(Object)} 拒绝。3.3 无戳文件
     * 只在读期兼容；下一次 core 写盘会由写侧统一补当前戳。</p>
     *
     * @param stampedVersion root version 原始值
     * @param integrationsRoot root 下 {@code integrations} 键原始值
     */
    public static void requireLoadable3x(Object stampedVersion, Object integrationsRoot) {
        if (stampedVersion == null && integrationsRoot instanceof Map) {
            return;
        }
        requireSupported(stampedVersion);
    }

    /**
     * integrations.yml root version 戳 fail-closed 校验：只认当前戳，其余一律异常。
     * <p>
     * 旧宽门语义（无戳/空视为兼容）已废止——无戳、旧戳、未来戳、非字符串均拒，全路径抛
     * {@link ConfigFormatException}，不返回 boolean（调用方没有「自行处理不兼容」的自由度）。
     * 空文件（解析结果为空 map）= 无戳，同拒，无豁免。
     * <p>
     * 判定序：缺失/空 → 拒；非字符串 → 拒；格式非 "major.minor" → 拒；数值大于当前
     * （数据比代码新，前向不兼容）→ 拒；数值小于当前（旧戳残留）→ 拒；等于当前 → 放行。
     *
     * @param stampedVersion 文件 root version 原始值（yaml.load 产物，可能为 null/Double/String 等）
     * @throws ConfigFormatException 四态判定不通过时
     */
    public static void requireSupported(Object stampedVersion) {
        if (stampedVersion == null) {
            throw new ConfigFormatException(STAMP_LOCATION, CURRENT_VERSION, "缺失",
                "无 integrations 根的无戳文件不兼容；3.3 无戳存量由加载口识别，"
                    + "其余存量文件由归一执行器停机窗统一盖戳，"
                    + "手工编辑时必须带上带引号的 version: \"" + CURRENT_VERSION + "\"");
        }
        if (!(stampedVersion instanceof String)) {
            throw new ConfigFormatException(STAMP_LOCATION, CURRENT_VERSION,
                stampedVersion.getClass().getSimpleName() + ":" + stampedVersion,
                "version 必须是带引号的 \"major.minor\" 字符串；裸写数值会被 YAML 解析为 "
                    + stampedVersion.getClass().getSimpleName() + " 而拒绝");
        }
        String stamped = (String) stampedVersion;
        if (stamped.isEmpty()) {
            throw new ConfigFormatException(STAMP_LOCATION, CURRENT_VERSION, "空",
                "空串等同无效显式戳，不视为兼容；存量文件由归一执行器停机窗统一盖戳");
        }
        FormatVersion.requireWellFormed(stamped, STAMP_LOCATION);
        int cmp = FormatVersion.compare(stamped, CURRENT_VERSION);
        if (cmp > 0) {
            throw new ConfigFormatException(STAMP_LOCATION, CURRENT_VERSION, stamped,
                "数据比代码新，前向不兼容：文件由更新版本的 core 写出，本 core 不能读改写该文件");
        }
        if (cmp < 0) {
            throw new ConfigFormatException(STAMP_LOCATION, CURRENT_VERSION, stamped,
                "旧戳残留：旧 core 写的文件未经盖戳，由归一执行器停机窗统一盖戳，运行时无 legacy 通道");
        }
        // ==0：当前戳，放行
    }
}
