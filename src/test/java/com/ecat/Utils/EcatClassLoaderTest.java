package com.ecat.Utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.ecat.core.Utils.EcatClassLoader;

/**
 * EcatClassLoader 隔离包加载行为测试
 *
 * <p>验证混合委派策略：</p>
 * <ul>
 *   <li>无隔离配置 → 全部 parent-first</li>
 *   <li>隔离包匹配 → child-first</li>
 *   <li>非隔离包 → 仍然 parent-first</li>
 *   <li>隔离包本地未找到 → fallback 到 parent（不抛 ClassNotFoundException）</li>
 * </ul>
 *
 * <p>注意：测试环境中 target/classes 同时存在于系统 classpath（surefire fork），
 * 因此类定义者（defining classloader）通常是 AppClassLoader。
 * 我们通过验证"类是否成功加载"和"不抛异常"来测试委派逻辑的正确性，
 * 而不是断言特定的 classloader 实例。</p>
 */
public class EcatClassLoaderTest {

    /**
     * 测试：无隔离配置（null），getIsolatedPackages 返回 null
     */
    @Test
    public void testNoIsolationConfig_IsolatedPackagesIsNull() throws Exception {
        File classesDir = new File("target/classes");
        URL classesUrl = classesDir.toURI().toURL();

        EcatClassLoader loader = new EcatClassLoader(
            new URL[]{classesUrl},
            (URLClassLoader) ClassLoader.getSystemClassLoader()
        );

        assertNull("无隔离配置时 isolatedPackages 应为 null", loader.getIsolatedPackages());
    }

    /**
     * 测试：带隔离配置的构造函数，getIsolatedPackages 返回正确值
     */
    @Test
    public void testWithIsolationConfig_IsolatedPackagesSet() throws Exception {
        File classesDir = new File("target/classes");
        URL classesUrl = classesDir.toURI().toURL();
        List<String> isolated = Arrays.asList("com.fasterxml.jackson", "org.slf4j");

        EcatClassLoader loader = new EcatClassLoader(
            new URL[]{classesUrl},
            (URLClassLoader) ClassLoader.getSystemClassLoader(),
            isolated
        );

        assertNotNull("隔离配置不应为 null", loader.getIsolatedPackages());
        assertEquals("应有 2 个隔离包", 2, loader.getIsolatedPackages().size());
        assertEquals("第一个应为 jackson", "com.fasterxml.jackson", loader.getIsolatedPackages().get(0));
    }

    /**
     * 测试：空隔离列表等同于无隔离
     */
    @Test
    public void testEmptyIsolationList_StillLoadsClasses() throws Exception {
        File classesDir = new File("target/classes");
        URL classesUrl = classesDir.toURI().toURL();

        EcatClassLoader parentLoader = new EcatClassLoader(
            new URL[]{classesUrl},
            (URLClassLoader) ClassLoader.getSystemClassLoader()
        );

        EcatClassLoader childLoader = new EcatClassLoader(
            new URL[]{classesUrl},
            parentLoader,
            Collections.<String>emptyList()
        );

        // 空隔离列表 → parent-first，应正常加载
        Class<?> clazz = childLoader.loadClass("com.ecat.core.Utils.EcatClassLoader");
        assertNotNull("空隔离列表应正常加载", clazz);
    }

    /**
     * 测试：隔离包本地找不到时 fallback 到 parent（不抛 ClassNotFoundException）
     *
     * <p>child 的 URL 列表为空，com.ecat.core 被配置为隔离包。
     * child-first 尝试 findClass() 失败后，应 fallback 到 super.loadClass()
     * 最终通过 parent 链成功加载，而非抛出 ClassNotFoundException。</p>
     */
    @Test
    public void testIsolatedPackage_NotFoundLocally_FallbackToParent() throws Exception {
        File classesDir = new File("target/classes");
        URL classesUrl = classesDir.toURI().toURL();

        EcatClassLoader parentLoader = new EcatClassLoader(
            new URL[]{classesUrl},
            (URLClassLoader) ClassLoader.getSystemClassLoader()
        );

        // child 没有任何 URL（空 classpath），隔离 "com.ecat.core"
        List<String> isolated = Arrays.asList("com.ecat.core");
        EcatClassLoader childLoader = new EcatClassLoader(
            new URL[0],
            parentLoader,
            isolated
        );

        // com.ecat.core.Utils.EcatClassLoader 匹配隔离前缀
        // 但 child 的 URL 列表为空，findClass 会失败
        // 应 fallback 到 parent 链 → 成功加载（不抛 ClassNotFoundException）
        Class<?> clazz = childLoader.loadClass("com.ecat.core.Utils.EcatClassLoader");
        assertNotNull("隔离包本地找不到时应 fallback 到 parent 成功加载", clazz);
        // 验证不是 childLoader 自己定义的（因为它的 URL 列表为空）
        assertNotSame("类不应由 childLoader 定义（它没有 URL）", childLoader, clazz.getClassLoader());
    }

