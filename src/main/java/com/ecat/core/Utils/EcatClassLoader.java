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

package com.ecat.core.Utils;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * EcatClassLoader — 支持隔离包配置的混合委派类加载器。
 * 为每个集成提供虚拟独立的依赖管理。
 *
 * <p>加载策略（优先级从高到低）：</p>
 * <ol>
 *   <li><b>缓存检查</b>：已加载的类直接返回</li>
 *   <li><b>隔离包匹配</b>：类名匹配 isolatedPackages 前缀时，child-first（子优先加载）</li>
 *   <li><b>标准双亲委派</b>：非隔离包走 parent-first</li>
 * </ol>
 *
 * @author coffee
 */

// 这个类是为了让应用的基础类加载器加载顶层类
// 实现一个隔离的类加载器，为每个集成提供一个虚拟独立的依赖管理（虚拟是因为parentClassLoader是同一个，独立是getURLs拿到的清单仅限集成所有）
// 已实现 packages 的隔离：通过 isolatedPackages 配置指定需要 child-first 加载的包前缀
public class EcatClassLoader extends CustomClassLoader {

    // private Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();
    // private ClassLoader parentClassLoader;
    private List<URL> urls = new ArrayList<>();

    /**
     * 需要隔离加载的包前缀列表。
     * 匹配这些前缀的类将由本 classloader 优先自行加载（child-first），
     * 不委托给父 classloader，避免版本冲突导致的 LinkageError。
     *
     * <p>null 或空列表表示不隔离，完全遵循标准双亲委派。</p>
     */
    private final List<String> isolatedPackages;

    /**
     * 标准构造函数（无隔离配置，向后兼容）
     */
    public EcatClassLoader(URL[] urls, URLClassLoader parent) throws IOException {
        super(urls, parent);
        // parentClassLoader = parent;
        this.isolatedPackages = null;
    }

    /**
     * 带隔离包配置的构造函数
     *
     * @param urls 初始 URL 列表
     * @param parent 父类加载器
     * @param isolatedPackages 需要隔离的包前缀列表，null 表示不隔离
     */
    public EcatClassLoader(URL[] urls, URLClassLoader parent, List<String> isolatedPackages) throws IOException {
        super(urls, parent);
        this.isolatedPackages = isolatedPackages;
    }

    // 添加新的 URL 路径
    @Override
    public void addURL(URL url) {
        try {
            // Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            // method.setAccessible(true);
            // method.invoke(parentClassLoader, url);
            super.addURL(url);
            this.urls.add(url);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add URL to class loader", e);
        }
    }

    // 添加多个 URL 路径
    @Override
    public void addUrls(URL[] urls) {
        for (URL url : urls) {
            addURL(url);
        }
    }

    @Override
    public URL[] getURLs() {
        return urls.toArray(new URL[0]);
    }

    /**
     * 获取隔离包配置（只读访问）
     * @return 隔离包前缀列表，可能为 null
     */
    public List<String> getIsolatedPackages() {
        return isolatedPackages;
    }

    /**
     * 混合委派 loadClass 实现。
     *
     * <p>执行规则：</p>
     * <ol>
     *   <li>缓存检查：findLoadedClass → 命中则直接返回</li>
     *   <li>隔离包匹配：className 匹配 isolatedPackages 前缀 → findClass（child-first）</li>
     *   <li>标准双亲委派：super.loadClass（parent-first）</li>
     * </ol>
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1. 缓存检查
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            return loadedClass;
        }

        // 2. 隔离包匹配 → child-first（子优先加载）
        if (isIsolatedClass(name)) {
            try {
                Class<?> clazz = findClass(name);
                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            } catch (ClassNotFoundException e) {
                // 隔离包在子 CL 中未找到，fallback 到父 CL
            }
        }

        // 3. 标准双亲委派（parent-first）
        return super.loadClass(name, resolve);
    }

    /**
     * 判断类名是否匹配隔离包前缀
     */
    private boolean isIsolatedClass(String className) {
        if (isolatedPackages == null || isolatedPackages.isEmpty()) {
            return false;
        }
        for (String prefix : isolatedPackages) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // // 将urlclassloader 和classloader没有实现的public方法全部重载
    // // ---------------------- 可委托给 parentClassLoader 实现的方法 ----------------------
    // @Override
    // public Class<?> loadClass(String name) throws ClassNotFoundException {
    //     // 委托给父类加载器加载类（URLClassLoader 已实现该逻辑）
    //     // 先从已加载的类集合中获取指定名称的类
    //     Class<?> clazz = loadedClasses.get(name);
    //     if (clazz != null) {
    //         return clazz;
    //     }
    //     // 如果类没有被加载，则使用loadClass方法加载
    //     try {
    //         clazz = parentClassLoader.loadClass(name);
    //         // 将加载的类添加到已加载的类集合中
    //         loadedClasses.put(name, clazz);
    //     } catch (ClassNotFoundException ex) {
    //         // 如果仍然无法加载，则抛出异常
    //         throw new ClassNotFoundException("Class not found: " + name, ex);
    //     }
    //     return clazz;
    // }

    // @Override
    // public URL getResource(String name) {
    //     // 直接使用父类的资源查找逻辑
    //     return parentClassLoader.getResource(name);
    // }

    // @Override
    // public Enumeration<URL> getResources(String name) throws IOException {
    //     // 委托父类获取资源枚举
    //     return parentClassLoader.getResources(name);
    // }

    // @Override
    // public InputStream getResourceAsStream(String name) {
    //     // 通过父类资源获取输入流
    //     return parentClassLoader.getResourceAsStream(name);
    // }


    // // ---------------------- 无法实现需抛异常的方法 ----------------------
    // @Override
    // public void close() throws IOException {
    //     throw new UnsupportedOperationException("close() is not supported");
    // }


}
