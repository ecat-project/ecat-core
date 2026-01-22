package com.ecat.Utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.ecat.core.Integration.IntegrationInfo;
import com.ecat.core.Utils.JarDependencyLoader;
import com.ecat.core.Utils.JarScanException;
import com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport;

public class JarDependencyLoaderTest {
    @Test
    public void testNormalCase() {
        // 准备测试数据
        List<IntegrationInfo> integrationInfoList = new ArrayList<>();
        integrationInfoList.add(new IntegrationInfo("a", false, createDependencyInfoList(Arrays.asList("b", "c")), true, "classA", "groupA", "1.0", new WebPlatformSupport(), "^1.0.0"));
        integrationInfoList.add(new IntegrationInfo("b", false, createDependencyInfoList(Arrays.asList("e")), true, "classB", "groupB", "1.0", new WebPlatformSupport(), "^1.0.0"));
        integrationInfoList.add(new IntegrationInfo("c", false, createDependencyInfoList(Arrays.asList("b")), true, "classC", "groupC", "1.0", new WebPlatformSupport(), "^1.0.0"));
        integrationInfoList.add(new IntegrationInfo("d", false, createDependencyInfoList(Arrays.asList("c")), true, "classD", "groupD", "1.0", new WebPlatformSupport(), "^1.0.0"));
        integrationInfoList.add(new IntegrationInfo("e", false, null, true, "classE", "groupE", "1.0", new WebPlatformSupport(), "^1.0.0"));

        // 使用 coordinate (groupId:artifactId) 作为 key
        Map<String, List<String>> dependencyMap = new HashMap<>();
        dependencyMap.put("groupA:a", Arrays.asList("groupB:b", "groupC:c"));
        dependencyMap.put("groupB:b", Arrays.asList("groupE:e"));
        dependencyMap.put("groupC:c", Arrays.asList("groupB:b"));
        dependencyMap.put("groupD:d", Arrays.asList("groupC:c"));
        dependencyMap.put("groupE:e", Collections.emptyList());

        // 调用方法，测试kahn算法
        List<IntegrationInfo> loadOrder = JarDependencyLoader.getLoadOrder(integrationInfoList, dependencyMap);

        // 断言结果不为空
        assertNotNull(loadOrder);
        // 断言结果的大小与输入列表大小相同
        assertEquals(integrationInfoList.size(), loadOrder.size());
    }

    // 辅助方法：从旧格式的 artifactId 列表创建 DependencyInfo 列表
    private List<com.ecat.core.Integration.DependencyInfo> createDependencyInfoList(List<String> artifactIds) {
        if (artifactIds == null || artifactIds.isEmpty()) {
            return null;
        }
        List<com.ecat.core.Integration.DependencyInfo> list = new ArrayList<>();
        for (String artifactId : artifactIds) {
            list.add(new com.ecat.core.Integration.DependencyInfo(artifactId));
        }
        return list;
    }

    @Test
   public void testLoopCase() {
        // 准备测试数据，创建一个包含更多元素的环路依赖
        List<IntegrationInfo> integrationInfoList = new ArrayList<>();
        integrationInfoList.add(new IntegrationInfo("a", false, createDependencyInfoList(Arrays.asList("b")), true, "classA", "groupA", "1.0", new WebPlatformSupport(), "^1.0.0"));
        integrationInfoList.add(new IntegrationInfo("b", false, createDependencyInfoList(Arrays.asList("c")), true, "classB", "groupB", "1.0", new WebPlatformSupport(), "^1.0.0"));
        integrationInfoList.add(new IntegrationInfo("c", false, createDependencyInfoList(Arrays.asList("d")), true, "classC", "groupC", "1.0", new WebPlatformSupport(), "^1.0.0"));
        integrationInfoList.add(new IntegrationInfo("d", false, createDependencyInfoList(Arrays.asList("e")), true, "classD", "groupD", "1.0", new WebPlatformSupport(), "^1.0.0"));
        integrationInfoList.add(new IntegrationInfo("e", false, createDependencyInfoList(Arrays.asList("d")), true, "classE", "groupE", "1.0", new WebPlatformSupport(), "^1.0.0"));
        integrationInfoList.add(new IntegrationInfo("f", false, null, true, "classF", "groupF", "1.0", new WebPlatformSupport(), "^1.0.0"));

        // 使用 coordinate (groupId:artifactId) 作为 key
        Map<String, List<String>> dependencyMap = new HashMap<>();
        dependencyMap.put("groupA:a", Arrays.asList("groupB:b"));
        dependencyMap.put("groupB:b", Arrays.asList("groupC:c"));
        dependencyMap.put("groupC:c", Arrays.asList("groupD:d"));
        dependencyMap.put("groupD:d", Arrays.asList("groupE:e"));
        dependencyMap.put("groupE:e", Arrays.asList("groupD:d"));
        dependencyMap.put("groupF:f", Collections.emptyList());

        // 断言抛出 IllegalStateException 异常
        assertThrows(IllegalStateException.class, () -> {
            JarDependencyLoader.getLoadOrder(integrationInfoList, dependencyMap);
        });
    }

