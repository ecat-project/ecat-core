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

import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationRegistry;
import com.ecat.core.Utils.DateTimeUtils;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ConfigEntry 注册器
 * <p>
 * 管理配置条目的注册、缓存和持久化。
 *
 * @author coffee
 */
public class ConfigEntryRegistry {

    private static final Log log = LogFactory.getLogger(ConfigEntryRegistry.class);

    private final Map<String, ConfigEntry> entryCache = new ConcurrentHashMap<>();
    private final ConfigEntryPersistence persistence;
    private final EcatCore core;

    // ==================== 异常类 ====================

    /**
     * 唯一 ID 重复异常
     */
    public static class DuplicateUniqueIdException extends RuntimeException {
        public DuplicateUniqueIdException(String uniqueId) {
            super("Unique ID already exists: " + uniqueId);
        }
    }

    /**
     * 配置条目不存在异常
     */
    public static class EntryNotFoundException extends RuntimeException {
        public EntryNotFoundException(String entryId) {
            super("ConfigEntry not found: " + entryId);
        }
    }

    /**
     * 配置条目正在使用异常
     */
    public static class EntryInUseException extends RuntimeException {
        public EntryInUseException(String message) {
            super(message);
        }
    }

    /**
     * 集成通知异常（条目已持久化，但通知集成创建/重配置设备失败）
     * <p>
     * 携带完整的 entry 对象，便于后续扩展输出更多信息。
     */
    public static class EntryNotificationException extends RuntimeException {
        private final ConfigEntry entry;
        private final String operation;

        public EntryNotificationException(ConfigEntry entry, String coordinate, String operation, Throwable cause) {
            super(String.format("Integration %s failed for entry %s (%s): %s",
                    operation, entry.getEntryId(), coordinate, cause.getMessage()), cause);
            this.entry = entry;
            this.operation = operation;
        }

        public ConfigEntry getEntry() { return entry; }
        public String getOperation() { return operation; }
    }

    // ==================== 构造函数 ====================

    /**
     * 构造函数
     *
     * @param core        EcatCore 实例
     * @param persistence 持久化实现
     */
    public ConfigEntryRegistry(EcatCore core, ConfigEntryPersistence persistence) {
        this.core = core;
        this.persistence = persistence;
        loadAllEntries();
    }

    /**
     * 兼容旧代码的构造函数（不调用 integration）
     *
     * @param persistence 持久化实现
     * @deprecated 使用 {@link #ConfigEntryRegistry(EcatCore, ConfigEntryPersistence)} 代替
     */
    @Deprecated
    public ConfigEntryRegistry(ConfigEntryPersistence persistence) {
        this.core = null;
        this.persistence = persistence;
        loadAllEntries();
    }

    /**
     * 加载所有配置条目
     */
    private void loadAllEntries() {
        List<ConfigEntry> entries = persistence.loadAll();
        for (ConfigEntry entry : entries) {
            entryCache.put(entry.getEntryId(), entry);
        }
        log.info("Loaded {} config entries", entries.size());
    }

    // ==================== CRUD 方法 ====================

    /**
     * 创建配置条目
     *
     * @param entry 配置条目
     * @return 创建后的配置条目
     * @throws DuplicateUniqueIdException 如果 uniqueId 已存在
     */
    public ConfigEntry createEntry(ConfigEntry entry) {
        // 1. 校验 uniqueId 唯一性
        if (entry.getUniqueId() != null && getByUniqueId(entry.getUniqueId()) != null) {
            throw new DuplicateUniqueIdException(entry.getUniqueId());
        }

        // 2. 生成 entryId
        if (entry.getEntryId() == null) {
            entry.setEntryId(UUID.randomUUID().toString());
        }

        // 3. 设置时间戳
        ZonedDateTime now = DateTimeUtils.now();
        if (entry.getCreateTime() == null) {
            entry.setCreateTime(now);
        }
        entry.setUpdateTime(now);

        // 4. 默认启用
        if (entry.getVersion() == 0) {
            entry.setVersion(1);
        }

        // 5. 持久化
        persistence.save(entry);

        // 6. 更新缓存
        entryCache.put(entry.getEntryId(), entry);

        // 7. 通知 integration
        notifyIntegrationCreate(entry);

        log.info("Created config entry: entryId={}, uniqueId={}",
                entry.getEntryId(), entry.getUniqueId());

        return entry;
    }

    /**
     * 更新配置条目
     *
     * @param entryId     配置条目 ID
     * @param newEntryData 新的配置数据
     * @return 更新后的配置条目
     * @throws EntryNotFoundException 如果 entryId 不存在
     */
    public ConfigEntry updateEntry(String entryId, ConfigEntry newEntryData) {
        ConfigEntry existing = entryCache.get(entryId);
        if (existing == null) {
            throw new EntryNotFoundException(entryId);
        }

        // 保留核心标识字段, 仅更新可变字段
        ConfigEntry updated = existing.withUpdate(newEntryData);

        // 持久化
        persistence.update(updated);

        // 更新缓存
        entryCache.put(entryId, updated);

        log.info("Updated config entry: entryId={}, version {} -> {}",
                entryId, existing.getVersion(), updated.getVersion());

        return updated;
    }

