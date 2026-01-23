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

import com.ecat.core.Config.YamlConfigUtils;
import com.ecat.core.Repository.CloudRepositoryClient;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 集成安装器 - 处理集成安装、卸载、启用、禁用等操作
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>安装集成（含依赖解析）</li>
 *   <li>卸载集成</li>
 *   <li>启用/禁用集成</li>
 *   <li>列出已安装集成</li>
 *   <li>触发 ecat-core 重载</li>
 * </ul>
 *
 * @author coffee
 * @version 1.0.0
 */
public class IntegrationInstaller {

    private final CloudRepositoryClient cloudClient;
    private final Path integrationsConfigPath;
    private final Yaml yaml;

    /**
     * 构造函数
     *
     * @param cloudClient 云端仓库客户端
     */
    public IntegrationInstaller(CloudRepositoryClient cloudClient) {
        this.cloudClient = cloudClient;
        // 配置文件路径：~/.ecat-core/.ecat-data/core/integrations.yml
        // core 从运行目录（~/.ecat-core/）查找 .ecat-data 目录
        this.integrationsConfigPath = Paths.get(
            System.getProperty("user.home"),
            ".ecat-core/.ecat-data/core/integrations.yml"
        );
        this.yaml = new Yaml();
    }

    /**
     * 安装集成（含依赖解析）
     *
     * @param coordinate Maven 坐标格式 "groupId:artifactId" 或仅 "artifactId"
     * @param version 版本号，null 表示最新版本
     * @throws InstallationException 安装失败
     */
    public void install(String coordinate, String version) throws InstallationException {
        try {
            // 1. 获取包信息（支持 artifactId 或 groupId:artifactId 格式）
            CloudRepositoryClient.PackageInfo packageInfo = cloudClient.getPackageInfo(coordinate);
            String actualArtifactId = packageInfo.getArtifactId();
            String groupId = packageInfo.getGroupId();

            // 2. 检查是否已安装（使用 groupId:artifactId 作为 key）
            Map<String, Map<String, Object>> config = loadConfig();
            String key = groupId + ":" + actualArtifactId;
            if (config.containsKey(key)) {
                throw new InstallationException("集成 " + key + " 已安装");
            }

            // 3. 确定版本
            if (version == null || version.isEmpty()) {
                version = packageInfo.getLatestVersion();
                if (version == null) {
                    throw new InstallationException("未找到可用版本");
                }
            }

            // 4. 解析并安装依赖（递归）
            installDependencies(coordinate, version, packageInfo, new HashSet<>());

            // 5. 下载主包（使用完整坐标）
            Path jarPath = cloudClient.downloadPackage(coordinate, version);
            System.out.println("已下载: " + jarPath);

            // 6. 更新 integrations.yml（使用 groupId:artifactId 作为 key）
            // className 不再写入配置，由 core 运行时扫描 JAR 获取
            updateConfig(groupId, actualArtifactId, version, true);

            System.out.println("安装成功: " + key + " " + version);

        } catch (IOException e) {
            throw new InstallationException("安装失败: " + e.getMessage(), e);
        }
    }

