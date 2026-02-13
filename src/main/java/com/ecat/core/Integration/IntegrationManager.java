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

import com.ecat.core.EcatCore;
import com.ecat.core.State.StateManager;
import com.ecat.core.Utils.LoadJarUtils;
import com.ecat.core.Utils.MavenDependencyParser;
import com.ecat.core.Utils.CustomClassLoader;
import com.ecat.core.Utils.JarDependencyLoader;
import com.ecat.core.Utils.JarScanException;
import com.ecat.core.Utils.LoadJarResult;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.Mdc.MdcExecutorService;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * IntegrationManager is responsible for loading and managing integrations.
 *
 * @author coffee
 */
public class IntegrationManager {

    private SystemRestarter systemRestarter;

    private static String INTEGRATIONS_CONFIG_PATH = ".ecat-data/core/integrations.yml";
    private static String INTEGRATION_ITEM_PATH = ".ecat-data/integrations/%s.yml";

    private final EcatCore core;
    private final IntegrationRegistry integrationRegistry;
    private final StateManager stateManager;
    private ExecutorService executorService = MdcExecutorService.wrap(Executors.newFixedThreadPool(1));
    private final LoadJarUtils loadJarUtils;
    private final URLClassLoader restartClassLoader;

    // 初始依赖关系快照（系统启动时的依赖关系）
    // 用于判断 ClassLoader 层级是否需要调整
    private Map<String, List<String>> initialDependencyGraph;

    private boolean initialSnapshotTaken = false;

    private Log log;

    public IntegrationManager(EcatCore core, IntegrationRegistry integrationRegistry, StateManager stateManager,
        String integrationConfigPath, String integrationItemPath) {
        this(core, integrationRegistry, stateManager);
        INTEGRATIONS_CONFIG_PATH = integrationConfigPath;
        INTEGRATION_ITEM_PATH = integrationItemPath;
    }

    public IntegrationManager(EcatCore core, IntegrationRegistry integrationRegistry, StateManager stateManager) {
        this.core = core;
        this.integrationRegistry = integrationRegistry;
        this.stateManager = stateManager;
        this.log = LogFactory.getLogger(getClass());
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        // if classLoader is instance of URLClassLoader, set restartClassLoader to classLoader
        if (classLoader instanceof URLClassLoader) {
            this.restartClassLoader = (URLClassLoader) classLoader;
        }
        else{
            throw new RuntimeException("Thread.currentThread().getContextClassLoader() is not URLClassLoader");
        }
        this.loadJarUtils = new LoadJarUtils(core, restartClassLoader);
        this.systemRestarter = new SystemRestarter();
    }

    public void loadIntegrations() {
        Yaml yaml = new Yaml();
        InputStream inputStream = this.getClass()
               .getClassLoader()
               .getResourceAsStream("core-integrations.yml");
        Map<String, Map<String, Object>> coreIntegrationsConfig = yaml.load(inputStream);
        Map<String, Object> coreitgs = coreIntegrationsConfig.getOrDefault("integrations", null);

        // TODO: merge coreitgs and itgs for needed

        Map<String, Map<String, Object>> integrationsConfig = loadIntegrationsConfig();
        Map<String, Object> itgs = integrationsConfig.getOrDefault("integrations", new HashMap<>());

        // 清理 PENDING_REMITTED 集成（已标记为删除的集成）
        cleanupPendingRemovedIntegrations(integrationsConfig);
        
        Map<String, List<String>> dependencyMap = new HashMap<>();
        List<IntegrationInfo> integrationInfoList = new ArrayList<>();

        for (Map.Entry<String, Object> entry : itgs.entrySet()) {
            // key 现在是 groupId:artifactId 格式
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> integrationConfig = (Map<String, Object>) value;

                boolean enabled = (boolean) integrationConfig.get("enabled");
                if (enabled) {
                    // 不再从配置读取 className，改为从 JAR 扫描获取
                    String groupId = (String) integrationConfig.get("groupId");
                    String artifactId = (String) integrationConfig.get("artifactId");
                    String version = (String) integrationConfig.get("version");

                    String localRepoPath = System.getProperty("user.home") + "/.m2/repository";
                    String jarPath = localRepoPath + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
                    File jarFile = new File(jarPath);

                    if (!jarFile.exists()) {
                        log.error("JAR 文件不存在: " + jarPath);
                        continue;
                    }

                    // 扫描 JAR 获取入口类 className
                    String className;
                    try {
                        className = JarDependencyLoader.scanIntegrationEntryClass(jarFile);
                    } catch (JarScanException e) {
                        log.error("扫描 JAR 入口类失败 [" + key + "]: " + e.getMessage());
                        // 打印详细错误信息到控制台
                        System.err.println(e.getMessage());
                        // core 退出运行
                        throw new RuntimeException("集成扫描失败，core 无法继续运行", e);
                    }

                    // 读取JAR包中的部分集成信息（已包含dependencies和webPlatform）
                    IntegrationInfo info = JarDependencyLoader.readPartialIntegrationInfoFromJar(jarFile);

                    // 用主配置信息完善对象（关键业务字段由主配置决定）
                    info.setArtifactId(artifactId);
                    info.setDepended(false); // 主配置控制是否为被依赖项
                    info.setEnabled(enabled);
                    info.setClassName(className);
                    info.setGroupId(groupId);
                    info.setVersion(version);

                    // 输出依赖信息（用于调试）
                    if (log.isDebugEnabled()) {
                        StringBuilder depInfo = new StringBuilder();
                        depInfo.append("集成依赖信息 [").append(artifactId).append("]: ");
                        depInfo.append("groupId=").append(groupId).append(", ");
                        depInfo.append("version=").append(version).append(", ");
                        depInfo.append("dependencies=[");
                        if (info.getDependencyInfoList() != null && !info.getDependencyInfoList().isEmpty()) {
                            for (com.ecat.core.Integration.DependencyInfo dep : info.getDependencyInfoList()) {
                                depInfo.append(dep.toShortString()).append(", ");
                            }
                        } else {
                            depInfo.append("空");
                        }
                        depInfo.append("]");
                        log.debug(depInfo.toString());
                    }

                    // 添加到依赖映射（使用 getCoordinate() 作为唯一键）
                    // 从 dependencyInfoList 提取 coordinate 列表
                    List<String> depCoordinates = new ArrayList<>();
                    if (info.getDependencyInfoList() != null) {
                        for (com.ecat.core.Integration.DependencyInfo dep : info.getDependencyInfoList()) {
                            depCoordinates.add(dep.getCoordinate());
                        }
                    }
                    dependencyMap.put(info.getCoordinate(), depCoordinates);
                    integrationInfoList.add(info);
                }
            }
        }
        List<IntegrationInfo> loadOrder;
        try{
            loadOrder = JarDependencyLoader.getLoadOrder(integrationInfoList, dependencyMap);
        }
        catch (IllegalStateException e){
            throw new RuntimeException(e.getMessage());
        }

        // 输出加载顺序（DEBUG 级别，合并为一条日志）
        if (log.isDebugEnabled()) {
            StringBuilder orderInfo = new StringBuilder();
            orderInfo.append("加载顺序 (loaderOrder):\n");
            for (IntegrationInfo info : loadOrder) {
                orderInfo.append("  ").append(info).append("\n");
            }
            log.debug(orderInfo.toString());
        }

