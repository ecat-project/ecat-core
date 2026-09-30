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

package com.ecat.core.State;

import java.util.concurrent.ConcurrentMap;

/**
 * 持久化库 schema 迁移：storeSchemaVersion 升版时对旧版本库执行的一次性原地改写。
 *
 * <p>治理纪律：凡改持久化结构（PersistedState 字段 / 复合键格式 / map 拓扑 / 存储引擎）
 * 一律升 StateManager.STORE_SCHEMA_VERSION 并为本旧版本补一个本实现，不设免升例外——
 * 库级单轴版本是打开库时判代的唯一依据，记录不携带版本（无代数据 = 误读为当代的静默损坏）。
 *
 * <p>实现约定：rewrite 只依赖 states map 自身内容完成改写，不改 meta——版本推进与
 * commit 由 StateManager 统一执行，每步 rewrite + 版本推进 + commit 构成一个事务原子
 * （中途崩溃回滚到旧版本，重开重跑）。实现类按 fromVersion 升序排入迁移链，
 * fromVersion 必须与链上前一步 toVersion 衔接（缺步的旧库打开时旁置重建，不静默跳代）。
 */
public interface StoreMigration {

    /** 迁移起始库版本（存在该版本的存量库） */
    int fromVersion();

    /** 迁移目标库版本（= fromVersion + 1，链式逐步推进） */
    int toVersion();

    /** 原地改写 states map 内容到目标版本格式 */
    void rewrite(ConcurrentMap<String, String> states);
}