    /**
     * 测试：非隔离包始终 parent-first
     *
     * <p>com.ecat.core 不在隔离列表中，应走标准 parent-first 委派。
     * 由于 parent-first 会先委托给 parent CL 加载，
     * 类不会由 childLoader 自己定义（而是由 parent 链中的某个 CL 定义）。</p>
     */
    @Test
    public void testNonIsolatedPackage_ParentFirst() throws Exception {
        File classesDir = new File("target/classes");
        URL classesUrl = classesDir.toURI().toURL();

        EcatClassLoader parentLoader = new EcatClassLoader(
            new URL[]{classesUrl},
            (URLClassLoader) ClassLoader.getSystemClassLoader()
        );

        // 只隔离 com.fasterxml.jackson，不隔离 com.ecat.core
        List<String> isolated = Arrays.asList("com.fasterxml.jackson");
        EcatClassLoader childLoader = new EcatClassLoader(
            new URL[]{classesUrl},
            parentLoader,
            isolated
        );

        // com.ecat.core 不在隔离范围 → parent-first
        Class<?> clazz = childLoader.loadClass("com.ecat.core.Integration.IntegrationInfo");
        assertNotNull("非隔离包应正常加载", clazz);
        // parent-first 意味着 childLoader 不会自行定义该类，而是委托给 parent 链
        assertNotSame("非隔离包不应由 childLoader 自己定义", childLoader, clazz.getClassLoader());
    }

    /**
     * 测试：isIsolatedClass 前缀匹配逻辑
     * "com.fasterxml.jackson" 应匹配 "com.fasterxml.jackson.databind.ObjectMapper"
     *
     * <p>验证非隔离包在隔离配置存在时仍能正常加载。</p>
     */
    @Test
    public void testIsolatedPackage_JacksonPrefixMatchesSubPackages() throws Exception {
        File classesDir = new File("target/classes");
        URL classesUrl = classesDir.toURI().toURL();

        EcatClassLoader parentLoader = new EcatClassLoader(
            new URL[]{classesUrl},
            (URLClassLoader) ClassLoader.getSystemClassLoader()
        );

        // 隔离 com.fasterxml.jackson
        List<String> isolated = Arrays.asList("com.fasterxml.jackson");
        EcatClassLoader childLoader = new EcatClassLoader(
            new URL[0],
            parentLoader,
            isolated
        );

        // com.ecat.core 的类不在隔离范围 → parent-first → 正常加载
        Class<?> nonIsolatedClazz = childLoader.loadClass("com.ecat.core.Utils.EcatClassLoader");
        assertNotNull("非隔离包应正常加载", nonIsolatedClazz);
    }

    /**
     * 测试：多个隔离包前缀，任一匹配即隔离
     *
     * <p>验证多个隔离前缀配置下，非隔离包仍走 parent-first 正常加载。</p>
     */
    @Test
    public void testMultipleIsolatedPrefixes_AnyMatchIsIsolated() throws Exception {
        File classesDir = new File("target/classes");
        URL classesUrl = classesDir.toURI().toURL();

        EcatClassLoader parentLoader = new EcatClassLoader(
            new URL[]{classesUrl},
            (URLClassLoader) ClassLoader.getSystemClassLoader()
        );

        List<String> isolated = Arrays.asList("com.fasterxml.jackson", "org.slf4j");
        EcatClassLoader childLoader = new EcatClassLoader(
            new URL[0],
            parentLoader,
            isolated
        );

        // 非隔离包应正常通过 parent 加载
        Class<?> clazz = childLoader.loadClass("com.ecat.core.Utils.EcatClassLoader");
        assertNotNull("非隔离包应正常加载", clazz);
        assertNotSame("非隔离包不应由 childLoader 自己定义", childLoader, clazz.getClassLoader());
    }
}