        for (IntegrationInfo info : loadOrder) {
            if (info.isEnabled()) {
                String className = info.getClassName();
                String groupId = info.getGroupId();
                String artifactId = info.getArtifactId();
                String version = info.getVersion();

                executorService.execute(() -> {
                    try {
                        // 构建本地 Maven 仓库中 JAR 文件的路径
                        String localRepoPath = System.getProperty("user.home") + "/.m2/repository";
                        String jarPath = localRepoPath + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
                        File jarFile = new File(jarPath);
                        if (!jarFile.exists()) {
                            throw new RuntimeException("JAR 文件 " + jarPath + " 不存在，请检查本地 Maven 仓库。");
                        }

                        ArrayList<URL> urlList = new ArrayList<>();
                        urlList.add(jarFile.toURI().toURL());
                        ArrayList<String> pathList = new ArrayList<>();
                        
                        List<String> dependencyJarPaths = MavenDependencyParser.getDependencyJarPaths(jarPath);
                        for (String djarpath : dependencyJarPaths) {
                            File dJarFile = new File(djarpath);
                            if (!jarFile.exists()) {
                                throw new RuntimeException("JAR 文件 " + jarPath + " 不存在，请检查本地 Maven 仓库。");
                            }
                            urlList.add(dJarFile.toURI().toURL());
                            pathList.add(dJarFile.getPath());
                            
                            // loadJarUtils.loadJar(dJarFile.getPath(),restartClassLoader);
                        }

                        // ClassLoader classLoader =restartClassLoader;
                        // ClassLoader classLoader = loadJarUtils.getBaseEcatClassLoader();
                        // if(jarFile.getPath().indexOf("integration-mock-timer-tick") >=0){
                        //     classLoader = null;
                        // }
                        // if(info.isDepended()){
                        //     classLoader = null;
                        // }
                        // else{
                        //     URLClassLoader childClassLoader = findParentClassloader(loadOrder, integrationRegistry, info);
                        //     if(childClassLoader != null) {
                        //         classLoader = childClassLoader;
                        //     }
                        // }
                        
                        // use parent's childClassLoader first
                        // if not found, then use the base class loader as parent class loader or directly using the base class loader
                        // URLClassLoader childClassLoader = findParentClassloader(loadOrder, integrationRegistry, info);
                        // if(childClassLoader != null) {
                        //     classLoader = childClassLoader;
                        // }

                        URLClassLoader classLoader = null;

                        URLClassLoader childClassLoader = findParentClassloader(loadOrder, integrationRegistry, info);


                        if(info.isDepended()){
                            if(childClassLoader == null){
                                // find a tree root classloader node, merge to ecat core classloader as child node
                                // Ecat Common Dependencies
                                // i.e integration-ecat-common
                                classLoader = loadJarUtils.getEcatCoreClassLoader();
                            }
                            else{
                                if(childClassLoader.equals(loadJarUtils.getEcatCoreClassLoader())){
                                    // Ecat Core Dependencies + Ecat Common Dependencies
                                    // i.e integration-ecat-core-ruoyi integration-modbus 
                                    classLoader = loadJarUtils.getEcatDependentClassLoader();
                                }
                                else{
                                    // method 1
                                    // find middle node between parent and other Ecat Dependent integration
                                    // i.e integration-env-data-manager
                                    // 适用于springboot被架的被依赖集成加载，因为引用关系被springboot的bean处理好了
                                    // 如果是一个父节点有多个依赖子节点，且子节点间存在相互依赖，并且被依赖的子节点还依赖第三方jar
                                    // 则需要抽取公共依赖jar到common或父节点下新建一个公共依赖集成，在此集成下再挂载子节点，确保依赖关系正确
                                    // classLoader = new CustomClassLoader(new URL[0], childClassLoader);



                                    // method 2
                                    // for common dependencies import using, this integration can be loaded by parent's childClassLoader
                                    // other leaf integration which depended this integration will be loaded by parent childClassLoader's CustomClassLoader
                                    // i.e integration-env-data-manager
                                    classLoader = childClassLoader;
                                    
                                }
                            }
                        }
                        else{
                            // use parent's childClassLoader first
                            // if not found, then use the base class loader as parent class loader or directly using the base class loader
                            if(childClassLoader != null){
                                if(childClassLoader.equals(loadJarUtils.getEcatCoreClassLoader())){
                                    // find root classloader leaf node, but root classloader child node must be a trunk for design (like dependent classloader)
                                    // this mean this integration is a leaf node and parent don't have childClassLoader,
                                    // so change to dependent classloader leaf node
                                    // i.e integration-saimosen‘s childClassLoader is found by integration-ecat-common
                                    classLoader = new CustomClassLoader(new URL[0], loadJarUtils.getEcatDependentClassLoader());
                                }
                                else{
                                    // find non root classloader leaf node
                                    // i.e env-device-manager's childClassLoader is found by integration-ecat-core-ruoyi
                                    // 为了让基于框架如ruoyi的集成内部Controller使用自己Integration内所有代码包括IntegrationBase子类代码，需要将此类场景的集成加载方式交给框架集成自己负责，
                                    // 比如springboot框架需要用LaunchedURLClassLoader来处理下属集成的class加载，其他框架不一定。
                                    // 因此parent需要控制返回的childClassLoader是LaunchedURLClassLoader还是new CustomClassLoader(new URL[0], launchedURLClassLoader);

                                    classLoader = childClassLoader;
                                }
                            }
                            else{
                                // Single Integration without any depended integration, temporarily use the dependent classloader
                                // i.e integration-env-device-calibration
                                classLoader = loadJarUtils.getEcatDependentClassLoader();
                            }
                        }
                        
                        // 递归查找被依赖集成loadOption是否有childClassloader
                        // TODO:注意这里只能串行执行无法并发，如果并发必须确保被依赖集成必须早于当前集成完成加载
                        

                        LoadJarResult checkService = loadJarUtils.loadJar(jarFile.getPath(), pathList.toArray(new String[0]), classLoader, className);

                        try {
                            // LoadJarUtils.loadJar(jarFile.getPath(), restartClassLoader);
                            // Class<?> clazz = restartClassLoader.loadClass(className);
                            // System.out.println("MockIntegration 类加载器: " + clazz.getClassLoader());
                            // Class<?> baseClass = restartClassLoader.loadClass("com.ecat.core.Integration.IntegrationBase");
                            // System.out.println("IntegrationBase 类加载器: " + baseClass.getClassLoader());

                            // Object checkService = clazz.getDeclaredConstructor().newInstance();
                            IntegrationBase integration = checkService.getIntegration();
                            IntegrationLoadOption loadOption = new IntegrationLoadOption(checkService.getClassLoader());
                            loadOption.setIntegrationInfo(info);

                            try{
                                integration.onLoad(core, loadOption);
                                integrationRegistry.register(info.getCoordinate(), integration);
                                integration.onInit();
                                integration.onStart();

                            }
                            catch (Exception e) {
                                log.error("集成 " + artifactId + " 加载失败: " + e.getStackTrace().toString());
                            }

                        } catch (Exception e) {
                            log.error(e.getStackTrace().toString());
                        }

                    } catch (Exception e) {
                        log.error(e.getStackTrace().toString());
                    }
                });

            }

        }

