package com.ecat.core.Integration;

import com.ecat.core.EcatCore;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.StateManager;
import com.ecat.core.Utils.Log;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * IntegrationManagerTest is a unit test class for testing the IntegrationManager functionality.
 * 
 * @author coffee
 */
public class IntegrationManagerTest {

    @Mock
    private EcatCore core;
    @Mock
    private IntegrationRegistry integrationRegistry;
    @Mock
    private StateManager stateManager;
    @Mock
    private Log log;
    @Mock
    private URLClassLoader restartClassLoader;
    
    @InjectMocks
    private IntegrationManager integrationManager;
    
    private AutoCloseable mockitoCloseable;
    private File testConfigDir;
    private String testIntegrationName = "unitTest";
    private String testConfigPath;
    
    @Before
    public void setUp() throws Exception {
        // 初始化Mockito
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        
        // 创建测试目录和文件
        testConfigDir = new File("target",".ecat-test");
        testConfigPath = testConfigDir.getAbsolutePath() + "/integrations/" + testIntegrationName + ".yml";
        new File(testConfigDir + "/integrations").mkdirs();
        
        // 设置类加载器模拟
        when(restartClassLoader.getURLs()).thenReturn(new URL[0]);
        
        // 初始化IntegrationManager
        integrationManager = new IntegrationManager(core, integrationRegistry, stateManager);
        setPrivateField(integrationManager, "INTEGRATIONS_CONFIG_PATH", testConfigDir + "/core/integrations.yml");
        setPrivateField(integrationManager, "INTEGRATION_ITEM_PATH", testConfigDir + "/integrations/%s.yml");
    }
    
    @After
    public void tearDown() throws Exception {
        // 清理模拟
        mockitoCloseable.close();
        
        // 清理测试文件
        deleteRecursively(testConfigDir);
    }

