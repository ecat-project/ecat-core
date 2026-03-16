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

package com.ecat.core.ConfigEntry;

import java.util.List;

/**
 * ConfigEntry 持久化接口
 * <p>
 * 定义配置条目的持久化操作，包括加载、保存、更新和删除。
 *
 * @author coffee
 */
public interface ConfigEntryPersistence {

    /**
     * 加载所有配置条目
     *
     * @return 所有配置条目列表
     */
    List<ConfigEntry> loadAll();

    /**
     * 保存配置条目
     *
     * @param entry 配置条目
     */
    void save(ConfigEntry entry);

    /**
     * 更新配置条目
     *
     * @param entry 配置条目
     */
    void update(ConfigEntry entry);

    /**
     * 删除配置条目
     *
     * @param entryId 配置条目 ID
     */
    void delete(String entryId);
}