        // 等待所有集成加载完成后，保存初始依赖关系快照
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("集成加载超时，部分集成可能未完全加载");
            }
            // 重新创建线程池（后续操作可能需要）
            executorService = MdcExecutorService.wrap(Executors.newFixedThreadPool(1));

            // 更新所有成功加载的集成状态为 RUNNING（包括之前是 PENDING_ADDED 的）
            updateLoadedIntegrationsState();

            // 保存初始依赖关系快照（用于判断 ClassLoader 层级调整）
            saveInitialDependencySnapshot();

        } catch (InterruptedException e) {
            log.error("等待集成加载完成被中断", e);
            Thread.currentThread().interrupt();
        }
    }

    // public void saveConfig(String integrationName, Map<String, Object> config) {
    //     Yaml yaml = new Yaml();
    //     // 更新config  update字段，为当前时间的string
    //     config.put("update", new java.util.Date().toString());

    //     try (FileWriter writer = new FileWriter(new File(".ecat-data/integrations/" + integrationName + ".yml"))) {
    //         yaml.dump(config, writer);
    //     } catch (IOException e) {
    //         log.error(e.getStackTrace().toString());
    //     }
    // }

    // public Map<String, Object> loadConfig(String integrationName) {
    //     Yaml yaml = new Yaml();
    //     try {
    //         return yaml.load(new java.io.FileInputStream(new File(".ecat-data/integrations/" + integrationName + ".yml")));
    //     } catch (IOException e) {
    //         log.error(e.getStackTrace().toString());
    //         return null;
    //     }
    // }

    public ClassLoader getEcatClassLoader() {
        return restartClassLoader;
    }

    public SystemRestarter getSystemRestarter() {
        return systemRestarter;
    }

    // ========== 状态管理方法 ==========

    /**
     * 获取集成状态
     *
     * @param coordinate 集成坐标 (groupId:artifactId)
     * @return 集成状态信息
     */
    public IntegrationStatus getIntegrationStatus(String coordinate) {
        IntegrationStatus.Builder builder = IntegrationStatus.builder()
            .coordinate(coordinate);

        Map<String, Map<String, Object>> config = loadIntegrationsConfig();
        Map<String, Object> integrations = config.getOrDefault("integrations", new HashMap<>());
        Map<String, Object> integrationConfig = (Map<String, Object>) integrations.get(coordinate);

        if (integrationConfig == null) {
            return builder
                .state(IntegrationState.STOPPED)
                .message("集成不存在")
                .isLocked(false)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .build();
        }

        // 读取配置
        Boolean enabled = (Boolean) integrationConfig.get("enabled");
        String stateStr = (String) integrationConfig.get("state");
        String version = (String) integrationConfig.get("version");
        String pendingVersion = (String) integrationConfig.get("pendingVersion");
        String updateStr = (String) integrationConfig.get("update");

        // 解析状态
        IntegrationState state;
        if (stateStr != null) {
            try {
                state = IntegrationState.valueOf(stateStr);
            } catch (IllegalArgumentException e) {
                state = enabled != null && enabled ? IntegrationState.RUNNING : IntegrationState.STOPPED;
            }
        } else {
            state = enabled != null && enabled ? IntegrationState.RUNNING : IntegrationState.STOPPED;
        }

        // 解析更新时间
        Date lastUpdate = null;
        if (updateStr != null) {
            try {
                // SimpleDateFormat 不是线程安全的，每次创建新实例
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                lastUpdate = sdf.parse(updateStr);
            } catch (Exception e) {
                lastUpdate = new Date();
            }
        }

        // 查找依赖和依赖者
        // 注意：依赖者列表使用初始依赖关系快照，而不是运行时依赖关系
        // 这确保 ClassLoader 层级的一致性 - 只有重启后才能调整层级
        List<String> dependencies = findDependencies(coordinate, integrationConfig);
        List<String> initialDependents = getInitialDependents(coordinate);

        // 判断操作权限
        boolean isLocked = state.isPending();
        boolean hasInitialDependents = !initialDependents.isEmpty();
        boolean canDisable = !isLocked && state.isRunning() && !hasInitialDependents;
        boolean canRemove = !isLocked && !hasInitialDependents;
        boolean canEnable = !isLocked && state.isStopped();
        boolean canUpgrade = !isLocked;

        return builder
            .state(state)
            .message(state.getDescription())
            .dependencies(dependencies)
            .dependents(initialDependents)
            .version(version)
            .pendingVersion(pendingVersion)
            .lastUpdate(lastUpdate)
            .canDisable(canDisable)
            .canRemove(canRemove)
            .canEnable(canEnable)
            .canUpgrade(canUpgrade)
            .isLocked(isLocked)
            .build();
    }

    /**
     * 获取所有集成状态
     *
     * @return 所有集成状态列表
     */
    public List<IntegrationStatus> getAllIntegrationStatus() {
        List<IntegrationStatus> statusList = new ArrayList<>();

        Map<String, Map<String, Object>> config = loadIntegrationsConfig();
        Map<String, Object> integrations = config.getOrDefault("integrations", new HashMap<>());

        for (String coordinate : integrations.keySet()) {
            statusList.add(getIntegrationStatus(coordinate));
        }

        return statusList;
    }

    /**
     * 检查集成是否处于 PENDING_* 状态（锁定）
     *
     * @param coordinate 集成坐标
     * @return 如果处于锁定状态返回 true
     */
    public boolean isIntegrationLocked(String coordinate) {
        IntegrationStatus status = getIntegrationStatus(coordinate);
        return status.isLocked();
    }

    /**
     * 检查是否有待重启的集成
     *
     * @return 如果有待重启的集成返回 true
     */
    public boolean hasPendingIntegrations() {
        List<IntegrationStatus> allStatus = getAllIntegrationStatus();
        for (IntegrationStatus status : allStatus) {
            if (status.isLocked()) {
                return true;
            }
        }
        return false;
    }

    // ========== 核心控制方法 ==========

    /**
     * 新增集成
     *
     * @param coordinate 集成坐标 (groupId:artifactId)
     * @param config 集成配置
     * @return 集成状态（RUNNING 或 PENDING_ADDED）
     */
    public IntegrationStatus addIntegration(String coordinate, Map<String, Object> config) {
        log.info("新增集成: {}", coordinate);

        // 1. 检查是否已存在
        IntegrationStatus existingStatus = getIntegrationStatus(coordinate);
        if (existingStatus.getState() != IntegrationState.STOPPED ||
            !("集成不存在".equals(existingStatus.getMessage()))) {
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(existingStatus.getState())
                .message("集成已存在")
                .isLocked(existingStatus.isLocked())
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .build();
        }

        // 2. 验证 JAR 文件存在
        String groupId = (String) config.get("groupId");
        String artifactId = (String) config.get("artifactId");
        String version = (String) config.get("version");

        if (groupId == null || artifactId == null || version == null) {
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(IntegrationState.STOPPED)
                .message("配置缺少必要字段 (groupId, artifactId, version)")
                .isLocked(false)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .build();
        }

        String jarPath = getJarPath(groupId, artifactId, version);
        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(IntegrationState.STOPPED)
                .message("JAR 文件不存在: " + jarPath)
                .isLocked(false)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .build();
        }

        // 3. 扫描依赖
        List<String> dependencies = new ArrayList<>();
        try {
            IntegrationInfo info = com.ecat.core.Utils.JarDependencyLoader.readPartialIntegrationInfoFromJar(jarFile);
            if (info.getDependencyInfoList() != null) {
                for (com.ecat.core.Integration.DependencyInfo dep : info.getDependencyInfoList()) {
                    dependencies.add(dep.getCoordinate());
                }
            }
        } catch (Exception e) {
            log.warn("扫描 JAR 依赖信息失败: {} - {}", jarPath, e.getMessage());
        }

        // 4. 检查是否需要调整类加载器层级
        // 使用 requiresClassLoaderHierarchyChange 检查：新集成的依赖是否在初始加载时是独立的
        // 如果是，启用新集成需要 ClassLoader 层级调整
        boolean needsAdjustment = requiresClassLoaderHierarchyChange(coordinate, dependencies);

        // 5. 准备配置
        Boolean enabled = (Boolean) config.getOrDefault("enabled", true);
        // 注意：dependencies 不会保存到配置文件中，而是从 JAR 包的 ecat-config.yml 动态读取

        if (needsAdjustment) {
            // 需要重启 → PENDING_ADDED
            config.put("enabled", true);
            config.put("state", IntegrationState.PENDING_ADDED.name());
            saveIntegrationConfig(coordinate, config);

            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(IntegrationState.PENDING_ADDED)
                .message("新集成需要调整类加载器层级，请重启系统以加载")
                .dependencies(dependencies)
                .dependents(new ArrayList<>())
                .version(version)
                .isLocked(true)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .build();
        } else {
            // 热加载 → RUNNING
            config.put("enabled", true);
            config.put("state", IntegrationState.RUNNING.name());
            saveIntegrationConfig(coordinate, config);

            // TODO: 实际加载集成
            // 这里需要调用现有的加载逻辑

            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(IntegrationState.RUNNING)
                .message("集成已成功加载")
                .dependencies(dependencies)
                .dependents(new ArrayList<>())
                .version(version)
                .isLocked(false)
                .canEnable(false)
                .canDisable(true)
                .canRemove(true)
                .canUpgrade(true)
                .build();
        }
    }

    /**
     * 启用集成 (STOPPED → RUNNING)
     *
     * @param coordinate 集成坐标
     * @return 集成状态
     */
    public IntegrationStatus enableIntegration(String coordinate) {
        log.info("启用集成: {}", coordinate);

        // 1. 检查是否锁定
        if (isIntegrationLocked(coordinate)) {
            IntegrationStatus status = getIntegrationStatus(coordinate);
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(status.getState())
                .message("集成处于待重启状态，禁止操作")
                .isLocked(true)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .build();
        }

        // 2. 获取当前状态
        IntegrationStatus currentStatus = getIntegrationStatus(coordinate);
        if (!currentStatus.isStopped()) {
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(currentStatus.getState())
                .message("集成不是停止状态，无法启用")
                .isLocked(false)
                .canEnable(false)
                .canDisable(currentStatus.canDisable())
                .canRemove(currentStatus.canRemove())
                .canUpgrade(currentStatus.canUpgrade())
                .build();
        }

        // 3. 关键修复：检查是否需要调整 ClassLoader 层级
        // 如果被启用的集成有依赖，且这些依赖在初始加载时没有被依赖，
        // 则需要将这些依赖升级为被依赖节点，这需要重启系统
        if (requiresClassLoaderHierarchyChange(coordinate)) {
            log.warn("启用集成 {} 需要调整 ClassLoader 层级，标记为 PENDING_ADDED", coordinate);

            // 更新配置为启用状态，但标记为待重启
            Map<String, Map<String, Object>> config = loadIntegrationsConfig();
            Map<String, Object> integrations = config.getOrDefault("integrations", new HashMap<>());
            @SuppressWarnings("unchecked")
            Map<String, Object> integrationConfig = (Map<String, Object>) integrations.get(coordinate);

            if (integrationConfig != null) {
                integrationConfig.put("enabled", true);
                integrationConfig.put("state", IntegrationState.PENDING_ADDED.name());
                integrationConfig.put("update", new Date().toString());
                updateIntegrationsConfig(config);
            }

            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(IntegrationState.PENDING_ADDED)
                .message("集成需要调整 ClassLoader 层级，请重启系统以生效")
                .isLocked(true)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .dependents(currentStatus.getDependents())
                .dependencies(currentStatus.getDependencies())
                .version(currentStatus.getVersion())
                .build();
        }

        // 4. 正常启用流程
        Map<String, Map<String, Object>> config = loadIntegrationsConfig();
        Map<String, Object> integrations = config.getOrDefault("integrations", new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> integrationConfig = (Map<String, Object>) integrations.get(coordinate);

        if (integrationConfig != null) {
            integrationConfig.put("enabled", true);
            integrationConfig.put("state", IntegrationState.RUNNING.name());
            integrationConfig.put("update", new Date().toString());
            updateIntegrationsConfig(config);

            // TODO: 调用 integration.onStart()
            IntegrationBase integration = integrationRegistry.getIntegration(coordinate);
            if (integration != null) {
                try {
                    integration.onStart();
                } catch (Exception e) {
                    log.error("启动集成失败: {} - {}", coordinate, e.getMessage());
                }
            }
        }

        return getIntegrationStatus(coordinate);
    }

    /**
     * 检查启用集成是否需要改变 ClassLoader 层级
     *
     * <p>关键逻辑：Class Loader 层级是基于系统初始化时的依赖关系确定的。
     * 如果启用一个集成后，它的依赖在初始加载时没有被依赖（使用独立 ClassLoader），
     * 但现在需要被共享（成为被依赖节点），这需要 ClassLoader 层级调整，必须重启。
     *
     * @param coordinate 要启用的集成坐标
     * @return true 如果需要调整 ClassLoader 层级
     */
    private boolean requiresClassLoaderHierarchyChange(String coordinate) {
        // 获取要启用的集成的依赖列表
        IntegrationStatus status = getIntegrationStatus(coordinate);
        List<String> dependencies = status.getDependencies();

        return requiresClassLoaderHierarchyChange(coordinate, dependencies);
    }

    /**
     * 检查启用集成是否需要改变 ClassLoader 层级（重载版本，直接传入依赖列表）
     *
     * <p>这个版本用于 addIntegration 等场景，此时已经扫描了依赖列表，
     * 避免重复调用 getIntegrationStatus。
     *
     * @param coordinate 要启用的集成坐标
     * @param dependencies 依赖列表
     * @return true 如果需要调整 ClassLoader 层级
     */
    private boolean requiresClassLoaderHierarchyChange(String coordinate, List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return false;
        }

        // 检查每个依赖是否在初始加载时被其他集成依赖
        for (String depCoordinate : dependencies) {
            // 获取依赖集成的初始依赖者（在系统初始化时的依赖者）
            List<String> initialDependents = getInitialDependents(depCoordinate);

            if (initialDependents.isEmpty()) {
                // 这个依赖在初始加载时没有被依赖者（使用独立 ClassLoader）
                // 现在要启用的新集成依赖它，所以它需要升级为被依赖节点
                // 这需要 ClassLoader 层级调整
                log.warn("集成 {} 依赖 {}，而 {} 在初始加载时没有依赖者，需要调整 ClassLoader 层级",
                    coordinate, depCoordinate, depCoordinate);
                return true;
            }
        }

        return false;
    }

    /**
     * 获取集成在系统初始化时的依赖者列表
     *
     * <p>这个方法返回集成在系统启动时被哪些其他集成依赖。
     * 与运行时的 findDependents() 不同，这个方法反映的是初始状态，
     * 用于判断 ClassLoader 层级是否需要调整。
     *
     * @param coordinate 集成坐标
     * @return 初始依赖者列表
     */
    private List<String> getInitialDependents(String coordinate) {
        // 从初始依赖关系快照中获取
        if (initialDependencyGraph != null) {
            return initialDependencyGraph.getOrDefault(coordinate, new ArrayList<>());
        }
        // 如果快照不存在，返回空列表
        return new ArrayList<>();
    }

    /**
     * 保存初始依赖关系快照
     *
     * <p>在系统启动完成后，调用此方法保存所有启用集成的依赖关系快照。
     * 这个快照用于判断后续操作是否需要调整 ClassLoader 层级。
     */
    private void saveInitialDependencySnapshot() {
        initialDependencyGraph = new HashMap<>();
        Map<String, Map<String, Object>> config = loadIntegrationsConfig();
        Map<String, Object> integrations = config.getOrDefault("integrations", new HashMap<>());

        // 遍历所有启用的集成
        for (Map.Entry<String, Object> entry : integrations.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> integrationConfig = (Map<String, Object>) value;
                Boolean enabled = (Boolean) integrationConfig.get("enabled");

                // 只处理启用的集成
                if (enabled != null && enabled) {
                    // 获取此集成的依赖列表
                    List<String> dependencies = findDependencies(key, integrationConfig);

                    // 将此集成添加到其所有依赖的依赖者列表中
                    for (String depCoordinate : dependencies) {
                        initialDependencyGraph.computeIfAbsent(depCoordinate, k -> new ArrayList<>()).add(key);
                    }
                }
            }
        }

        initialSnapshotTaken = true;
        log.info("已保存初始依赖关系快照，共 {} 个集成有依赖者", initialDependencyGraph.size());
    }

    /**
     * 更新所有已加载集成的状态为 RUNNING
     *
     * <p>在系统重启后，之前标记为 PENDING_ADDED 的集成现在应该被正常加载，
     * 加载成功后需要将状态更新为 RUNNING。
     */
    private void updateLoadedIntegrationsState() {
        Map<String, Map<String, Object>> config = loadIntegrationsConfig();
        Map<String, Object> integrations = config.getOrDefault("integrations", new HashMap<>());
        boolean needsUpdate = false;

        for (Map.Entry<String, Object> entry : integrations.entrySet()) {
            String coordinate = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> integrationConfig = (Map<String, Object>) value;
                Boolean enabled = (Boolean) integrationConfig.get("enabled");

                // 只处理已启用且当前不是 RUNNING 状态的集成
                if (enabled != null && enabled) {
                    String currentState = (String) integrationConfig.get("state");

                    // 如果状态是 PENDING_ADDED 或其他非 RUNNING 状态，更新为 RUNNING
                    if (!IntegrationState.RUNNING.name().equals(currentState)) {
                        // 检查集成是否真的已加载
                        if (integrationRegistry.getIntegration(coordinate) != null) {
                            integrationConfig.put("state", IntegrationState.RUNNING.name());
                            integrationConfig.put("update", new Date().toString());
                            needsUpdate = true;
                            log.info("集成 {} 状态已更新为 RUNNING", coordinate);
                        }
                    }
                }
            }
        }

        if (needsUpdate) {
            updateIntegrationsConfig(config);
            log.info("已完成集成状态更新");
        }
    }

    /**
     * 停用集成 (RUNNING → STOPPED)
     *
     * @param coordinate 集成坐标
     * @return 集成状态
     */
    public IntegrationStatus disableIntegration(String coordinate) {
        log.info("停用集成: {}", coordinate);

        // 1. 检查是否锁定
        if (isIntegrationLocked(coordinate)) {
            IntegrationStatus status = getIntegrationStatus(coordinate);
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(status.getState())
                .message("集成处于待重启状态，禁止操作")
                .isLocked(true)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .build();
        }

        // 2. 检查是否有依赖者（基于初始依赖关系）
        if (!canDisableOrRemove(coordinate)) {
            // 使用初始依赖关系来显示错误信息
            List<String> initialDependents = getInitialDependents(coordinate);
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(IntegrationState.RUNNING)
                .message("无法停用：此集成在初始加载时被其他集成依赖，需要重启后才能停用 - " + initialDependents)
                .dependents(initialDependents)
                .isLocked(false)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(true)
                .build();
        }

        // 3. 获取当前状态
        IntegrationStatus currentStatus = getIntegrationStatus(coordinate);
        if (!currentStatus.isRunning()) {
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(currentStatus.getState())
                .message("集成不是运行状态，无法停用")
                .isLocked(false)
                .canEnable(currentStatus.canEnable())
                .canDisable(false)
                .canRemove(currentStatus.canRemove())
                .canUpgrade(currentStatus.canUpgrade())
                .build();
        }

        // 4. 更新配置
        Map<String, Map<String, Object>> config = loadIntegrationsConfig();
        Map<String, Object> integrations = config.getOrDefault("integrations", new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> integrationConfig = (Map<String, Object>) integrations.get(coordinate);

        if (integrationConfig != null) {
            integrationConfig.put("enabled", false);
            integrationConfig.put("state", IntegrationState.STOPPED.name());
            integrationConfig.put("update", new Date().toString());
            updateIntegrationsConfig(config);

            // 调用 integration.onPause()
            IntegrationBase integration = integrationRegistry.getIntegration(coordinate);
            if (integration != null) {
                try {
                    integration.onPause();
                } catch (Exception e) {
                    log.error("暂停集成失败: {} - {}", coordinate, e.getMessage());
                }
            }
        }

        return getIntegrationStatus(coordinate);
    }

    /**
     * 卸载集成 (RUNNING/STOPPED → PENDING_REMOVED)
     *
     * @param coordinate 集成坐标
     * @return 集成状态
     */
    public IntegrationStatus removeIntegration(String coordinate) {
        log.info("卸载集成: {}", coordinate);

        // 1. 检查是否锁定
        if (isIntegrationLocked(coordinate)) {
            IntegrationStatus status = getIntegrationStatus(coordinate);
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(status.getState())
                .message("集成处于待重启状态，禁止操作")
                .isLocked(true)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .build();
        }

        // 2. 检查是否有依赖者
        if (!canDisableOrRemove(coordinate)) {
            List<String> dependents = findDependents(coordinate);
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(getIntegrationStatus(coordinate).getState())
                .message("无法卸载：有其他集成依赖此集成 - " + dependents)
                .dependents(dependents)
                .isLocked(false)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(true)
                .build();
        }

        // 3. 更新配置
        Map<String, Map<String, Object>> config = loadIntegrationsConfig();
        Map<String, Object> integrations = config.getOrDefault("integrations", new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> integrationConfig = (Map<String, Object>) integrations.get(coordinate);

        if (integrationConfig != null) {
            integrationConfig.put("enabled", false);
            integrationConfig.put("state", IntegrationState.PENDING_REMOVED.name());
            integrationConfig.put("_deleted", true);
            integrationConfig.put("update", new Date().toString());
            updateIntegrationsConfig(config);

            // 调用 integration.onRelease()
            IntegrationBase integration = integrationRegistry.getIntegration(coordinate);
            if (integration != null) {
                try {
                    integration.onRelease();
                } catch (Exception e) {
                    log.error("释放集成失败: {} - {}", coordinate, e.getMessage());
                }
            }
        }

        return IntegrationStatus.builder()
            .coordinate(coordinate)
            .state(IntegrationState.PENDING_REMOVED)
            .message("集成已逻辑删除，请重启系统以彻底清理")
            .dependents(new ArrayList<>())
            .isLocked(true)
            .canEnable(false)
            .canDisable(false)
            .canRemove(false)
            .canUpgrade(false)
            .build();
    }

    /**
     * 升级集成 (→ PENDING_UPGRADE)
     *
     * @param coordinate 集成坐标
     * @param newVersion 新版本号
     * @return 集成状态
     */
    public IntegrationStatus upgradeIntegration(String coordinate, String newVersion) {
        log.info("升级集成: {} -> {}", coordinate, newVersion);

        // 1. 检查是否锁定
        if (isIntegrationLocked(coordinate)) {
            IntegrationStatus status = getIntegrationStatus(coordinate);
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(status.getState())
                .message("集成处于待重启状态，禁止操作")
                .isLocked(true)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .build();
        }

        // 2. 获取当前配置
        Map<String, Map<String, Object>> config = loadIntegrationsConfig();
        Map<String, Object> integrations = config.getOrDefault("integrations", new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> integrationConfig = (Map<String, Object>) integrations.get(coordinate);

        if (integrationConfig == null) {
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(IntegrationState.STOPPED)
                .message("集成不存在")
                .isLocked(false)
                .canEnable(false)
                .canDisable(false)
                .canRemove(false)
                .canUpgrade(false)
                .build();
        }

        String currentVersion = (String) integrationConfig.get("version");
        String groupId = (String) integrationConfig.get("groupId");
        String artifactId = (String) integrationConfig.get("artifactId");

        // 3. 检查新版本 JAR 是否已下载
        String newJarPath = getJarPath(groupId, artifactId, newVersion);
        File newJarFile = new File(newJarPath);
        if (!newJarFile.exists()) {
            return IntegrationStatus.builder()
                .coordinate(coordinate)
                .state(getIntegrationStatus(coordinate).getState())
                .message("新版本 JAR 文件不存在: " + newJarPath)
                .version(currentVersion)
                .isLocked(false)
                .canEnable(getIntegrationStatus(coordinate).canEnable())
                .canDisable(getIntegrationStatus(coordinate).canDisable())
                .canRemove(getIntegrationStatus(coordinate).canRemove())
                .canUpgrade(false)
                .build();
        }

        // 4. 更新配置
        integrationConfig.put("oldVersion", currentVersion);
        integrationConfig.put("version", newVersion);
        integrationConfig.put("pendingVersion", newVersion);
        integrationConfig.put("state", IntegrationState.PENDING_UPGRADE.name());
        integrationConfig.put("update", new Date().toString());
        updateIntegrationsConfig(config);

        return IntegrationStatus.builder()
            .coordinate(coordinate)
            .state(IntegrationState.PENDING_UPGRADE)
            .message("版本已更新，旧版本继续运行，请重启系统以加载新版本")
            .version(currentVersion)
            .pendingVersion(newVersion)
            .isLocked(true)
            .canEnable(false)
            .canDisable(false)
            .canRemove(false)
            .canUpgrade(false)
            .build();
    }

    /**
     * 重启系统
     *
     * @param delaySeconds 延迟秒数
     */
    public void restartSystem(int delaySeconds) {
        log.info("准备重启系统，延迟 {} 秒", delaySeconds);
        systemRestarter.scheduleRestart(delaySeconds);
    }

    /**
     * 使用默认延迟（5秒）重启系统
     */
    public void restartSystem() {
        restartSystem(SystemRestarter.getDefaultDelaySeconds());
    }


    // 加载完整配置（自动创建文件）
    public Map<String, Map<String, Object>> loadIntegrationsConfig() {
        File configFile = new File(INTEGRATIONS_CONFIG_PATH);
        
        // 确保文件存在（不存在则创建空文件）
        if (!configFile.exists()) {
            createEmptyConfigFile(configFile);
            return new HashMap<>();
        }
        
        try (InputStream inputStream = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Map<String, Object>> config = yaml.load(inputStream);
            return config != null ? config : new HashMap<>();
        } catch (IOException e) {
            log.error("读取配置文件失败: " + e.getMessage());
            return new HashMap<>();
        }
    }

    // 保存core/integrations.yml单个集成配置（追加模式）
    public void saveIntegrationConfig(String integrationName, Map<String, Object> integrationConfig) {
        Map<String, Map<String, Object>> fullConfig = loadIntegrationsConfig();

        // 初始化integrations节点
        Map<String, Object> integrationsNode = fullConfig.computeIfAbsent(
            "integrations", k -> new LinkedHashMap<>()
        );

        // 只保存必要的字段：
        // 1. 标识字段（用于定位 JAR 包）：groupId, artifactId, version
        // 2. 运行时状态：enabled, state, update, _deleted, pendingVersion
        // 不保存静态依赖（dependencies）等其他配置，这些从 JAR 包读取
        Map<String, Object> runtimeConfig = new LinkedHashMap<>();

        // 保存标识字段（用于定位 JAR 包）
        if (integrationConfig.containsKey("groupId")) {
            runtimeConfig.put("groupId", integrationConfig.get("groupId"));
        }
        if (integrationConfig.containsKey("artifactId")) {
            runtimeConfig.put("artifactId", integrationConfig.get("artifactId"));
        }
        if (integrationConfig.containsKey("version")) {
            runtimeConfig.put("version", integrationConfig.get("version"));
        }

        // 保存运行时状态
        if (integrationConfig.containsKey("enabled")) {
            runtimeConfig.put("enabled", integrationConfig.get("enabled"));
        }
        if (integrationConfig.containsKey("state")) {
            runtimeConfig.put("state", integrationConfig.get("state"));
        }
        if (integrationConfig.containsKey("pendingVersion")) {
            runtimeConfig.put("pendingVersion", integrationConfig.get("pendingVersion"));
        }
        if (integrationConfig.containsKey("_deleted")) {
            runtimeConfig.put("_deleted", integrationConfig.get("_deleted"));
        }

        // 添加时间戳
        runtimeConfig.put("update", new Date().toString());

        // 添加/更新集成配置
        integrationsNode.put(integrationName, runtimeConfig);

        // 写回文件
        updateIntegrationsConfig(fullConfig);
    }

    // 覆盖更新core/integrations.yml完整配置
    public void updateIntegrationsConfig(Map<String, Map<String, Object>> config) {
        File configFile = new File(INTEGRATIONS_CONFIG_PATH);

        // 确保文件存在（不存在则创建空文件）
        if (!configFile.exists()) {
            createEmptyConfigFile(configFile);
        }

        try (FileOutputStream fos = new FileOutputStream(configFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {

            // 配置YAML输出格式为标准块格式
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);

            Yaml yaml = new Yaml(options);
            yaml.dump(config, writer);
        } catch (IOException e) {
            log.error("写入配置文件失败: " + e.getMessage());
        }
    }

    // 加载integrations/xx.yml单个集成的配置
    public Map<String, Object> loadConfig(String integrationName) {
        String filePath = String.format(INTEGRATION_ITEM_PATH, integrationName);
        try {
            Yaml yaml = new Yaml();
            return yaml.load(new FileInputStream(new File(filePath)));
        } catch (IOException e) {
            log.warn("读取{}集成配置失败: {}" , integrationName, e.getMessage());
            // 返回空配置
            return new HashMap<>();
        }
    }

    // 保存integrations/xx.yml单个集成的配置
    public void saveConfig(String integrationName, Map<String, Object> config) {
        String filePath = String.format(INTEGRATION_ITEM_PATH, integrationName);
        config.put("update", new Date());
        
        try (FileOutputStream fos = new FileOutputStream(new File(filePath));
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
            
            // 配置YAML输出格式
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));  // 东八区
            
            Yaml yaml = new Yaml(options);
            yaml.dump(config, writer);
        } catch (IOException e) {
            log.error("保存集成配置失败: " + e.getMessage());
            throw new RuntimeException("保存集成配置失败: " + e.getMessage());
        }
    }

    // // 保存integrations/xx.yml单个集成的配置
    // public void saveConfig(String integrationName, Map<String, Object> config) {
    //     String filePath = String.format(INTEGRATION_ITEM_PATH, integrationName);
    //     config.put("update", new Date());
        
    //     try (FileWriter writer = new FileWriter(new File(filePath))) {
    //         // 配置YAML输出格式为块格式（标准缩进）
    //         DumperOptions options = new DumperOptions();
    //         options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    //         options.setPrettyFlow(true);
    //         // options.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));  // 东八区

    //         Yaml yaml = new Yaml(options);
    //         yaml.dump(config, writer);
    //     } catch (IOException e) {
    //         log.error(e.getStackTrace().toString());
    //         throw new RuntimeException("保存集成配置失败: " + e.getMessage());
    //     }
    // }

    /**
     * 添加或修改属性到YAML数据中（支持嵌套路径）
     * 例如：setProperty(config, "parent.child.key", "value")
     * @param config YAML数据Map
     * @param path 键路径，使用点号分隔嵌套层级
     * @param value 要设置的值
     */
    @SuppressWarnings("unchecked")
    public void setProperty(Map<String, Object> config, String path, Object value) {
        if (config == null || path == null || path.isEmpty()) {
            return;
        }
        
        String[] pathParts = path.split("\\.");
        Map<String, Object> currentMap = config;
        
        // 遍历路径，创建嵌套Map结构
        for (int i = 0; i < pathParts.length - 1; i++) {
            String key = pathParts[i];
            Object existingValue = currentMap.get(key);
            
            // 如果当前节点不存在，创建新的Map
            if (existingValue == null) {
                Map<String, Object> newMap = new HashMap<>();
                currentMap.put(key, newMap);
                currentMap = newMap;
            } 
            // 如果当前节点是Map，继续遍历
            else if (existingValue instanceof Map) {
                currentMap = (Map<String, Object>) existingValue;
            } 
            // 如果当前节点不是Map，覆盖为新的Map（可能丢失原有数据）
            else {
                Map<String, Object> newMap = new HashMap<>();
                currentMap.put(key, newMap);
                currentMap = newMap;
            }
        }
        
        // 设置最终的键值对
        String finalKey = pathParts[pathParts.length - 1];
        currentMap.put(finalKey, value);
    }
    
    /**
     * 删除属性（支持嵌套路径），不会保存到yml文件
     * @param config YAML数据Map
     * @param path 要删除的键路径，例如 "asd.dds.test"
     * @return 是否成功删除
     *     * 如果路径不存在或正常删除，返回true
     *     * 如果路径中有任何节点不是Map类型，返回false
     */
    @SuppressWarnings("unchecked")
    public boolean removeProperty(Map<String, Object> config, String path) {
        if (config == null || path == null || path.isEmpty()) {
            return false;
        }
        
        String[] pathParts = path.split("\\.");
        
        // 处理单级路径
        if (pathParts.length == 1) {
            Object  v = config.getOrDefault(path, null);
            return v == null || config.remove(path) != null;
        }
        
        // 处理多级嵌套路径
        Map<String, Object> currentMap = config;
        for (int i = 0; i < pathParts.length - 1; i++) {
            String key = pathParts[i];
            Object value = currentMap.getOrDefault(key, null);
            
            // 如果路径中的节点不存在，直接返回失败
            if (value == null) {
                return true;
            }
            
            // 如果节点不是Map类型，无法继续遍历，返回失败
            if (!(value instanceof Map)) {
                return false;
            }
            
            currentMap = (Map<String, Object>) value;
        }
        
        // 删除最后一级的键
        String lastKey = pathParts[pathParts.length - 1];
        Object  v = currentMap.getOrDefault(lastKey, null);
        return v == null || currentMap.remove(lastKey) != null;
    }

    // 创建空配置文件
    private void createEmptyConfigFile(File file) {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            
            if (file.createNewFile()) {
                try (FileWriter writer = new FileWriter(file)) {
                     DumperOptions options = new DumperOptions();
                    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // 关键配置
                    
                    Yaml yaml = new Yaml(options);
                    // 创建空配置
                    Map<String, Object> emptyConfig = new HashMap<>();
                    emptyConfig.put("integrations", new LinkedHashMap<>());
                    
                    yaml.dump(emptyConfig, writer);
                }
                log.info("已创建空配置文件: " + INTEGRATIONS_CONFIG_PATH);
            }
        } catch (IOException e) {
            log.error("创建配置文件失败: " + e.getStackTrace().toString());
            throw new RuntimeException("创建配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 广度优先查找最近的父类加载器
     * 
     * @param loadOrder 集成信息的加载顺序列表
     * @param integrationRegistry 集成注册表
     * @param info 当前集成信息
     * @return 找到的最近的childClassLoader，若未找到则返回null
     */
    public URLClassLoader findParentClassloader(List<IntegrationInfo> loadOrder, 
                                              IntegrationRegistry integrationRegistry, 
                                              IntegrationInfo info) {
        // 创建队列用于广度优先搜索
        Queue<String> queue = new LinkedList<>();
        // 记录已访问过的artifactId，避免循环依赖导致的无限循环
        Set<String> visited = new HashSet<>();

        // 初始化队列，添加当前info的所有依赖
        List<com.ecat.core.Integration.DependencyInfo> dependencyInfoList = info.getDependencyInfoList();
        if (dependencyInfoList != null) {
            for (com.ecat.core.Integration.DependencyInfo dep : dependencyInfoList) {
                String depCoordinate = dep.getCoordinate();
                if (!visited.contains(depCoordinate)) {
                    queue.add(depCoordinate);
                    visited.add(depCoordinate);
                }
            }
        }

        // 广度优先搜索
        while (!queue.isEmpty()) {
            // 处理当前层级的所有依赖
            String artifactId = queue.poll();
            if (artifactId == null) continue;

            // 查找对应的IntegrationBase
            IntegrationBase integration = integrationRegistry.getIntegration(artifactId);
            if (integration != null && integration.getLoadOption() != null) {
                // 检查是否存在childClassLoader
                URLClassLoader childClassLoader = integration.getLoadOption().getChildClassLoader();
                if (childClassLoader != null) {
                    return childClassLoader;
                }
            }

            // 如果当前依赖没有找到classLoader，查找它的依赖并加入队列（下一层级）
            IntegrationInfo dependencyInfo = findIntegrationInfoByCoordinate(loadOrder, artifactId);
            if (dependencyInfo != null) {
                List<com.ecat.core.Integration.DependencyInfo> nextDependencyInfoList = dependencyInfo.getDependencyInfoList();
                if (nextDependencyInfoList != null) {
                    for (com.ecat.core.Integration.DependencyInfo dep : nextDependencyInfoList) {
                        String nextDepCoordinate = dep.getCoordinate();
                        if (!visited.contains(nextDepCoordinate)) {
                            queue.add(nextDepCoordinate);
                            visited.add(nextDepCoordinate);
                        }
                    }
                }
            }
        }

        // 未找到任何可用的childClassLoader
        return null;
    }
    
    /**
     * 根据coordinate从loadOrder列表中查找对应的IntegrationInfo
     *
     * @param loadOrder 集成信息列表
     * @param coordinate 要查找的coordinate (groupId:artifactId)
     * @return 对应的IntegrationInfo，若未找到则返回null
     */
    private IntegrationInfo findIntegrationInfoByCoordinate(List<IntegrationInfo> loadOrder, String coordinate) {
        if (loadOrder == null || coordinate == null) {
            return null;
        }

        for (IntegrationInfo info : loadOrder) {
            if (coordinate.equals(info.getCoordinate())) {
                return info;
            }
        }
        return null;
    }

    // ========== 启动时清理方法 ==========

    /**
     * 清理标记为删除的集成（PENDING_REMOVED）
     *
     * 在系统启动时调用，清理所有标记为 _deleted=true 的集成配置
     */
    private void cleanupPendingRemovedIntegrations(Map<String, Map<String, Object>> integrationsConfig) {
        Map<String, Object> integrations = integrationsConfig.getOrDefault("integrations", new HashMap<>());

        // 找出所有需要清理的集成
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Object> entry : integrations.entrySet()) {
            String coordinate = entry.getKey();
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) entry.getValue();
                Boolean deleted = (Boolean) config.get("_deleted");
                if (deleted != null && deleted) {
                    toRemove.add(coordinate);
                    log.info("清理已卸载的集成: {}", coordinate);
                }
            }
        }

        // 从配置中移除
        for (String coordinate : toRemove) {
            integrations.remove(coordinate);
        }

        // 更新配置文件
        if (!toRemove.isEmpty()) {
            updateIntegrationsConfig(integrationsConfig);
            log.info("已清理 {} 个已卸载的集成", toRemove.size());
        }
    }

    // ========== 依赖检查辅助方法 ==========

    /**
     * 查找依赖指定集成的所有集成
     *
     * @param coordinate 集成坐标
     * @return 依赖者列表
     */
    private List<String> findDependents(String coordinate) {
        Map<String, Map<String, Object>> config = loadIntegrationsConfig();
        Map<String, Object> integrations = config.getOrDefault("integrations", new HashMap<>());
        List<String> dependents = new ArrayList<>();

        for (Map.Entry<String, Object> entry : integrations.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> integrationConfig = (Map<String, Object>) value;
                Boolean enabled = (Boolean) integrationConfig.get("enabled");

                // 跳过自己和禁用的
                if (key.equals(coordinate) || (enabled != null && !enabled)) {
                    continue;
                }

                // 检查是否依赖
                List<String> deps = findDependencies(key, integrationConfig);
                if (deps.contains(coordinate)) {
                    dependents.add(key);
                }
            }
        }

        return dependents;
    }

    /**
     * 查找指定集成的依赖列表
     *
     * @param coordinate 集成坐标
     * @param integrationConfig 集成配置
     * @return 依赖列表
     */
    @SuppressWarnings("unchecked")
    private List<String> findDependencies(String coordinate, Map<String, Object> integrationConfig) {
        List<String> dependencies = new ArrayList<>();

        String groupId = (String) integrationConfig.get("groupId");
        String artifactId = (String) integrationConfig.get("artifactId");
        String version = (String) integrationConfig.get("version");

        if (groupId == null || artifactId == null || version == null) {
            return dependencies;
        }

        String localRepoPath = System.getProperty("user.home") + "/.m2/repository";
        String jarPath = localRepoPath + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
        File jarFile = new File(jarPath);

        if (jarFile.exists()) {
            try {
                IntegrationInfo info = com.ecat.core.Utils.JarDependencyLoader.readPartialIntegrationInfoFromJar(jarFile);
                if (info.getDependencyInfoList() != null) {
                    for (com.ecat.core.Integration.DependencyInfo dep : info.getDependencyInfoList()) {
                        dependencies.add(dep.getCoordinate());
                    }
                }
            } catch (Exception e) {
                log.warn("读取 JAR 依赖信息失败: {} - {}", jarPath, e.getMessage());
            }
        }

        return dependencies;
    }

    /**
     * 检查是否可以停用/卸载（无依赖者）
     *
     * <p>关键逻辑：基于初始依赖关系判断，而不是运行时依赖关系。
     * 如果集成在初始加载时被依赖（使用共享 ClassLoader），则不能直接停用/卸载。
     * 只有重启后，ClassLoader 层级才会重新计算，此时才能调整。
     *
     * @param coordinate 集成坐标
     * @return 如果可以停用/卸载返回 true
     */
    private boolean canDisableOrRemove(String coordinate) {
        // 使用初始依赖关系快照，而不是运行时依赖关系
        List<String> initialDependents = getInitialDependents(coordinate);
        return initialDependents.isEmpty();
    }

    /**
     * 获取 JAR 文件路径
     *
     * @param groupId 组 ID
     * @param artifactId 构建 ID
     * @param version 版本
     * @return JAR 文件路径
     */
    private String getJarPath(String groupId, String artifactId, String version) {
        String localRepoPath = System.getProperty("user.home") + "/.m2/repository";
        return localRepoPath + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
    }

}