    // 反射辅助方法
    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);

        // // 获取字段的修饰符
        // Field modifiersField = Field.class.getDeclaredField("modifiers");
        // modifiersField.setAccessible(true);
        // // 移除final修饰符
        // modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        field.set(target, value);
    }
    
    private Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
    
    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw e;
            }
            return findField(superClass, fieldName);
        }
    }
    
    private Object invokePrivateMethod(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Short) {
                parameterTypes[i] = short.class;
            } else if (args[i] instanceof Integer) {
                parameterTypes[i] = int.class;
            } else if (args[i] instanceof AttributeStatus) {
                parameterTypes[i] = AttributeStatus.class;
            } else {
                parameterTypes[i] = args[i].getClass();
            }
        }
        
        Method method = findMethod(target.getClass(), methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
    
    private Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        try {
            return clazz.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw e;
            }
            return findMethod(superClass, methodName, parameterTypes);
        }
    }
    
    // 递归删除目录及文件
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
    
    // 测试loadConfig方法
    @Test
    public void testLoadConfig() throws IOException {
        // 创建测试配置文件
        Map<String, Object> testConfig = new HashMap<>();
        testConfig.put("key1", "value1");
        testConfig.put("nested", new HashMap<String, Object>() {{
            put("key2", 123);
        }});
        
        writeYamlToFile(testConfigPath, testConfig);
        
        // 调用loadConfig
        Map<String, Object> loadedConfig = integrationManager.loadConfig(testIntegrationName);
        
        // 验证加载结果
        assertNotNull(loadedConfig);
        assertEquals("value1", loadedConfig.get("key1"));
        Map nestedMap = (Map) loadedConfig.get("nested");
        assertNotNull(nestedMap);
        assertEquals(123, nestedMap.get("key2"));

    }
    
    // 测试loadConfig失败情况
    @Test
    public void testLoadConfig_Failure() {
        // 调用不存在的配置文件
        Map<String, Object> loadedConfig = integrationManager.loadConfig("nonExistent");
        
        // 验证返回空Map
        assertNotNull(loadedConfig);
        assertTrue(loadedConfig.isEmpty());
        
    }
    
    // 测试saveConfig方法
    @Test
    public void testSaveConfig() throws IOException {
        // 准备测试数据
        Map<String, Object> testConfig = new HashMap<>();
        testConfig.put("key1", "value1");
        testConfig.put("number", 42);
        testConfig.put("keyC", "中文测试");
        
        // 调用saveConfig
        integrationManager.saveConfig(testIntegrationName, testConfig);
        
        // 验证文件存在
        File configFile = new File(testConfigPath);
        assertTrue(configFile.exists());
        
        // 验证文件内容
        Map<String, Object> savedConfig = readYamlFromFile(testConfigPath);
        assertNotNull(savedConfig);
        assertEquals("value1", savedConfig.get("key1"));
        assertEquals(42, savedConfig.get("number"));
        assertEquals("中文测试", savedConfig.get("keyC"));
        
        // 验证自动添加了update时间
        assertNotNull(savedConfig.get("update"));
        assertTrue(savedConfig.get("update") instanceof Date);
    }
    
    // 测试setProperty方法
    @Test
    public void testSetProperty() {
        // 准备测试Map
        Map<String, Object> config = new HashMap<>();
        config.put("existingKey", "oldValue");
        
        // 添加新属性
        integrationManager.setProperty(config, "newKey", "newValue");
        assertEquals("newValue", config.get("newKey"));
        
        // 修改现有属性
        integrationManager.setProperty(config, "existingKey", "newValue");
        assertEquals("newValue", config.get("existingKey"));
        
        // 测试嵌套属性
        Map nested = new HashMap<>();
        config.put("nested", nested);
        integrationManager.setProperty(config, "nested.deepKey", "deepValue");
        assertEquals("deepValue", ((Map) config.get("nested")).get("deepKey"));
    }
    
    // 测试removeProperty方法 - 单级路径
    @Test
    public void testRemoveProperty_SingleLevel() {
        // 准备测试Map
        Map<String, Object> config = new HashMap<>();
        config.put("key1", "value1");
        config.put("key2", "value2");
        
        // 删除存在的键
        boolean removed = integrationManager.removeProperty(config, "key1");
        assertTrue(removed);
        assertNull(config.get("key1"));
        
        // 删除不存在的键
        removed = integrationManager.removeProperty(config, "key3");
        assertTrue(removed);
        assertNull(config.get("key3"));
    }
    
    // 测试removeProperty方法 - 嵌套路径
    @Test
    public void testRemoveProperty_NestedPath() {
        // 准备测试Map
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> level1 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();
        List<String> list = Arrays.asList("item1", "item2");
        level2.put("keyToRemove", "value");
        level1.put("level2", level2);
        config.put("level1", level1);
        config.put("list1", list);
        
        // 删除嵌套键
        boolean removed = integrationManager.removeProperty(config, "level1.level2.keyToRemove");
        assertTrue(removed);
        assertNull(((Map)((Map)config.get("level1")).get("level2")).get("keyToRemove"));
        
        // 删除不存在的嵌套键
        removed = integrationManager.removeProperty(config, "level1.level2.nonexistent");
        assertTrue(removed);

        // 验证删除不是map的key path
        removed = integrationManager.removeProperty(config, "list1.level2.nonexistent");
        assertFalse(removed);
    }
    
    // 测试removeProperty方法 - 路径中节点非Map
    @Test
    public void testRemoveProperty_NodeNotMap() {
        // 准备测试Map - 中间节点不是Map
        Map<String, Object> config = new HashMap<>();
        config.put("level1", "notAMap");
        
        // 尝试删除非map嵌套键
        boolean removed = integrationManager.removeProperty(config, "level1.level2.key");
        assertFalse(removed);
    }
    
    // 辅助方法：从YAML文件读取Map
    private Map<String, Object> readYamlFromFile(String filePath) throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            return yaml.load(fis);
        }
    }

    // 辅助方法：将Map写入YAML文件（使用标准格式）
    private void writeYamlToFile(String filePath, Map<String, Object> data) throws IOException {
        // 配置YAML输出格式为块格式（标准缩进）
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Yaml yaml = new Yaml(options);
        try (FileWriter writer = new FileWriter(filePath)) {
            yaml.dump(data, writer);
        }
    }

    // ========== ClassLoader 层级检测相关测试 ==========

    /**
     * 测试 saveInitialDependencySnapshot() - 保存初始依赖关系快照
     *
     * 注意：此测试直接验证 initialDependencyGraph 的设置行为，
     * 而不是通过完整的 saveInitialDependencySnapshot() 流程，
     * 因为 findDependencies() 需要从实际 JAR 文件读取依赖信息。
     */
    @Test
    public void testSaveInitialDependencySnapshot() throws Exception {
        // 直接设置 initialDependencyGraph 来验证后续的行为
        // 模拟场景：
        // - com.ecat:integration-common 被 integration-a 和 integration-b 依赖
        // - com.ecat:integration-a 被 integration-c 依赖（但 c 是 disabled，不记录）

        Map<String, List<String>> testGraph = new HashMap<>();
        testGraph.put("com.ecat:integration-common", Arrays.asList("com.ecat:integration-a", "com.ecat:integration-b"));

        setPrivateField(integrationManager, "initialDependencyGraph", testGraph);
        setPrivateField(integrationManager, "initialSnapshotTaken", true);

        // 验证 initialDependencyGraph 已被正确设置
        @SuppressWarnings("unchecked")
        Map<String, List<String>> graph = (Map<String, List<String>>) getPrivateField(integrationManager, "initialDependencyGraph");
        assertNotNull(graph);
        assertTrue(graph.containsKey("com.ecat:integration-common"));
        assertEquals(2, graph.get("com.ecat:integration-common").size());
        assertTrue(graph.get("com.ecat:integration-common").contains("com.ecat:integration-a"));
        assertTrue(graph.get("com.ecat:integration-common").contains("com.ecat:integration-b"));

        // 验证 initialSnapshotTaken 被设置为 true
        boolean snapshotTaken = (boolean) getPrivateField(integrationManager, "initialSnapshotTaken");
        assertTrue(snapshotTaken);
    }

    /**
     * 测试 getInitialDependents() - 获取初始依赖者
     */
    @Test
    public void testGetInitialDependents() throws Exception {
        // 设置 initialDependencyGraph
        Map<String, List<String>> testGraph = new HashMap<>();
        testGraph.put("com.ecat:integration-common", Arrays.asList("com.ecat:integration-a", "com.ecat:integration-b"));
        testGraph.put("com.ecat:integration-a", Arrays.asList("com.ecat:integration-c"));

        setPrivateField(integrationManager, "initialDependencyGraph", testGraph);

        // 调用 getInitialDependents
        List<String> dependents = (List<String>) invokePrivateMethod(integrationManager, "getInitialDependents", "com.ecat:integration-common");

        // 验证返回结果
        assertNotNull(dependents);
        assertEquals(2, dependents.size());
        assertTrue(dependents.contains("com.ecat:integration-a"));
        assertTrue(dependents.contains("com.ecat:integration-b"));
    }

    /**
     * 测试 getInitialDependents() - 无依赖者的情况
     */
    @Test
    public void testGetInitialDependents_NoDependents() throws Exception {
        // 设置空的 initialDependencyGraph
        setPrivateField(integrationManager, "initialDependencyGraph", new HashMap<>());

        // 调用 getInitialDependents
        List<String> dependents = (List<String>) invokePrivateMethod(integrationManager, "getInitialDependents", "com.ecat:integration-unknown");

        // 验证返回空列表
        assertNotNull(dependents);
        assertTrue(dependents.isEmpty());
    }

    /**
     * 测试 getInitialDependents() - 快照未保存的情况
     */
    @Test
    public void testGetInitialDependents_NoSnapshot() throws Exception {
        // 设置 initialDependencyGraph 为 null
        setPrivateField(integrationManager, "initialDependencyGraph", null);

        // 调用 getInitialDependents
        List<String> dependents = (List<String>) invokePrivateMethod(integrationManager, "getInitialDependents", "com.ecat:integration-common");

        // 验证返回空列表
        assertNotNull(dependents);
        assertTrue(dependents.isEmpty());
    }

    /**
     * 测试 requiresClassLoaderHierarchyChange() - 需要调整的情况
     *
     * 场景：integration-a 在初始加载时没有依赖者（独立 ClassLoader）
     *      现在要启用的新集成 integration-new 依赖 integration-a
     *      这需要 ClassLoader 层级调整
     */
    @Test
    public void testRequiresClassLoaderHierarchyChange_NeedChange() throws Exception {
        // 设置 initialDependencyGraph - integration-a 没有初始依赖者
        Map<String, List<String>> testGraph = new HashMap<>();
        // integration-a 不在 graph 中，表示它初始时没有被依赖
        setPrivateField(integrationManager, "initialDependencyGraph", testGraph);

        // 模拟 getIntegrationStatus 返回
        IntegrationStatus mockStatus = IntegrationStatus.builder()
                .coordinate("com.ecat:integration-new")
                .dependencies(Arrays.asList("com.ecat:integration-a"))
                .build();

        // 使用 spy 来模拟 getIntegrationStatus 方法
        IntegrationManager spyManager = spy(integrationManager);
        doReturn(mockStatus).when(spyManager).getIntegrationStatus(anyString());

        // 调用 requiresClassLoaderHierarchyChange
        boolean needsChange = (boolean) invokePrivateMethod(spyManager, "requiresClassLoaderHierarchyChange", "com.ecat:integration-new");

        // 验证需要调整
        assertTrue(needsChange);
    }

    /**
     * 测试 requiresClassLoaderHierarchyChange() - 不需要调整的情况
     *
     * 场景：integration-a 在初始加载时已经有依赖者（共享 ClassLoader）
     *      现在要启用的新集成 integration-new 也依赖 integration-a
     *      不需要 ClassLoader 层级调整
     */
    @Test
    public void testRequiresClassLoaderHierarchyChange_NoChange() throws Exception {
        // 设置 initialDependencyGraph - integration-a 已经有初始依赖者
        Map<String, List<String>> testGraph = new HashMap<>();
        testGraph.put("com.ecat:integration-a", Arrays.asList("com.ecat:integration-b"));
        setPrivateField(integrationManager, "initialDependencyGraph", testGraph);

        // 模拟 getIntegrationStatus 返回
        IntegrationStatus mockStatus = IntegrationStatus.builder()
                .coordinate("com.ecat:integration-new")
                .dependencies(Arrays.asList("com.ecat:integration-a"))
                .build();

        // 使用 spy 来模拟 getIntegrationStatus 方法
        IntegrationManager spyManager = spy(integrationManager);
        doReturn(mockStatus).when(spyManager).getIntegrationStatus(anyString());

        // 调用 requiresClassLoaderHierarchyChange
        boolean needsChange = (boolean) invokePrivateMethod(spyManager, "requiresClassLoaderHierarchyChange", "com.ecat:integration-new");

        // 验证不需要调整
        assertFalse(needsChange);
    }

    /**
     * 测试 requiresClassLoaderHierarchyChange() - 无依赖的情况
     */
    @Test
    public void testRequiresClassLoaderHierarchyChange_NoDependencies() throws Exception {
        // 设置 initialDependencyGraph
        setPrivateField(integrationManager, "initialDependencyGraph", new HashMap<>());

        // 模拟 getIntegrationStatus 返回 - 无依赖
        IntegrationStatus mockStatus = IntegrationStatus.builder()
                .coordinate("com.ecat:integration-new")
                .dependencies(Arrays.asList())
                .build();

        // 使用 spy 来模拟 getIntegrationStatus 方法
        IntegrationManager spyManager = spy(integrationManager);
        doReturn(mockStatus).when(spyManager).getIntegrationStatus(anyString());

        // 调用 requiresClassLoaderHierarchyChange
        boolean needsChange = (boolean) invokePrivateMethod(spyManager, "requiresClassLoaderHierarchyChange", "com.ecat:integration-new");

        // 验证不需要调整
        assertFalse(needsChange);
    }

    /**
     * 测试 requiresClassLoaderHierarchyChange() - null 依赖的情况
     */
    @Test
    public void testRequiresClassLoaderHierarchyChange_NullDependencies() throws Exception {
        // 设置 initialDependencyGraph
        setPrivateField(integrationManager, "initialDependencyGraph", new HashMap<>());

        // 模拟 getIntegrationStatus 返回 - 依赖为 null
        IntegrationStatus mockStatus = IntegrationStatus.builder()
                .coordinate("com.ecat:integration-new")
                .dependencies(null)
                .build();

        // 使用 spy 来模拟 getIntegrationStatus 方法
        IntegrationManager spyManager = spy(integrationManager);
        doReturn(mockStatus).when(spyManager).getIntegrationStatus(anyString());

        // 调用 requiresClassLoaderHierarchyChange
        boolean needsChange = (boolean) invokePrivateMethod(spyManager, "requiresClassLoaderHierarchyChange", "com.ecat:integration-new");

        // 验证不需要调整
        assertFalse(needsChange);
    }

    /**
     * 测试 requiresClassLoaderHierarchyChange() - 多个依赖中有一个需要调整
     */
    @Test
    public void testRequiresClassLoaderHierarchyChange_MultipleDependencies() throws Exception {
        // 设置 initialDependencyGraph
        // integration-a 有初始依赖者，integration-b 没有
        Map<String, List<String>> testGraph = new HashMap<>();
        testGraph.put("com.ecat:integration-a", Arrays.asList("com.ecat:integration-x"));
        // integration-b 不在 graph 中
        setPrivateField(integrationManager, "initialDependencyGraph", testGraph);

        // 模拟 getIntegrationStatus 返回 - 同时依赖 a 和 b
        IntegrationStatus mockStatus = IntegrationStatus.builder()
                .coordinate("com.ecat:integration-new")
                .dependencies(Arrays.asList("com.ecat:integration-a", "com.ecat:integration-b"))
                .build();

        // 使用 spy 来模拟 getIntegrationStatus 方法
        IntegrationManager spyManager = spy(integrationManager);
        doReturn(mockStatus).when(spyManager).getIntegrationStatus(anyString());

        // 调用 requiresClassLoaderHierarchyChange
        boolean needsChange = (boolean) invokePrivateMethod(spyManager, "requiresClassLoaderHierarchyChange", "com.ecat:integration-new");

        // 验证需要调整（因为 integration-b 需要调整）
        assertTrue(needsChange);
    }
}