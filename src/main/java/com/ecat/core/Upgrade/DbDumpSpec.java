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

package com.ecat.core.Upgrade;

import lombok.Builder;
import lombok.Value;

/**
 * DB dump 约定块(core 侧中性规格)。
 *
 * <p>DbDumpExecutor 的分派输入:前六字段与集成包 ecat-config.yml 的 db 约定声明
 * (manifest wire 键位)同名同义;调用方(升级编排器/快照服务消费方)从各自读取的
 * manifest db_conventions 块映射而来——core 侧不向上依赖 core-api 的 wire 类型,
 * 故以本规格为跨 jar 载体。</p>
 *
 * <p>{@code groupId}/{@code artifactId} 为声明该约定的集成坐标(owner),wire 块
 * 不含、由调用方从所在 manifest item 补齐:file-copy 策略按
 * {@code .ecat-data/storage/{groupId}/{artifactId}/} 定位存储目录(table-family
 * 策略不消费,可为 null)。</p>
 *
 * @author coffee
 */
@Value
@Builder
public class DbDumpSpec {
    String domain;
    String engine;
    String mechanism;
    String historyTable;
    String legacyVersion;
    String dumpPolicy;
    String groupId;
    String artifactId;
}
