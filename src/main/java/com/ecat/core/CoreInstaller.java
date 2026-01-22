package com.ecat.core;

import com.ecat.core.Config.YamlConfigUtils;
import com.ecat.core.Repository.CloudRepositoryClient;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Core 安装器 - 处理 ecat-core 的安装、升级和版本管理
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>安装 ecat-core 到 ~/.ecat-core/</li>
 *   <li>升级 ecat-core</li>
 *   <li>管理 core 版本信息</li>
 *   <li>创建启动脚本</li>
 * </ul>
 *
 * @author coffee
 * @version 1.0.0
 */
public class CoreInstaller {

    private final CloudRepositoryClient cloudClient;
    private final Path coreInstallDir;
    private final Path configPath;
    private final Yaml yaml;

    /**
     * 构造函数
     *
     * @param cloudClient 云端仓库客户端
     */
    public CoreInstaller(CloudRepositoryClient cloudClient) {
        this.cloudClient = cloudClient;
        this.coreInstallDir = Paths.get(System.getProperty("user.home"), ".ecat-core");
        // 配置文件在 ~/.ecat-core/.ecat-data/core/integrations.yml
        // core 从运行目录（~/.ecat-core/）查找 .ecat-data 目录
        this.configPath = coreInstallDir.resolve(".ecat-data/core/integrations.yml");
        this.yaml = new Yaml();
    }

    /**
     * 安装 core
     *
     * @param coordinate Maven 坐标格式 "groupId:artifactId" 或仅 "artifactId"
     * @param version 版本号
     * @param force 是否强制重新安装
     * @return 安装的 JAR 文件路径
     * @throws IOException 安装失败
     */
    public Path install(String coordinate, String version, boolean force) throws IOException {
        // 1. 下载 JAR
        Path jarPath = cloudClient.downloadPackage(coordinate, version);

        // 2. 创建目录
        Files.createDirectories(coreInstallDir);
        Files.createDirectories(coreInstallDir.resolve(".ecat-data/core"));

        // 3. 复制 JAR 到 ~/.ecat-core/ecat-core-{version}.jar
        String filename = "ecat-core-" + version + ".jar";
        Path targetJar = coreInstallDir.resolve(filename);
        if (force || !Files.exists(targetJar)) {
            Files.copy(jarPath, targetJar, StandardCopyOption.REPLACE_EXISTING);
        }

        // 4. 创建当前版本的符号链接
        Path currentLink = coreInstallDir.resolve("ecat-core.jar");
        if (Files.exists(currentLink)) {
            Files.delete(currentLink);
        }
        Files.createSymbolicLink(currentLink, targetJar);

        // 5. 创建启动脚本
        createLaunchScript();

        return targetJar;
    }

    /**
     * 更新 core 版本信息到配置文件
     *
     * @param groupId groupId
     * @param artifactId artifactId
     * @param version 版本号
     * @throws IOException 更新失败
     */
    public void updateCoreVersion(String groupId, String artifactId, String version) throws IOException {
        Map<String, Object> config = loadConfig();

        // 更新 core 字段
        Map<String, Object> coreConfig = new HashMap<>();
        coreConfig.put("groupId", groupId);
        coreConfig.put("artifactId", artifactId);
        coreConfig.put("version", version);

        config.put("core", coreConfig);

        // 添加配置文件版本号，与 CLI 版本保持同步
        config.put("version", com.ecat.core.ConfigVersion.getVersion());

        // 确保 integrations 字段存在
        if (!config.containsKey("integrations")) {
            config.put("integrations", new HashMap<String, Object>());
        }

        saveConfig(config);
    }

    /**
     * 获取当前 core 版本
     *
     * @return 当前版本，如果未安装则返回 null
     * @throws IOException 读取失败
     */
    @SuppressWarnings("unchecked")
    public String getCurrentVersion() throws IOException {
        if (!Files.exists(configPath)) {
            return null;
        }

        Map<String, Object> config = loadConfig();
        Map<String, Object> coreConfig = (Map<String, Object>) config.get("core");
        return coreConfig != null ? (String) coreConfig.get("version") : null;
    }

    /**
     * 创建 core 启动脚本
     *
     * @throws IOException 创建失败
     */
    private void createLaunchScript() throws IOException {
        Path scriptPath = coreInstallDir.resolve("ecat-core");
        try (java.io.BufferedWriter writer = Files.newBufferedWriter(scriptPath)) {
            writer.write("#!/bin/bash\n");
            writer.write("# ECAT Core 启动脚本\n");
            writer.write("cd \"$(dirname \"$0\")\" || exit 1\n");
            writer.write("java -jar ecat-core.jar \"$@\"\n");
        }
        scriptPath.toFile().setExecutable(true);
    }

    /**
     * 加载配置文件
     *
     * @return 配置
     * @throws IOException 读取失败
     */
    private Map<String, Object> loadConfig() throws IOException {
        if (!Files.exists(configPath)) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("integrations", new HashMap<String, Object>());
            return empty;
        }
        try (InputStream in = Files.newInputStream(configPath)) {
            Map<String, Object> config = yaml.load(in);
            return config != null ? config : new HashMap<>();
        }
    }

    /**
     * 保存配置文件（使用标准 YAML 格式）
     *
     * @param config 配置
     * @throws IOException 保存失败
     */
    private void saveConfig(Map<String, Object> config) throws IOException {
        Files.createDirectories(configPath.getParent());

        // 获取各部分配置
        String version = config.containsKey("version") ? (String) config.get("version") : com.ecat.core.ConfigVersion.getVersion();
        Map<String, Map<String, Object>> integrations = (Map<String, Map<String, Object>>) config.get("integrations");
        Map<String, Object> coreConfig = (Map<String, Object>) config.get("core");

        // 使用工具类保存配置
        YamlConfigUtils.saveIntegrationConfig(configPath, version, integrations, coreConfig);
    }
}
