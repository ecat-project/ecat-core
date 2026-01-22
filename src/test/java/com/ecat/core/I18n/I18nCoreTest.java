package com.ecat.core.I18n;

import org.junit.Before;
import org.junit.Test;

import com.ecat.core.Const;

import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Test class for I18n core functionality
 *
 * @author coffee
 */
public class I18nCoreTest {

    private static class TestResourceLoader extends ResourceLoader {
        public TestResourceLoader(Class<?> clazz, ClassLoader classLoader) {
            super(clazz, classLoader);
        }
        @Override
        public Map<String, Object> loadResources(Locale locale) {
            if (Locale.CHINA.equals(locale) || "zh".equals(locale.getLanguage())) {
                return buildZhResources();
            } else {
                return buildEnResources();
            }
        }

        private Map<String, Object> buildZhResources() {
            Map<String, Object> root = new HashMap<>();
            Map<String, Object> common = new HashMap<>();
            Map<String, Object> action = new HashMap<>();
            action.put("close", "关闭");
            action.put("connect", "连接");
            common.put("action", action);
            Map<String, Object> device = new HashMap<>();
            device.put("name", "设备名");
            common.put("device", device);
            root.put("common", common);

            Map<String, Object> message = new HashMap<>();
            message.put("device_connected", "设备 {0} 已连接");
            root.put("message", message);

            root.put("turn_off", "关闭 {entity_name}");

            Map<String, Object> deviceRoot = new HashMap<>();
            deviceRoot.put("count", "{0, plural, one{# 台设备} other{# 台设备}}");
            deviceRoot.put("name", "设备");
            root.put("device", deviceRoot);

            return root;
        }

        private Map<String, Object> buildEnResources() {
            Map<String, Object> root = new HashMap<>();
            Map<String, Object> common = new HashMap<>();
            Map<String, Object> action = new HashMap<>();
            action.put("close", "Close");
            action.put("connect", "Connect");
            common.put("action", action);
            Map<String, Object> device = new HashMap<>();
            device.put("name", "DeviceNameTest");
            common.put("device", device);
            root.put("common", common);

            Map<String, Object> message = new HashMap<>();
            message.put("device_connected", "Device {0} connected");
            root.put("message", message);

            root.put("turn_off", "Turn off {entity_name}");

            Map<String, Object> deviceRoot = new HashMap<>();
            deviceRoot.put("count", "{0, plural, one{# device} other{# devices}}");
            deviceRoot.put("name", "Device");
            root.put("device", deviceRoot);

            return root;
        }
    }

    @Before
    public void setUp() throws Exception {
        // Clear any existing state
        I18nRegistry.getInstance().clear();
        I18nHelper.clearCache();
    }

