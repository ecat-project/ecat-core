package com.ecat.core.I18n;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;
import org.mockito.MockitoAnnotations;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * ResourceLoader综合测试类
 * 包含基础功能测试和Mockito白盒测试
 *
 * 测试覆盖范围：
 * 1. 基础功能测试 - i18n控制标志、资源加载、多语言支持
 * 2. 白盒测试 - 使用Mockito spy直接验证loadJsonFile方法调用情况
 * 3. 边界情况测试 - 异常处理、null响应、参数验证
 *
 * @author coffee
 */
public class ResourceLoaderTest {

    private boolean originalLoadI18nResources;
    @Spy
    private ResourceLoader resourceLoader;

    @Before
    public void setUp() {
        // 保存原始设置
        originalLoadI18nResources = ResourceLoader.isLoadI18nResources();

        // 重置为默认状态
        ResourceLoader.setLoadI18nResources(true);

        // 创建ResourceLoader实例并用spy包装（用于白盒测试）
        resourceLoader = new ResourceLoader(I18nHelper.class, I18nHelper.class.getClassLoader());
        MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() {
        // 恢复原始设置
        ResourceLoader.setLoadI18nResources(originalLoadI18nResources);
    }

    // ==================== 基础功能测试 ====================

    /**
     * 测试默认行为
     * 验证ResourceLoader在默认情况下能正常加载资源
     */
    @Test
    public void testDefaultBehavior() {
        ResourceLoader loader = new ResourceLoader(I18nHelper.class, I18nHelper.class.getClassLoader());
        Map<String, Object> result = loader.loadResources(Locale.US);
        assertNotNull("默认情况下应该能加载到资源", result);
        assertFalse("默认情况下资源不应该为空", result.isEmpty());
    }

    /**
     * 测试i18n控制标志功能
     * 验证静态控制标志可以正确控制i18n资源的加载行为
     */
    @Test
    public void testI18nControlFlagFunctionality() {
        // 验证默认状态
        assertTrue("默认应该启用i18n资源加载", ResourceLoader.isLoadI18nResources());

        // 测试设置为false
        ResourceLoader.setLoadI18nResources(false);
        assertFalse("应该能正确禁用i18n资源加载", ResourceLoader.isLoadI18nResources());

        // 测试设置回true
        ResourceLoader.setLoadI18nResources(true);
        assertTrue("应该能重新启用i18n资源加载", ResourceLoader.isLoadI18nResources());
    }

    /**
     * 测试启用i18n时的资源加载
     * 验证当i18n启用时，系统会同时加载基础资源和翻译资源
     */
    @Test
    public void testResourceLoadingWithI18nEnabled() {
        ResourceLoader.setLoadI18nResources(true);
        ResourceLoader loader = new ResourceLoader(I18nHelper.class, I18nHelper.class.getClassLoader());
        Map<String, Object> result = loader.loadResources(Locale.US);

        assertNotNull("启用i18n时结果不应为null", result);
        assertFalse("启用i18n时结果不应为空", result.isEmpty());
        // 启用i18n时应该同时包含基础资源和翻译资源
        assertTrue("启用i18n时应该有更多资源", result.size() > 0);
    }

    /**
     * 测试禁用i18n时的资源加载
     * 验证当i18n禁用时，系统只加载基础资源，不加载翻译资源
     */
    @Test
    public void testResourceLoadingWithI18nDisabled() {
        ResourceLoader.setLoadI18nResources(false);
        ResourceLoader loader = new ResourceLoader(I18nHelper.class, I18nHelper.class.getClassLoader());
        Map<String, Object> result = loader.loadResources(Locale.US);

        assertNotNull("禁用i18n时结果不应为null", result);
        assertFalse("禁用i18n时基础资源不应为空", result.isEmpty());
        // 禁用i18n时应该只有基础资源
        assertTrue("禁用i18n时应该有基础资源", result.size() > 0);
    }

    /**
     * 测试多个ResourceLoader实例的状态隔离
     * 验证所有实例都遵循同一个静态设置
     */
    @Test
    public void testResourceLoaderStateIsolation() {
        // 创建两个ResourceLoader实例
        ResourceLoader loader1 = new ResourceLoader(I18nHelper.class, I18nHelper.class.getClassLoader());
        ResourceLoader loader2 = new ResourceLoader(this.getClass(), this.getClass().getClassLoader());

        // 设置i18n加载为false
        ResourceLoader.setLoadI18nResources(false);

        // 两个loader都应该遵循静态设置
        Map<String, Object> resources1 = loader1.loadResources(Locale.getDefault());
        Map<String, Object> resources2 = loader2.loadResources(Locale.getDefault());

        assertNotNull("第一个loader的结果不应为null", resources1);
        assertNotNull("第二个loader的结果不应为null", resources2);

        // 设置i18n加载为true
        ResourceLoader.setLoadI18nResources(true);

        // 现在两个loader都应该加载i18n资源
        resources1 = loader1.loadResources(Locale.getDefault());
        resources2 = loader2.loadResources(Locale.getDefault());

        assertNotNull("启用i18n后第一个loader的结果不应为null", resources1);
        assertNotNull("启用i18n后第二个loader的结果不应为null", resources2);
    }

    /**
     * 测试动态切换i18n加载状态
     * 验证可以在运行时动态切换i18n加载状态
     */
    @Test
    public void testDynamicToggleI18nLoading() {
        ResourceLoader loader = new ResourceLoader(I18nHelper.class, I18nHelper.class.getClassLoader());

        // 开始时启用i18n
        ResourceLoader.setLoadI18nResources(true);
        Map<String, Object> resourcesWithI18n = loader.loadResources(Locale.getDefault());

        // 禁用i18n
        ResourceLoader.setLoadI18nResources(false);
        Map<String, Object> resourcesWithoutI18n = loader.loadResources(Locale.getDefault());

        // 两个都应该返回有效资源
        assertNotNull("启用i18n时结果不应为null", resourcesWithI18n);
        assertNotNull("禁用i18n时结果不应为null", resourcesWithoutI18n);

        // 重新启用i18n
        ResourceLoader.setLoadI18nResources(true);
        Map<String, Object> resourcesWithI18nAgain = loader.loadResources(Locale.getDefault());

        assertNotNull("重新启用i18n后结果不应为null", resourcesWithI18nAgain);
    }

    /**
     * 测试多语言环境支持
     * 验证系统能正确处理不同的locale设置
     */
    @Test
    public void testMultipleLocaleSupport() {
        ResourceLoader loader = new ResourceLoader(I18nHelper.class, I18nHelper.class.getClassLoader());

        // 测试不同的locale
        Locale[] locales = {Locale.US, Locale.CHINA, Locale.UK};

        for (Locale locale : locales) {
            // 测试启用i18n
            ResourceLoader.setLoadI18nResources(true);
            Map<String, Object> resourcesWithI18n = loader.loadResources(locale);
            assertNotNull("启用i18n时 " + locale + " 的资源不应为null", resourcesWithI18n);

            // 测试禁用i18n
            ResourceLoader.setLoadI18nResources(false);
            Map<String, Object> resourcesWithoutI18n = loader.loadResources(locale);
            assertNotNull("禁用i18n时 " + locale + " 的资源不应为null", resourcesWithoutI18n);
        }
    }

    // ==================== Mockito白盒测试 ====================

    /**
     * 白盒测试：验证启用i18n时loadJsonFile方法调用情况
     * 使用Mockito spy直接验证loadJsonFile被调用的参数和次数
     */
    @Test
    public void testMockLoadJsonFileWhenI18nEnabled() {
        // 准备模拟数据
        Map<String, Object> mockBaseResources = new HashMap<>();
        mockBaseResources.put("base.key", "base value");

        Map<String, Object> mockI18nResources = new HashMap<>();
        mockI18nResources.put("test.key", "translated value");

        // 使用Mockito模拟loadJsonFile方法调用
        doReturn(mockBaseResources).when(resourceLoader).loadJsonFile("strings.json");
        doReturn(mockI18nResources).when(resourceLoader).loadJsonFile("i18n/en-US.json");

        // 启用i18n并测试
        ResourceLoader.setLoadI18nResources(true);
        Map<String, Object> result = resourceLoader.loadResources(Locale.US);

        // 验证结果
        assertNotNull("结果不应为null", result);
        assertEquals("应该包含基础和i18n两种资源", 2, result.size());
        assertEquals("基础资源应该存在", "base value", result.get("base.key"));
        assertEquals("翻译资源应该存在", "translated value", result.get("test.key"));

        // 验证loadJsonFile方法被调用了一次base资源和一次i18n资源
        verify(resourceLoader, times(1)).loadJsonFile("strings.json");
        verify(resourceLoader, times(1)).loadJsonFile("i18n/en-US.json");
    }

    /**
     * 白盒测试：验证禁用i18n时loadJsonFile方法调用情况
     * 确保禁用i18n时不会调用i18n相关的方法
     */
    @Test
    public void testMockLoadJsonFileWhenI18nDisabled() {
        // 准备模拟数据
        Map<String, Object> mockBaseResources = new HashMap<>();
        mockBaseResources.put("base.key", "base value");

        // 使用Mockito模拟loadJsonFile方法调用
        doReturn(mockBaseResources).when(resourceLoader).loadJsonFile("strings.json");
        doReturn(null).when(resourceLoader).loadJsonFile("i18n/en-US.json"); // 这个不应该被调用

        // 禁用i18n并测试
        ResourceLoader.setLoadI18nResources(false);
        Map<String, Object> result = resourceLoader.loadResources(Locale.US);

        // 验证结果
        assertNotNull("结果不应为null", result);
        assertEquals("应该只包含基础资源", 1, result.size());
        assertEquals("基础资源应该存在", "base value", result.get("base.key"));

        // 验证loadJsonFile方法只被调用了一次base资源，没有被调用i18n资源
        verify(resourceLoader, times(1)).loadJsonFile("strings.json");
        verify(resourceLoader, never()).loadJsonFile("i18n/en-US.json");
    }

    /**
     * 白盒测试：验证不同locale参数的loadJsonFile调用情况
     * 确保不同locale会调用对应的文件路径
     */
    @Test
    public void testMockLoadJsonFileWithDifferentLocaleParameters() {
        // 为不同的文件路径设置不同的模拟返回值
        Map<String, Object> stringsJsonResult = new HashMap<>();
        stringsJsonResult.put("from.strings", "strings content");

        Map<String, Object> enUsResult = new HashMap<>();
        enUsResult.put("from.en-us", "English content");

        Map<String, Object> zhCnResult = new HashMap<>();
        zhCnResult.put("from.zh-cn", "中文内容");

        // 使用Mockito设置不同的模拟返回值
        doReturn(stringsJsonResult).when(resourceLoader).loadJsonFile("strings.json");
        doReturn(enUsResult).when(resourceLoader).loadJsonFile("i18n/en-US.json");
        doReturn(zhCnResult).when(resourceLoader).loadJsonFile("i18n/zh-CN.json");
        doReturn(null).when(resourceLoader).loadJsonFile("i18n/en-GB.json");

        // 启用i18n
        ResourceLoader.setLoadI18nResources(true);

        // 测试不同locale
        Map<String, Object> enUsResultMap = resourceLoader.loadResources(Locale.US);
        assertEquals("英文内容应该存在", "English content", enUsResultMap.get("from.en-us"));

        Map<String, Object> zhCnResultMap = resourceLoader.loadResources(Locale.CHINA);
        assertEquals("中文内容应该存在", "中文内容", zhCnResultMap.get("from.zh-cn"));

        Map<String, Object> enGbResultMap = resourceLoader.loadResources(Locale.UK);
        assertNull("不应该存在en-GB内容", enGbResultMap.get("from.en-gb"));

        // 验证loadJsonFile方法被调用时使用了正确的参数
        verify(resourceLoader, times(3)).loadJsonFile("strings.json");
        verify(resourceLoader, times(1)).loadJsonFile("i18n/en-US.json");
        verify(resourceLoader, times(1)).loadJsonFile("i18n/zh-CN.json");
        verify(resourceLoader, times(1)).loadJsonFile("i18n/en-GB.json");
    }

    /**
     * 白盒测试：验证loadJsonFile方法调用顺序
     * 使用InOrder验证方法调用的先后顺序
     */
    @Test
    public void testMockLoadJsonFileExecutionOrder() {
        // 准备模拟数据
        Map<String, Object> baseResources = new HashMap<>();
        baseResources.put("base", "base");

        Map<String, Object> i18nResources = new HashMap<>();
        i18nResources.put("i18n", "i18n");

        // 使用Mockito设置模拟返回值
        doReturn(baseResources).when(resourceLoader).loadJsonFile("strings.json");
        doReturn(i18nResources).when(resourceLoader).loadJsonFile("i18n/en-US.json");

        // 启用i18n
        ResourceLoader.setLoadI18nResources(true);

        // 使用InOrder验证调用顺序
        org.mockito.InOrder inOrder = inOrder(resourceLoader);

        // 执行测试
        Map<String, Object> result = resourceLoader.loadResources(Locale.US);

        // 验证调用顺序：先调用base，后调用i18n
        inOrder.verify(resourceLoader, times(1)).loadJsonFile("strings.json");
        inOrder.verify(resourceLoader, times(1)).loadJsonFile("i18n/en-US.json");

        // 验证结果包含两个资源
        assertEquals(2, result.size());
        assertEquals("base", result.get("base"));
        assertEquals("i18n", result.get("i18n"));
    }

    /**
     * 白盒测试：验证多次调用的累加效果
     * 确保多次调用loadResources会正确累加loadJsonFile的调用次数
     */
    @Test
    public void testMockLoadJsonFileMultipleCallsTracking() {
        // 准备模拟数据
        Map<String, Object> mockResources = new HashMap<>();
        mockResources.put("key", "value");

        // 使用Mockito设置模拟返回值
        doReturn(mockResources).when(resourceLoader).loadJsonFile("strings.json");
        doReturn(mockResources).when(resourceLoader).loadJsonFile("i18n/en-US.json");
        doReturn(mockResources).when(resourceLoader).loadJsonFile("i18n/zh-CN.json");

        ResourceLoader.setLoadI18nResources(true);

        // 多次调用loadResources
        resourceLoader.loadResources(Locale.US);
        resourceLoader.loadResources(Locale.US);
        resourceLoader.loadResources(Locale.CHINA);

        // 验证调用次数累加
        verify(resourceLoader, times(3)).loadJsonFile("strings.json");
        verify(resourceLoader, times(2)).loadJsonFile("i18n/en-US.json");
        verify(resourceLoader, times(1)).loadJsonFile("i18n/zh-CN.json");
    }

    /**
     * 白盒测试：验证null响应处理
     * 确保当loadJsonFile返回null时，系统仍能正常工作
     */
    @Test
    public void testMockLoadJsonFileWithNullResponses() {
        // 使用Mockito设置某些文件返回null（模拟文件不存在）
        doReturn(null).when(resourceLoader).loadJsonFile("strings.json");
        doReturn(null).when(resourceLoader).loadJsonFile("i18n/en-US.json");

        ResourceLoader.setLoadI18nResources(true);

        // 测试
        Map<String, Object> result = resourceLoader.loadResources(Locale.US);

        // 验证结果为空map（因为base资源返回null）
        assertNotNull("结果不应为null", result);
        assertTrue("当基础资源为null时结果应该为空", result.isEmpty());

        // 验证loadJsonFile仍然被调用了
        verify(resourceLoader, times(1)).loadJsonFile("strings.json");
        verify(resourceLoader, times(1)).loadJsonFile("i18n/en-US.json");
    }

    /**
     * 白盒测试：验证Mockito参数匹配器
     * 使用anyString()等参数匹配器验证更复杂的调用场景
     */
    @Test
    public void testMockLoadJsonFileParameterValidation() {
        // 准备模拟数据
        Map<String, Object> specialResources = new HashMap<>();
        specialResources.put("special.key", "special value");

        // 设置特殊参数的模拟响应
        doReturn(specialResources).when(resourceLoader).loadJsonFile("special-file.json");
        doReturn(null).when(resourceLoader).loadJsonFile(anyString());

        // 启用i18n
        ResourceLoader.setLoadI18nResources(true);

        // 执行测试
        resourceLoader.loadResources(Locale.US);

        // 验证loadJsonFile被调用时使用了正确的参数格式
        verify(resourceLoader, times(1)).loadJsonFile("strings.json");
        verify(resourceLoader, times(1)).loadJsonFile("i18n/en-US.json");

        // 验证没有调用特殊文件
        verify(resourceLoader, never()).loadJsonFile("special-file.json");
    }
}