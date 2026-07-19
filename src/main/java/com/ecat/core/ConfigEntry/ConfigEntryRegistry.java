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
import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.ConfigEntryEvent;
import com.ecat.core.Bus.event.EventContext;
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
        private final String uniqueId;

        public DuplicateUniqueIdException(String uniqueId) {
            super("Unique ID already exists: " + uniqueId);
            this.uniqueId = uniqueId;
        }

        /** 重复命中的 uniqueId（供调用方区分 ALREADY_CONFIGURED vs ALREADY_IN_PROGRESS）。 */
        public String getUniqueId() {
            return uniqueId;
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
        if (entry.getUniqueId() != null && getByUniqueId(entry.getCoordinate(), entry.getUniqueId()) != null) {
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

        publishConfigEntryEvent(entry, ConfigEntryEvent.Action.CREATE);

        log.info("Created config entry: entryId={}, uniqueId={}",
                entry.getEntryId(), entry.getUniqueId());

        return entry;
    }

    // ==================== IGNORE / UNIGNORE（req2 抑制 / req3 召回）====================

    /**
     * req2 抑制：创建 source=IGNORE 的 ConfigEntry。
     * <p>建立后，同 uniqueId 的后续发现经 R5（uniqueId 去重）静默 abort——复用 ConfigEntry，零新存储。
     * IGNORE entry 不触发设备加载（{@link #notifyIntegrationCreate} 跳过控制源 entry）。
     *
     * @param coordinate 目标集成标识（被忽略设备的归属集成）
     * @param uniqueId   被忽略设备的业务唯一标识
     * @param title      标题（可空，缺省 ignored:&lt;uniqueId&gt;）
     * @return 创建的 IGNORE entry
     */
    public ConfigEntry createIgnoreEntry(String coordinate, String uniqueId, String title) {
        ConfigEntry ignore = new ConfigEntry.Builder()
                .coordinate(coordinate)
                .uniqueId(uniqueId)
                .title(title != null && !title.isEmpty() ? title : ("ignored:" + uniqueId))
                .source(SourceType.IGNORE)
                .build();
        return createEntry(ignore);
    }

    /**
     * 查询某 uniqueId 是否处于 IGNORE 状态（存在 source=IGNORE 的 entry）。
     *
     * @return IGNORE entry；不存在或非 IGNORE 则 null
     */
    public ConfigEntry getIgnoreEntry(String coordinate, String uniqueId) {
        ConfigEntry e = getByUniqueId(coordinate, uniqueId);
        return (e != null && e.getSource() == SourceType.IGNORE) ? e : null;
    }

    /**
     * req3 召回：删除 IGNORE entry（使后续发现不再被 R5 拦截，下次广播可重新发现）。
     * <p>仅删除 source=IGNORE 的 entry；非 IGNORE entry（已配置的真实设备）不动，避免误删真实配置。
     *
     * @return 是否删除了 IGNORE entry
     */
    public boolean removeIgnoreEntry(String coordinate, String uniqueId) {
        ConfigEntry e = getByUniqueId(coordinate, uniqueId);
        if (e != null && e.getSource() == SourceType.IGNORE) {
            removeEntry(e.getEntryId());
            return true;
        }
        return false;
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

        publishConfigEntryEvent(updated, ConfigEntryEvent.Action.RECONFIGURE);

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

        publishConfigEntryEvent(entry, ConfigEntryEvent.Action.REMOVE);

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
     * 按 coordinate 域化查找（uniqueId 仅在 coordinate 内唯一，故查询须带 coordinate）。
     *
     * @param coordinate 集成标识（域）
     * @param uniqueId   唯一标识
     * @return 配置条目，不存在时返回 null
     */
    public ConfigEntry getByUniqueId(String coordinate, String uniqueId) {
        if (coordinate == null || uniqueId == null) {
            return null;
        }
        return entryCache.values().stream()
                .filter(e -> coordinate.equals(e.getCoordinate()) && uniqueId.equals(e.getUniqueId()))
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

        publishConfigEntryEvent(entry, enabled ? ConfigEntryEvent.Action.ENABLE : ConfigEntryEvent.Action.DISABLE);

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

        // 控制源 entry（IGNORE/UNIGNORE）不触发设备加载——它们是 core 管理的抑制/召回标记，无对应设备
        if (entry.getSource() == SourceType.IGNORE || entry.getSource() == SourceType.UNIGNORE) {
            log.debug("Skip device-load notify for control-source entry: source={}, uniqueId={}",
                    entry.getSource(), entry.getUniqueId());
            return;
        }

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
        } catch (EntryInUseException e) {
            // 集成否决删除(如通讯模块仍被具象设备引用)——必须向上抛出,中断 removeEntry 后续步骤
            // (缓存移除 + 持久化删除),否则被引用的 entry 会删成孤儿。其它异常仍按"清理尽力"语义吞掉。
            throw e;
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

    // ==================== ConfigEntry 生命周期事件发布 ====================

    /**
     * 发布 ConfigEntry 生命周期事件到 BusRegistry。
     *
     * <p><b>事件质量等级：</b>此事件在目标集成通知完成之后发布。
     * 如果目标集成处理失败（抛出异常），根据 notifyIntegration* 的异常处理策略：
     * <ul>
     *   <li>create/reconfigure：异常会向上抛出，不会执行到此事件发布</li>
     *   <li>remove/enable/disable：异常仅被记录，事件仍然会发布</li>
     * </ul>
     * 监听方需要理解：收到事件不保证目标集成的操作一定成功完成。
     */
    private void publishConfigEntryEvent(ConfigEntry entry, ConfigEntryEvent.Action action) {
        if (core == null) return;
        if (core.getBusRegistry() == null) return;

        ConfigEntryEvent event = new ConfigEntryEvent(
            entry.getEntryId(), entry.getCoordinate(), action);
        core.getBusRegistry().publish(BusEvent.of(
            BusTopic.CONFIG_ENTRY_LIFECYCLE.getTopicName(), event,
            EventContext.root(EventContext.Source.SYSTEM, null)));

        log.debug("Published config entry lifecycle event: action={}, entryId={}, coordinate={}",
            action, entry.getEntryId(), entry.getCoordinate());
    }
}
