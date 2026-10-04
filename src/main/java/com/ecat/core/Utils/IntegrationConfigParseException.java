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

/**
 * jar 内 ecat-config.yml 解析失败的唯一异常类型：jarPath + 原因链，替代 printStackTrace 吞异常。
 *
 * <p>场景：解析失败 = jar 损坏 / 非 YAML 内容 / 字段类型错。调用方两路：
 * boot 扫描区（IntegrationManager.collectIntegrationInfos，隔离点账后该坐标不加载，
 * 其余集成照常）；运行时 buildIntegrationInfo（经既有 {@code throws Exception} 传导至
 * 热加载回滚，REST message 可见）。坏 jar 不以「无依赖无 requires_core」的默认形态
 * 混入加载序（修复 = 重传/修正该集成 jar）。
 *
 * <p>注意：jar 无 ecat-config.yml / 流不可开是「无配置文件」的合法形态（此后由
 * requires_core 门以缺失拒绝），不走本异常——两早退分支在 {@link JarDependencyLoader}
 * 内保留。
 */
public class IntegrationConfigParseException extends Exception {

    /** 解析失败的 jar 绝对路径（诊断第一要素） */
    @Getter
    private final String jarFilePath;

    public IntegrationConfigParseException(String jarFilePath, Throwable cause) {
        super("解析集成 ecat-config.yml 失败: " + jarFilePath
            + " —— 坏 jar 不以默认形态混入加载序(修复=重传/修正该集成 jar)", cause);
        this.jarFilePath = jarFilePath;
    }
}