    /**
     * 测试：getLoadOrder() 能正确处理没有依赖的集成（dependencyInfoList 为 null）
     */
    @Test
    public void testGetLoadOrder_WithEmptyDependencies() {
        List<IntegrationInfo> integrationInfoList = new ArrayList<>();

        // 创建没有依赖的集成（dependencyInfoList 为 null）
        IntegrationInfo info1 = new IntegrationInfo(
            "module-a", false, null, true, "ClassA", "com.ecat", "1.0.0",
            new WebPlatformSupport(), "^1.0.0"
        );

        IntegrationInfo info2 = new IntegrationInfo(
            "module-b", false, null, true, "ClassB", "com.ecat", "1.0.0",
            new WebPlatformSupport(), "^1.0.0"
        );

        integrationInfoList.add(info1);
        integrationInfoList.add(info2);

        // 验证 dependencyInfoList 为 null（没有依赖）
        assertNull("info1.getDependencyInfoList() 应为 null", info1.getDependencyInfoList());
        assertNull("info2.getDependencyInfoList() 应为 null", info2.getDependencyInfoList());

        // 使用 coordinate (groupId:artifactId) 作为 key，空依赖列表
        Map<String, List<String>> dependencyMap = new HashMap<>();
        dependencyMap.put("com.ecat:module-a", Collections.emptyList());
        dependencyMap.put("com.ecat:module-b", Collections.emptyList());

        // 不应抛出 NPE
        List<IntegrationInfo> loadOrder = JarDependencyLoader.getLoadOrder(
            integrationInfoList, dependencyMap
        );

        assertNotNull("loadOrder 不应为 null", loadOrder);
        assertEquals("应返回所有集成", 2, loadOrder.size());
    }

    // ========== requires_core 测试 ==========

    /**
     * 测试：IntegrationInfo 构造函数正确设置 requiresCore 默认值
     */
    @Test
    public void testIntegrationInfo_RequiresCore_DefaultValue() {
        IntegrationInfo info = new IntegrationInfo(
            "test-artifact", false, null, true, "TestClass", "com.ecat", "1.0.0",
            new WebPlatformSupport(), null  // requiresCore 传入 null
        );

        assertEquals("requiresCore 默认值应为 '^1.0.0'", "^1.0.0", info.getRequiresCore());
    }

    /**
     * 测试：IntegrationInfo 构造函数正确设置自定义 requiresCore 值
     */
    @Test
    public void testIntegrationInfo_RequiresCore_CustomValue() {
        String customConstraint = ">=1.5.0,<2.0.0";
        IntegrationInfo info = new IntegrationInfo(
            "test-artifact", false, null, true, "TestClass", "com.ecat", "1.0.0",
            new WebPlatformSupport(), customConstraint
        );

        assertEquals("requiresCore 应为自定义值", customConstraint, info.getRequiresCore());
    }

    /**
     * 测试：IntegrationInfo requiresCore setter/getter
     */
    @Test
    public void testIntegrationInfo_RequiresCore_SetterGetter() {
        IntegrationInfo info = new IntegrationInfo(
            "test-artifact", false, null, true, "TestClass", "com.ecat", "1.0.0",
            new WebPlatformSupport(), "^1.0.0"
        );

        assertEquals("初始 requiresCore 应为 '^1.0.0'", "^1.0.0", info.getRequiresCore());

        // 修改 requiresCore
        info.setRequiresCore(">=2.0.0");
        assertEquals("修改后 requiresCore 应为 '>=2.0.0'", ">=2.0.0", info.getRequiresCore());
    }