    /**
     * 重新配置条目 (不增加版本号)
     * <p>
     * 用于 reconfigure flow，只更新数据和配置，不改变版本号。
     *
     * @param entryId     配置条目 ID
     * @param newEntryData 新的配置数据
     * @return 更新后的配置条目
     * @throws EntryNotFoundException 如果 entryId 不存在
     */
    public ConfigEntry reconfigureEntry(String entryId, ConfigEntry newEntryData) {
        ConfigEntry existing = entryCache.get(entryId);
        if (existing == null) {
            throw new EntryNotFoundException(entryId);
        }

        // 使用 withReconfigure 保持版本号不变
        ConfigEntry updated = existing.withReconfigure(newEntryData);

        // 持久化
        persistence.update(updated);

        // 更新缓存
        entryCache.put(entryId, updated);

        // 5. 通知 integration
        notifyIntegrationReconfigure(updated);

        log.info("Reconfigured config entry: entryId={}, version {} (preserved)",
                entryId, updated.getVersion());

        return updated;
    }

    /**
     * 删除配置条目
     *
     * @param entryId 配置条目 ID
     * @throws EntryNotFoundException 如果 entryId 不存在
     */
    public void removeEntry(String entryId) {
        ConfigEntry entry = entryCache.get(entryId);
        if (entry == null) {
            throw new EntryNotFoundException(entryId);
        }

        // 1. 先通知 integration 停止设备
        notifyIntegrationRemove(entry);

        // 2. 从缓存移除
        entryCache.remove(entryId);

        // 3. 从持久化删除
        persistence.delete(entryId);

        log.info("Removed config entry: entryId={}", entryId);
    }

    // ==================== 查询方法 ====================

    /**
     * 按 entryId 查询
     *
     * @param entryId 配置条目 ID
     * @return 配置条目，不存在时返回 null
     */
    public ConfigEntry getByEntryId(String entryId) {
        return entryCache.get(entryId);
    }