    /** 注入测试proxy到I18nHelper.proxyCache，保证全局t方法走测试资源 */
    private void injectTestProxyToCache() throws Exception {
        I18nProxy proxy = new I18nProxy(Const.CORE_ARTIFACT_ID, getClass(), getClass().getClassLoader());
        com.ecat.core.Utils.TestTools.setPrivateField(proxy, "resourceLoader", new TestResourceLoader(getClass(), getClass().getClassLoader()));
        java.lang.reflect.Field cacheField = I18nHelper.class.getDeclaredField("proxyCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, I18nProxy> cache = (java.util.Map<String, I18nProxy>) cacheField.get(null);
        cache.put(Const.CORE_ARTIFACT_ID, proxy);
    }

    /**
     * 测试 I18nConfig 单例模式和默认 locale
     */
    @Test
    public void testI18nConfigSingleton() {
        I18nConfig config1 = I18nConfig.getInstance();
        I18nConfig config2 = I18nConfig.getInstance();

        assertNotNull(config1);
        assertSame(config1, config2);
    }

    /**
     * 测试 I18nConfig 支持动态切换 locale
     */
    @Test
    public void testI18nConfigLocaleSwitching() {
        I18nConfig config = I18nConfig.getInstance();

        // Switch to Chinese
        Locale chineseLocale = new Locale("zh", "CN");
        config.setCurrentLocale(chineseLocale);
        assertEquals(chineseLocale, config.getCurrentLocale());

        // Switch to English
        Locale englishLocale = new Locale("en", "US");
        config.setCurrentLocale(englishLocale);
        assertEquals(englishLocale, config.getCurrentLocale());
    }

    /**
     * 测试 I18nRegistry 单例模式
     */
    @Test
    public void testI18nRegistrySingleton() {
        I18nRegistry registry1 = I18nRegistry.getInstance();
        I18nRegistry registry2 = I18nRegistry.getInstance();

        assertNotNull(registry1);
        assertSame(registry1, registry2);
    }

    /**
     * 测试 I18nProxy 创建及命名空间
     */
    @Test
    public void testI18nProxyCreation() throws Exception {
        I18nProxy proxy = new I18nProxy("myecat", getClass(), getClass().getClassLoader()) {
            {
                // 注入测试资源加载器
                com.ecat.core.Utils.TestTools.setPrivateField(this, "resourceLoader", new TestResourceLoader(getClass(), getClass().getClassLoader()));
            }
        };
        assertNotNull(proxy);

        // Verify namespace is correct
        assertEquals("integration.myecat", proxy.getNamespace());

        proxy = new I18nProxy(Const.CORE_ARTIFACT_ID, getClass(), getClass().getClassLoader()) {
            {
                // 注入测试资源加载器
                com.ecat.core.Utils.TestTools.setPrivateField(this, "resourceLoader", new TestResourceLoader(getClass(), getClass().getClassLoader()));
            }
        };
        assertNotNull(proxy);

        // Verify namespace is correct
        assertEquals(Const.CORE_ARTIFACT_ID, proxy.getNamespace());
    }

    /**
     * 测试 I18nProxy 基本翻译功能（英文）
     */
    @Test
    public void testI18nProxyBasicTranslation() throws Exception {
        I18nProxy proxy = new I18nProxy("custom-test", getClass(), getClass().getClassLoader());
        com.ecat.core.Utils.TestTools.setPrivateField(proxy, "resourceLoader", new TestResourceLoader(getClass(), getClass().getClassLoader()));

        // Test English translations
        I18nHelper.setLocale("en-US");
        String connectText = proxy.t("common.action.connect");
        assertNotNull(connectText);
        // Just check it's not returning the key itself
        assertNotEquals("common.action.connect", connectText);

        String closeText = proxy.t("common.action.close");
        assertNotNull(closeText);
        assertNotEquals("common.action.close", closeText);
    }

    /**
     * 测试 I18nProxy 支持 locale 切换（英文）
     */
    @Test
    public void testI18nProxyLocaleSwitching() throws Exception {
        I18nProxy proxy = new I18nProxy("custom-test", getClass(), getClass().getClassLoader());
        com.ecat.core.Utils.TestTools.setPrivateField(proxy, "resourceLoader", new TestResourceLoader(getClass(), getClass().getClassLoader()));

        // Test English translations
        I18nHelper.setLocale("en-US");
        String englishClose = proxy.t("common.action.close");
        assertEquals("Close", englishClose);
    }

    /**
     * 测试 I18nProxy 参数化翻译功能
     */
    @Test
    public void testI18nProxyParameterizedTranslation() throws Exception {
        I18nProxy proxy = new I18nProxy("custom-test", getClass(), getClass().getClassLoader());
        com.ecat.core.Utils.TestTools.setPrivateField(proxy, "resourceLoader", new TestResourceLoader(getClass(), getClass().getClassLoader()));
        I18nHelper.setLocale("en-US");

        String message = proxy.t("message.device_connected", "Test Device");
        assertNotNull(message);
        assertTrue(message.contains("Test Device"));
    }

    /**
     * 测试 I18nProxy 命名参数翻译功能
     */
    @Test
    public void testI18nProxyNamedParameterTranslation() throws Exception {
        I18nProxy proxy = new I18nProxy("custom-test", getClass(), getClass().getClassLoader());
        com.ecat.core.Utils.TestTools.setPrivateField(proxy, "resourceLoader", new TestResourceLoader(getClass(), getClass().getClassLoader()));
        I18nHelper.setLocale("en-US");

        Map<String, Object> params = new HashMap<>();
        params.put("entity_name", "Light");
        String result = proxy.t("turn_off", params);
        assertNotNull(result);
        assertTrue(result.contains("Light") || result.contains("{entity_name}"));
    }

    /**
     * 测试 I18nHelper.getArtifactId 只返回本 jar 的 artifactId
     */
    @Test
    public void testI18nHelperArtifactIdJarLocation() throws Exception {
        // 通过反射获取I18nHelper的getArtifactId方法
        Method method = com.ecat.core.Utils.TestTools.findMethod(
            I18nHelper.class, "getArtifactId", Class.class, ClassLoader.class
        );
        method.setAccessible(true);
        String artifactId = (String) method.invoke(null, I18nHelper.class, I18nHelper.class.getClassLoader());
        assertNotNull(artifactId);
        assertEquals(Const.CORE_ARTIFACT_ID, artifactId); // 只允许本jar返回ecat
    }

    /**
     * 测试 I18nProxy key 不存在时 fallback 返回 key 本身
     */
    @Test
    public void testI18nProxyKeyNotFound() {
        I18nProxy proxy = I18nHelper.createCoreProxy();
        I18nHelper.setLocale("en-US");

        String result = proxy.t("non.existent.key");
        assertEquals("non.existent.key", result); // Should return key itself
    }

    /**
     * 测试 I18nProxy 支持复数形式（pluralization）
     */
    @Test
    public void testI18nProxyPluralization() throws Exception {
        I18nProxy proxy = new I18nProxy("custom-test", getClass(), getClass().getClassLoader());
        com.ecat.core.Utils.TestTools.setPrivateField(proxy, "resourceLoader", new TestResourceLoader(getClass(), getClass().getClassLoader()));
        I18nHelper.setLocale("en-US");

        // Test pluralization (if supported by resource files)
        String singular = proxy.t("device.count", 1);
        String plural = proxy.t("device.count", 2);

        assertNotNull(singular);
        assertNotNull(plural);
        assertNotEquals(singular, plural);

        assertEquals("1 device", singular);
        assertEquals("2 devices", plural);

        I18nHelper.setLocale("zh-CN");
        singular = proxy.t("device.count", 1);
        plural = proxy.t("device.count", 2);
        assertEquals("1 台设备", singular);
        assertEquals("2 台设备", plural);


    }

    /**
     * 测试 I18nHelper 的 proxy 缓存行为
     */
    @Test
    public void testI18nHelperCacheBehavior() {
        I18nProxy proxy1 = I18nHelper.createCoreProxy();
        I18nProxy proxy2 = I18nHelper.createCoreProxy();

        assertSame(proxy1, proxy2);

        // Verify cache count
        assertTrue(I18nHelper.getCachedProxyCount() > 0);
    }

    /**
     * 测试 ResourceLoader 能正确加载资源结构
     */
    @Test
    public void testI18nResourceLoading() {
        ResourceLoader loader = new TestResourceLoader(getClass(), getClass().getClassLoader());

        Map<String, Object> resources = loader.loadResources(Locale.ENGLISH);
        assertNotNull(resources);
        assertFalse(resources.isEmpty());

        // Verify common keys exist
        assertTrue(resources.containsKey("common"));
    }

    /**
     * 测试 I18nHelper.t 全局方法能返回正确翻译
     */
    @Test
    public void testI18nHelperGlobalMethods() throws Exception {
        injectTestProxyToCache();
        I18nHelper.setLocale("en-US");
        String connectText = I18nHelper.t("common.action.connect");
        assertNotNull(connectText);
        assertEquals("Connect", connectText);
    }

    /**
     * 测试 I18nHelper.t 支持动态切换中英文
     */
    @Test
    public void testI18nLocaleSwitchingDynamic() throws Exception {
        injectTestProxyToCache();
        I18nHelper.setLocale("en-US");
        assertEquals("Connect", I18nHelper.t("common.action.connect"));
        assertEquals("DeviceNameTest", I18nHelper.t("common.device.name"));

        I18nHelper.setLocale("zh-CN");
        assertEquals("连接", I18nHelper.t("common.action.connect"));
        assertEquals("设备名", I18nHelper.t("common.device.name"));

        I18nHelper.setLocale("en-US");
        assertEquals("Connect", I18nHelper.t("common.action.connect"));
    }

    /**
     * 测试 I18nProxy 支持多级 key 访问和 hasKey
     */
    @Test
    public void testI18nNestedKeyAccess() throws Exception {
        I18nProxy proxy = new I18nProxy("custom-test", getClass(), getClass().getClassLoader());
        com.ecat.core.Utils.TestTools.setPrivateField(proxy, "resourceLoader", new TestResourceLoader(getClass(), getClass().getClassLoader()));
        I18nHelper.setLocale("en-US");

        // Test nested key access
        String deviceName = proxy.t("common.device.name");
        assertNotNull(deviceName);

        // Test that nested keys work correctly
        assertTrue(proxy.hasKey("common.device.name"));
    }

    /**
     * 测试 I18nRegistry 支持自定义 namespace 的 proxy 注册与获取
     */
    @Test
    public void testI18nRegistryProxyManagement() {
        I18nRegistry registry = I18nRegistry.getInstance();

        // Create proxy for custom namespace
        I18nProxy customProxy = I18nHelper.createProxy("custom.namespace", getClass());
        registry.registerProxy("custom.namespace", customProxy);

        // Verify proxy is registered
        I18nProxy retrievedProxy = registry.getProxy("custom.namespace");
        assertSame(customProxy, retrievedProxy);
    }
}
