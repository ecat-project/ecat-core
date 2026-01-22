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

/**
 * IntegrationManager is responsible for loading and managing integrations.
 * 
 * @author coffee
 */
public class IntegrationManager {

    private static String INTEGRATIONS_CONFIG_PATH = ".ecat-data/core/integrations.yml";
    private static String INTEGRATION_ITEM_PATH = ".ecat-data/integrations/%s.yml";

    private final EcatCore core;
    private final IntegrationRegistry integrationRegistry;
    private final StateManager stateManager;
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final LoadJarUtils loadJarUtils;
    private final URLClassLoader restartClassLoader;

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
        
        // 添加/更新集成配置（带时间戳）
        integrationsNode.put(integrationName, new LinkedHashMap<String, Object>(integrationConfig) {{
            put("update", new Date().toString());
        }});
        
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
        
        try (FileWriter writer = new FileWriter(configFile)) {
            Yaml yaml = new Yaml();
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

}
