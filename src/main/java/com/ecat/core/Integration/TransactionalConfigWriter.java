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

package com.ecat.core.Integration;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * 事务性配置写入器
 * <p>
 * 确保配置写入的原子性：全部成功或全部回滚。
 * </p>
 *
 * <h3>工作流程：</h3>
 * <ol>
 *   <li>备份当前配置</li>
 *   <li>合并新配置到现有配置</li>
 *   <li>写入临时文件</li>
 *   <li>验证新配置</li>
 *   <li>原子性替换（rename）</li>
 *   <li>失败时自动回滚</li>
 * </ol>
 *
 * @author coffee
 */
public class TransactionalConfigWriter {

    private final Path configPath;
    private final Path backupPath;
    private final Path tempPath;

    /**
     * 创建事务性配置写入器
     *
     * @param configPath 配置文件路径
     */
    public TransactionalConfigWriter(Path configPath) {
        this.configPath = configPath;
        this.backupPath = Paths.get(configPath + ".backup");
        this.tempPath = Paths.get(configPath + ".tmp");
    }

    /**
     * 原子性写入配置
     *
     * @param newPackages 新增的包配置
     * @param replace 是否替换整个配置（false 则合并）
     * @throws IOException 写入失败时自动回滚
     */
    @SuppressWarnings("unchecked")
    public void writeAtomic(Map<String, Map<String, Object>> newPackages, boolean replace)
            throws IOException {

        // 1. 备份当前配置
        backup();

        try {
            // 2. 读取现有配置
            Map<String, Object> existingConfig = readConfig();
            Map<String, Map<String, Object>> existingIntegrations =
                (Map<String, Map<String, Object>>) existingConfig.getOrDefault("integrations", new java.util.HashMap<>());

            // 3. 合并配置
            Map<String, Object> mergedConfig;
            if (replace) {
                // 替换模式：使用新配置
                Map<String, Object> root = new java.util.HashMap<>();
                root.put("version", com.ecat.core.ConfigVersion.getVersion());
                root.put("integrations", newPackages);
                mergedConfig = root;
            } else {
                // 合并模式：将新包添加到现有配置
                Map<String, Map<String, Object>> mergedIntegrations =
                    new java.util.HashMap<>(existingIntegrations);
                mergedIntegrations.putAll(newPackages);

                Map<String, Object> root = new java.util.HashMap<>();
                root.put("version", com.ecat.core.ConfigVersion.getVersion());
                root.put("integrations", mergedIntegrations);
                mergedConfig = root;
            }

            // 4. 写入临时文件
            writeTemp(mergedConfig);

            // 5. 验证新配置
            validate(tempPath);

            // 6. 原子性替换
            atomicReplace();

            // 7. 删除备份
            deleteBackup();

        } catch (Exception e) {
            // 8. 回滚
            rollback();
            throw new IOException("配置写入失败，已回滚: " + e.getMessage(), e);
        }
    }

    /**
     * 备份当前配置
     */
    private void backup() throws IOException {
        if (Files.exists(configPath)) {
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 读取现有配置
     */
    private Map<String, Object> readConfig() throws IOException {
        if (!Files.exists(configPath)) {
            // 返回空配置
            Map<String, Object> empty = new java.util.HashMap<>();
            empty.put("version", com.ecat.core.ConfigVersion.getVersion());
            empty.put("integrations", new java.util.HashMap<>());
            return empty;
        }

        Yaml yaml = new Yaml();
        try (java.io.InputStream in = Files.newInputStream(configPath)) {
            return yaml.load(in);
        }
    }

    /**
     * 写入临时文件
     */
    private void writeTemp(Map<String, Object> config) throws IOException {
        Yaml yaml = new Yaml();
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        yaml = new Yaml(options);

        try (BufferedWriter writer = Files.newBufferedWriter(tempPath)) {
            yaml.dump(config, writer);
        }
    }

    /**
     * 验证新配置
     */
    private void validate(Path path) throws IOException {
        // 尝试解析 YAML
        Yaml yaml = new Yaml();
        try (java.io.InputStream in = Files.newInputStream(path)) {
            Map<String, Object> config = yaml.load(in);

            // 基本验证：检查必要字段
            if (!config.containsKey("integrations")) {
                throw new IOException("配置缺少 'integrations' 字段");
            }

            // 验证版本号
            String version = (String) config.get("version");
            if (version == null) {
                throw new IOException("配置缺少 'version' 字段");
            }
        }
    }

    /**
     * 原子性替换（使用 rename 操作）
     */
    private void atomicReplace() throws IOException {
        Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING,
                   StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * 删除备份
     */
    private void deleteBackup() throws IOException {
        if (Files.exists(backupPath)) {
            Files.delete(backupPath);
        }
    }

    /**
     * 回滚：从备份恢复
     */
    private void rollback() throws IOException {
        try {
            // 删除临时文件
            if (Files.exists(tempPath)) {
                Files.delete(tempPath);
            }

            // 从备份恢复
            if (Files.exists(backupPath)) {
                Files.move(backupPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // 回滚失败，记录错误
            System.err.println("警告：配置回滚失败: " + e.getMessage());
        }
    }

    /**
     * 获取配置路径
     */
    public Path getConfigPath() {
        return configPath;
    }

    /**
     * 获取备份路径
     */
    public Path getBackupPath() {
        return backupPath;
    }

    /**
     * 检查是否存在备份
     */
    public boolean hasBackup() {
        return Files.exists(backupPath);
    }
}
