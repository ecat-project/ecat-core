package com.ecat.core.Utils;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * EcatClassLoader is a custom class loader that extends URLClassLoader.
 * this class is used for springboot or other app frameworks loading classes, make app base classloader to load top level classes
 * 
 * @author coffee
 */

// 这个类是为了让应用的基础类加载器加载顶层类
// 实现一个隔离的类加载器，为每个集成提供一个虚拟独立的依赖管理（虚拟是因为parentClassLoader是同一个，独立是getURLs拿到的清单仅限集成所有）
// 目前还没有实现packages的隔离
public class EcatClassLoader extends CustomClassLoader {

    // private Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();
    // private ClassLoader parentClassLoader;
    private List<URL> urls = new ArrayList<>();

    public EcatClassLoader(URL[] urls, URLClassLoader parent) throws IOException {
        super(urls, parent);
        // parentClassLoader = parent;
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