    /**
     * 测试：各种 requiresCore 约束格式
     */
    @Test
    public void testIntegrationInfo_RequiresCore_VariousFormats() {
        // 测试 ^ 约束
        IntegrationInfo info1 = new IntegrationInfo(
            "test1", false, null, true, "Test1", "com.ecat", "1.0.0",
            new WebPlatformSupport(), "^1.2.0"
        );
        assertEquals("^1.2.0", info1.getRequiresCore());

        // 测试 ~ 约束
        IntegrationInfo info2 = new IntegrationInfo(
            "test2", false, null, true, "Test2", "com.ecat", "1.0.0",
            new WebPlatformSupport(), "~1.2.0"
        );
        assertEquals("~1.2.0", info2.getRequiresCore());

        // 测试 >= 约束
        IntegrationInfo info3 = new IntegrationInfo(
            "test3", false, null, true, "Test3", "com.ecat", "1.0.0",
            new WebPlatformSupport(), ">=1.0.0"
        );
        assertEquals(">=1.0.0", info3.getRequiresCore());

        // 测试组合约束
        IntegrationInfo info4 = new IntegrationInfo(
            "test4", false, null, true, "Test4", "com.ecat", "1.0.0",
            new WebPlatformSupport(), ">=1.0.0,<2.0.0"
        );
        assertEquals(">=1.0.0,<2.0.0", info4.getRequiresCore());
    }

    // ========== JarScanException 测试 ==========

    @Test
    public void testJarScanException_NoEntryClass_MessageOnly() throws Exception {
        String jarPath = "/path/to/test.jar";
        String message = "未找到继承自 IntegrationBase 的入口类";

        JarScanException exception = new JarScanException(
            jarPath,
            JarScanException.ScanFailureType.NO_ENTRY_CLASS,
            message
        );

        assertEquals("消息应匹配", message, exception.getMessage());
        assertEquals("JAR 路径应匹配", jarPath, exception.getJarFilePath());
        assertEquals("失败类型应匹配",
            JarScanException.ScanFailureType.NO_ENTRY_CLASS,
            exception.getFailureType());
        assertNull("原因应该为 null", exception.getCause());
    }

    @Test
    public void testJarScanException_MultipleEntryClasses_MessageOnly() throws Exception {
        String jarPath = "/path/to/test.jar";
        String message = "找到多个继承自 IntegrationBase 的类";

        JarScanException exception = new JarScanException(
            jarPath,
            JarScanException.ScanFailureType.MULTIPLE_ENTRY_CLASSES,
            message
        );

        assertEquals("消息应匹配", message, exception.getMessage());
        assertEquals("JAR 路径应匹配", jarPath, exception.getJarFilePath());
        assertEquals("失败类型应匹配",
            JarScanException.ScanFailureType.MULTIPLE_ENTRY_CLASSES,
            exception.getFailureType());
        assertNull("原因应该为 null", exception.getCause());
    }

    @Test
    public void testJarScanException_ScanError_WithCause() throws Exception {
        String jarPath = "/path/to/test.jar";
        String message = "扫描 JAR 时发生 I/O 错误";
        IOException cause = new IOException("JAR 文件损坏");

        JarScanException exception = new JarScanException(
            jarPath,
            JarScanException.ScanFailureType.SCAN_ERROR,
            message,
            cause
        );

        assertEquals("消息应匹配", message, exception.getMessage());
        assertEquals("JAR 路径应匹配", jarPath, exception.getJarFilePath());
        assertEquals("失败类型应匹配",
            JarScanException.ScanFailureType.SCAN_ERROR,
            exception.getFailureType());
        assertEquals("原因应匹配", cause, exception.getCause());
    }

    @Test
    public void testJarScanException_ScanFailureType_Descriptions() throws Exception {
        // 验证枚举类型的描述信息
        assertEquals("未找到入口类描述应匹配",
            "未找到继承自 IntegrationBase 的入口类",
            JarScanException.ScanFailureType.NO_ENTRY_CLASS.getDescription());

        assertEquals("多个入口类描述应匹配",
            "找到多个继承自 IntegrationBase 的类",
            JarScanException.ScanFailureType.MULTIPLE_ENTRY_CLASSES.getDescription());

        assertEquals("扫描错误描述应匹配",
            "扫描 JAR 时发生错误",
            JarScanException.ScanFailureType.SCAN_ERROR.getDescription());
    }

    @Test
    public void testJarScanException_AllFailureTypes() throws Exception {
        String jarPath = "/test.jar";

        // 测试所有失败类型都能正常创建
        JarScanException ex1 = new JarScanException(
            jarPath, JarScanException.ScanFailureType.NO_ENTRY_CLASS, "msg1");
        assertEquals(JarScanException.ScanFailureType.NO_ENTRY_CLASS, ex1.getFailureType());

        JarScanException ex2 = new JarScanException(
            jarPath, JarScanException.ScanFailureType.MULTIPLE_ENTRY_CLASSES, "msg2");
        assertEquals(JarScanException.ScanFailureType.MULTIPLE_ENTRY_CLASSES, ex2.getFailureType());

        JarScanException ex3 = new JarScanException(
            jarPath, JarScanException.ScanFailureType.SCAN_ERROR, "msg3");
        assertEquals(JarScanException.ScanFailureType.SCAN_ERROR, ex3.getFailureType());
    }
}