    /**
     * 卸载集成
     *
     * @param coordinate Maven 坐标格式 "groupId:artifactId" 或仅 "artifactId"
     * @throws RemovalException 卸载失败
     */
    public void remove(String coordinate) throws RemovalException {
        try {
            // 解析坐标
            String key = parseCoordinateToKey(coordinate);

            Map<String, Map<String, Object>> config = loadConfig();
            if (!config.containsKey(key)) {
                throw new RemovalException("集成 " + key + " 未安装");
            }

            // 1. 检查依赖关系（是否有其他集成依赖它）
            List<String> dependents = findDependents(key, config);
            if (!dependents.isEmpty()) {
                throw new RemovalException("无法卸载，以下集成依赖它: " + dependents);
            }

            // 2. 从 integrations.yml 移除
            config.remove(key);
            saveConfig(config);

            System.out.println("卸载成功: " + key);

        } catch (IOException e) {
            throw new RemovalException("卸载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将坐标解析为配置 key
     *
     * @param coordinate Maven 坐标格式 "groupId:artifactId" 或仅 "artifactId"
     * @return 配置 key（groupId:artifactId）
     */
    private String parseCoordinateToKey(String coordinate) throws IOException {
        // 如果已包含 ":"，则认为是完整坐标
        if (coordinate.contains(":")) {
            return coordinate;
        }

        // 仅 artifactId：需要搜索获取 groupId
        CloudRepositoryClient.PackageInfo packageInfo = cloudClient.getPackageInfo(coordinate);
        return packageInfo.getGroupId() + ":" + coordinate;
    }

    /**
     * 启用集成
     *
     * @param coordinate Maven 坐标格式 "groupId:artifactId" 或仅 "artifactId"
     * @throws IOException 操作失败
     */
    public void enable(String coordinate) throws IOException {
        String key = parseCoordinateToKey(coordinate);
        setEnabled(key, true);
        System.out.println("已启用: " + key);
        System.out.println("运行 'ecat core reload' 使配置生效");
    }

    /**
     * 禁用集成
     *
     * @param coordinate Maven 坐标格式 "groupId:artifactId" 或仅 "artifactId"
     * @throws IOException 操作失败
     */
    public void disable(String coordinate) throws IOException {
        String key = parseCoordinateToKey(coordinate);
        setEnabled(key, false);
        System.out.println("已禁用: " + key);
        System.out.println("运行 'ecat core reload' 使配置生效");
    }

    /**
     * 列出已安装集成
     *
     * @return 已安装集成列表
     */
    public List<InstalledIntegration> listInstalled() {
        try {
            Map<String, Map<String, Object>> config = loadConfig();
            List<InstalledIntegration> result = new ArrayList<>();

            for (Map.Entry<String, Map<String, Object>> entry : config.entrySet()) {
                String key = entry.getKey();
                Map<String, Object> value = entry.getValue();
                // key 现在是 groupId:artifactId 格式
                result.add(new InstalledIntegration(
                    key,
                    (Boolean) value.get("enabled"),
                    (String) value.get("artifactId"),
                    (String) value.get("version")
                ));
            }

            return result;

        } catch (IOException e) {
            System.err.println("读取配置失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 进程查找工具类（纯 Java 实现）
     * 使用 /proc 文件系统查找进程，不依赖外部命令
     */
    private static class ProcessFinder {

        /**
         * 查找指定名称的进程
         * @param processName 进程名称（如 "EcatCore"）
         * @return 如果找到进程返回进程 PID，否则返回 null
         */
        static String findProcess(String processName) {
            // 使用 /proc 文件系统查找进程
            try {
                File procDir = new File("/proc");
                if (!procDir.exists()) {
                    return null;
                }

                File[] pidDirs = procDir.listFiles(File::isDirectory);
                if (pidDirs == null) return null;

                for (File pidDir : pidDirs) {
                    try {
                        int pid = Integer.parseInt(pidDir.getName());
                        File cmdlineFile = new File(pidDir, "cmdline");
                        if (cmdlineFile.exists()) {
                            String cmdline = new String(
                                Files.readAllBytes(cmdlineFile.toPath())
                            ).replace("\0", " ");

                            // 查找 EcatCore 进程
                            if (cmdline.contains(processName)) {
                                return String.valueOf(pid);
                            }
                        }
                    } catch (NumberFormatException ignored) {
                        // 忽略非数字目录名
                    }
                }
            } catch (IOException e) {
                // 读取 /proc 失败
            }

            return null;
        }
    }

    /**
     * 触发 ecat-core 重载
     *
     * @throws ReloadException 重载失败
     */
    public void reloadCore() throws ReloadException {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            WindowsReloader.reload();
        } else {
            // Linux/macOS
            UnixReloader.reload();
        }
    }

    // ==================== 平台相关重载器 ====================

    /**
     * Unix 平台重载器（Linux/macOS）
     */
    private static class UnixReloader {
        static void reload() throws ReloadException {
            // 1. 查找进程
            String pid = ProcessFinder.findProcess("EcatCore");

            if (pid == null || pid.isEmpty()) {
                throw new ReloadException(
                    "未找到运行中的 ecat-core 进程\n请先启动 core: ecat core start"
                );
            }

            // 2. 发送 SIGHUP 信号重载配置
            try {
                Process process = Runtime.getRuntime().exec(
                    new String[]{"kill", "-HUP", pid}
                );
                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    throw new ReloadException("发送 SIGHUP 信号失败");
                }

                System.out.println("已发送重载信号到 ecat-core (PID: " + pid + ")");

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ReloadException("重载失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Windows 平台重载器（预留扩展点）
     *
     * <p>后期添加 Windows 支持时，只需实现此方法。
     * 不需要修改 UnixReloader 或 reloadCore() 的代码。</p>
     *
     * <p>可能的实现方案：</p>
     * <ul>
     *   <li>命名管道通信</li>
     *   <li>HTTP 端点触发</li>
     *   <li>文件触发（监视特定文件）</li>
     *   <li>JMX/MBean</li>
     * </ul>
     */
    private static class WindowsReloader {
        static void reload() throws ReloadException {
            throw new ReloadException(
                "===========================================\n" +
                "  Windows 系统暂不支持自动重载\n" +
                "===========================================\n\n" +
                "请手动执行以下步骤：\n" +
                "  1. 按 Ctrl+Shift+Esc 打开任务管理器\n" +
                "  2. 找到 java 进程，右键选择\"结束任务\"\n" +
                "  3. 重新启动 EcatCore\n\n" +
                "我们计划在后续版本中支持 Windows 自动重载。\n" +
                "==========================================="
            );
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 递归安装依赖
     */
    private void installDependencies(String coordinate, String version,
                                      CloudRepositoryClient.PackageInfo packageInfo,
                                      Set<String> installing) throws InstallationException, IOException {
        String artifactId = packageInfo.getArtifactId();

        if (installing.contains(artifactId)) {
            throw new InstallationException("检测到循环依赖: " + artifactId);
        }

        installing.add(artifactId);

        Map<String, List<CloudRepositoryClient.DependencyItem>> dependencies = packageInfo.getDependencies();
        if (dependencies != null && dependencies.containsKey(version)) {
            List<CloudRepositoryClient.DependencyItem> deps = dependencies.get(version);
            for (CloudRepositoryClient.DependencyItem dep : deps) {
                String depCoordinate = dep.getCoordinate(); // groupId:artifactId
                String depVersion = dep.getVersion(); // 可能为 null、版本约束或具体版本

                // 检查依赖是否已安装（使用完整的坐标作为 key）
                Map<String, Map<String, Object>> config = loadConfig();
                if (!config.containsKey(depCoordinate)) {
                    System.out.println("安装依赖: " + depCoordinate);
                    // 如果版本是约束（如 *、>=1.0.0 等），解析为具体版本
                    if (depVersion == null || depVersion.equals("*") || depVersion.matches("^[<>~=^]")) {
                        install(depCoordinate, null); // 传入 null 让 install() 使用最新版本
                    } else {
                        install(depCoordinate, depVersion);
                    }
                }
            }
        }

        installing.remove(artifactId);
    }

    /**
     * 更新配置文件
     *
     * @param groupId groupId
     * @param artifactId artifactId
     * @param version 版本号
     * @param enabled 是否启用
     */
    private void updateConfig(String groupId, String artifactId, String version, boolean enabled) throws IOException {
        Map<String, Map<String, Object>> config = loadConfig();

        Map<String, Object> integrationConfig = new HashMap<>();
        integrationConfig.put("enabled", enabled);
        integrationConfig.put("groupId", groupId);
        integrationConfig.put("artifactId", artifactId);
        integrationConfig.put("version", version);

        // 使用 groupId:artifactId 作为 key
        String key = groupId + ":" + artifactId;
        config.put(key, integrationConfig);

        saveConfig(config);
    }

    /**
     * 设置集成启用状态
     *
     * @param key 配置 key（groupId:artifactId）
     * @param enabled 是否启用
     */
    private void setEnabled(String key, boolean enabled) throws IOException {
        Map<String, Map<String, Object>> config = loadConfig();

        if (!config.containsKey(key)) {
            throw new IllegalArgumentException("未找到集成: " + key);
        }

        config.get(key).put("enabled", enabled);
        saveConfig(config);
    }

    /**
     * 查找依赖此集成的其他集成
     */
    private List<String> findDependents(String artifactId, Map<String, Map<String, Object>> config) {
        List<String> dependents = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : config.entrySet()) {
            if ((Boolean) entry.getValue().get("enabled")) {
                // 实际需要读取依赖的 JAR 配置来检查依赖关系
                // 这里简化处理
            }
        }

        return dependents;
    }

    /**
     * 加载配置文件
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadConfig() throws IOException {
        if (!Files.exists(integrationsConfigPath)) {
            return new HashMap<>();
        }
        try (InputStream in = Files.newInputStream(integrationsConfigPath)) {
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                return new HashMap<>();
            }
            Map<String, Map<String, Object>> integrations = (Map<String, Map<String, Object>>) root.get("integrations");
            return integrations != null ? integrations : new HashMap<>();
        }
    }

    /**
     * 加载完整配置（包括 core 和 integrations）
     */
    private Map<String, Object> loadFullConfig() throws IOException {
        if (!Files.exists(integrationsConfigPath)) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("integrations", new HashMap<String, Object>());
            return empty;
        }
        try (InputStream in = Files.newInputStream(integrationsConfigPath)) {
            Map<String, Object> config = yaml.load(in);
            return config != null ? config : new HashMap<>();
        }
    }

    /**
     * 保存配置文件（使用标准 YAML 格式）
     */
    @SuppressWarnings("unchecked")
    private void saveConfig(Map<String, Map<String, Object>> config) throws IOException {
        // 读取现有配置以获取 core 信息
        Map<String, Object> root = loadFullConfig();
        Map<String, Object> coreConfig = (Map<String, Object>) root.get("core");

        // 使用工具类保存配置
        YamlConfigUtils.saveIntegrationConfig(
            integrationsConfigPath,
            com.ecat.core.ConfigVersion.getVersion(),
            config,
            coreConfig
        );
    }

    // ==================== 内部数据类 ====================

    /**
     * 已安装集成
     */
    public static class InstalledIntegration {
        private final String key;        // groupId:artifactId
        private final boolean enabled;
        private final String artifactId;
        private final String version;

        public InstalledIntegration(String key, boolean enabled, String artifactId, String version) {
            this.key = key;
            this.enabled = enabled;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String getKey() { return key; }
        public boolean isEnabled() { return enabled; }
        public String getArtifactId() { return artifactId; }
        public String getVersion() { return version; }

        /**
         * 获取 className
         * className 现在由 core 运行时扫描 JAR 自动获取
         */
        public String getClassName() { return key; }
    }

    // ==================== 异常类 ====================

    /**
     * 安装异常
     */
    public static class InstallationException extends Exception {
        public InstallationException(String message) {
            super(message);
        }

        public InstallationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 卸载异常
     */
    public static class RemovalException extends Exception {
        public RemovalException(String message) {
            super(message);
        }

        public RemovalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 重载异常
     */
    public static class ReloadException extends Exception {
        public ReloadException(String message) {
            super(message);
        }

        public ReloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