    /**
     * 按 uniqueId 查询
     *
     * @param uniqueId 唯一标识
     * @return 配置条目，不存在时返回 null
     */
    public ConfigEntry getByUniqueId(String uniqueId) {
        return entryCache.values().stream()
                .filter(e -> uniqueId.equals(e.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 按 coordinate 查询
     *
     * @param coordinate 集成标识
     * @return 配置条目列表
     */
    public List<ConfigEntry> listByCoordinate(String coordinate) {
        return entryCache.values().stream()
                .filter(e -> coordinate.equals(e.getCoordinate()))
                .collect(Collectors.toList());
    }

    /**
     * 检查 coordinate 是否有 entries
     *
     * @param coordinate 集成标识
     * @return 是否有配置条目
     */
    public boolean hasEntries(String coordinate) {
        return entryCache.values().stream()
                .anyMatch(e -> coordinate.equals(e.getCoordinate()));
    }

    /**
     * 列出所有 entries
     *
     * @return 所有配置条目列表
     */
    public List<ConfigEntry> listAll() {
        return new ArrayList<>(entryCache.values());
    }

    /**
     * 启用/禁用 entry
     *
     * @param entryId 配置条目 ID
     * @param enabled 是否启用
     * @return 更新后的配置条目
     * @throws EntryNotFoundException 如果 entryId 不存在
     */
    public ConfigEntry setEnabled(String entryId, boolean enabled) {
        ConfigEntry entry = getByEntryId(entryId);
        if (entry == null) {
            throw new EntryNotFoundException(entryId);
        }

        // 状态未变化时跳过通知
        if (entry.isEnabled() == enabled) {
            log.debug("Config entry enabled state unchanged: entryId={}, enabled={}", entryId, enabled);
            return entry;
        }

        // 先通知集成（禁用：停止设备；启用：从配置重建设备）
        if (enabled) {
            notifyIntegrationEnable(entry);
        } else {
            notifyIntegrationDisable(entry);
        }

        ConfigEntry updated = new ConfigEntry.Builder()
                .entryId(entry.getEntryId())
                .coordinate(entry.getCoordinate())
                .uniqueId(entry.getUniqueId())
                .title(entry.getTitle())
                .data(entry.getData())
                .enabled(enabled)
                .createTime(entry.getCreateTime())
                .updateTime(DateTimeUtils.now())
                .version(entry.getVersion()) // 启用/禁用不增加版本号
                .build();

        persistence.update(updated);
        entryCache.put(entryId, updated);

        log.info("Set config entry enabled: entryId={}, enabled={}", entryId, enabled);

        return updated;
    }

    // ==================== Integration 通知方法 ====================

    /**
     * 通知 integration 创建 entry
     */
    private void notifyIntegrationCreate(ConfigEntry entry) {
        if (core == null) return;

        String coordinate = entry.getCoordinate();
        IntegrationRegistry integrationRegistry = core.getIntegrationRegistry();
        if (integrationRegistry == null) return;

        IntegrationBase integration = (IntegrationBase) integrationRegistry.getIntegration(coordinate);
        if (integration == null) {
            log.debug("Integration not found for coordinate: {}", coordinate);
            return;
        }

        try {
            integration.createEntry(entry);
            log.debug("Notified integration {} to create entry {}", coordinate, entry.getEntryId());
        } catch (UnsupportedOperationException e) {
            log.warn("Integration {} doesn't support ConfigEntry: {}", coordinate, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to notify integration {} to create entry {}: {}",
                    coordinate, entry.getEntryId(), e.getMessage(), e);
            throw new EntryNotificationException(entry, coordinate, "create", e);
        }
    }

    /**
     * 通知 integration 重新配置 entry
     */
    private void notifyIntegrationReconfigure(ConfigEntry entry) {
        if (core == null) return;

        String coordinate = entry.getCoordinate();
        IntegrationRegistry integrationRegistry = core.getIntegrationRegistry();
        if (integrationRegistry == null) return;

        IntegrationBase integration = (IntegrationBase) integrationRegistry.getIntegration(coordinate);
        if (integration == null) {
            log.debug("Integration not found for coordinate: {}", coordinate);
            return;
        }

        try {
            integration.reconfigureEntry(entry.getEntryId(), entry);
            log.debug("Notified integration {} to reconfigure entry {}", coordinate, entry.getEntryId());
        } catch (UnsupportedOperationException e) {
            log.warn("Integration {} doesn't support ConfigEntry: {}", coordinate, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to notify integration {} to reconfigure entry {}: {}",
                    coordinate, entry.getEntryId(), e.getMessage(), e);
            throw new EntryNotificationException(entry, coordinate, "reconfigure", e);
        }
    }

    /**
     * 通知 integration 删除 entry
     */
    private void notifyIntegrationRemove(ConfigEntry entry) {
        if (core == null) return;

        String coordinate = entry.getCoordinate();
        IntegrationRegistry integrationRegistry = core.getIntegrationRegistry();
        if (integrationRegistry == null) return;

        IntegrationBase integration = (IntegrationBase) integrationRegistry.getIntegration(coordinate);
        if (integration == null) {
            log.debug("Integration not found for coordinate: {}", coordinate);
            return;
        }

        try {
            integration.removeEntry(entry.getEntryId());
            log.debug("Notified integration {} to remove entry {}", coordinate, entry.getEntryId());
        } catch (UnsupportedOperationException e) {
            log.warn("Integration {} doesn't support ConfigEntry: {}", coordinate, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to notify integration {} to remove entry {}: {}",
                    coordinate, entry.getEntryId(), e.getMessage());
        }
    }

    /**
     * 通知 integration 禁用 entry
     */
    private void notifyIntegrationDisable(ConfigEntry entry) {
        if (core == null) return;

        String coordinate = entry.getCoordinate();
        IntegrationRegistry integrationRegistry = core.getIntegrationRegistry();
        if (integrationRegistry == null) return;

        IntegrationBase integration = (IntegrationBase) integrationRegistry.getIntegration(coordinate);
        if (integration == null) {
            log.debug("Integration not found for coordinate: {}", coordinate);
            return;
        }

        try {
            integration.disableEntry(entry.getEntryId());
            log.debug("Notified integration {} to disable entry {}", coordinate, entry.getEntryId());
        } catch (UnsupportedOperationException e) {
            log.warn("Integration {} doesn't support ConfigEntry: {}", coordinate, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to notify integration {} to disable entry {}: {}",
                    coordinate, entry.getEntryId(), e.getMessage());
        }
    }

    /**
     * 通知 integration 启用 entry
     */
    private void notifyIntegrationEnable(ConfigEntry entry) {
        if (core == null) return;

        String coordinate = entry.getCoordinate();
        IntegrationRegistry integrationRegistry = core.getIntegrationRegistry();
        if (integrationRegistry == null) return;

        IntegrationBase integration = (IntegrationBase) integrationRegistry.getIntegration(coordinate);
        if (integration == null) {
            log.debug("Integration not found for coordinate: {}", coordinate);
            return;
        }

        try {
            integration.enableEntry(entry);
            log.debug("Notified integration {} to enable entry {}", coordinate, entry.getEntryId());
        } catch (UnsupportedOperationException e) {
            log.warn("Integration {} doesn't support ConfigEntry: {}", coordinate, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to notify integration {} to enable entry {}: {}",
                    coordinate, entry.getEntryId(), e.getMessage());
        }
    }
}
