package com.ecat.core.Utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Utils.loader.archive.Archive;
import com.ecat.core.Utils.loader.archive.JarFileArchive;

/**
 * LoadJarUtils is a utility class for dynamically loading JAR files
 * 
 * @author coffee
 */

public class LoadJarUtils {
    // from https://www.panziye.com/back/3329.html
    // from https://blog.csdn.net/qq_39879126/article/details/138337059
    
    private Map<String, URLClassLoader> myClassLoaderCenter = new ConcurrentHashMap<>();

    private final String ECAT_CORE_CLASSLOADER_KEY = "ecat"; // ecat core 的统一依赖集成的类加载器key，统一管理各integration公共依赖解决冲突

    private final String ECAT_DEPENDENT_CLASSLOADER_KEY = "ecat-dependent"; // ecat integration 被依赖的集成的类加载器key，统一管理各integration的ecat-config.yml依赖解决Class 共享

    public LoadJarUtils(EcatCore core, URLClassLoader classLoader) {
        try {
            EcatClassLoader ecatClassLoader = new EcatClassLoader(new URL[0], classLoader);
            myClassLoaderCenter.put(ECAT_CORE_CLASSLOADER_KEY, ecatClassLoader);
            EcatClassLoader ecatDependentClassLoader = new EcatClassLoader(new URL[0], ecatClassLoader);
            myClassLoaderCenter.put(ECAT_DEPENDENT_CLASSLOADER_KEY, ecatDependentClassLoader);
            
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public LoadJarResult loadJar(String jarPath, URLClassLoader classLoader) {
        return loadJar(jarPath, null, classLoader, null);
    }
    /**
     * 动态加载本地jar，参数是jar绝对物理路径，使用指定的类加载器
     */
    public LoadJarResult loadJar(String jarPath, String[] depJarPath, URLClassLoader classLoader, String returnClassName) {
        // DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        List<URL> urls = new ArrayList<>();

        // CustomClassLoader parentClassLoader = (CustomClassLoader) getEcatCoreClassLoader();

        try {
            // 测试验证方案
            // 加载依赖 JAR 文件的 URL
            // URL[] depUrls = CustomClassLoader.getLibJarUrls(jarPath);
            // urls.addAll(Arrays.asList(depUrls));
            // List<URL> urls11 = new ArrayList<>();
            // urls11.addAll(Arrays.asList(depUrls));


            
            try (Archive archive = new JarFileArchive(new File(jarPath))) {
                // 2. 收集所有嵌套 jar 和 classes 目录的 URL
                for (Iterator<Archive> it = archive.getNestedArchives(
                        entry -> (entry.isDirectory() && entry.getName().equals("BOOT-INF/classes/")) || entry.getName().endsWith(".jar"),
                        entry -> true); it.hasNext(); ) {
                    Archive nested = it.next();
                    urls.add(nested.getUrl());
                }
            }

            // 加载顺序为最后加载主 JAR 文件的 URL，兼容 ecat-core-ruoyi 和 ecat-adapter-ruoyi内的class加载顺序
            File mainJarFile = new File(jarPath);
            URL url = mainJarFile.toURI().toURL();
            url = new URL("jar:file:" + mainJarFile.getAbsolutePath() + "!/");
            urls.add(url);
            

            // 可行方案
            // 加载依赖 JAR 文件的 URL
            // if (depJarPath != null) {
            //     for (String depPath : depJarPath) {
            //         File depJarFile = new File(depPath);
            //         URL depUrl = depJarFile.toURI().toURL();
            //         depUrl = new URL("jar:file:" + depJarFile.getAbsolutePath() + "!/");
            //         urls.add(depUrl);
            //     }
            // }

            
            // classLoader=null表示使用common的classLoader，否则使用common的子类加载器
            URLClassLoader myClassLoader = classLoader;
            // 添加urls
            
            try {
                java.lang.reflect.Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                method.setAccessible(true);
                for (URL urlItem : urls) {
                    method.invoke(myClassLoader, urlItem);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to add URL to classloader", e);
            }

            // myClassLoader.addUrls(urls.toArray(new URL[0]));
            


            // if(classLoader == null){
            //     // myClassLoader = parentClassLoader;
            //     // myClassLoader = new EcatClassLoader(new URL[0], (URLClassLoader)parentClassLoader.getParent());
            //     myClassLoader = parentClassLoader;
            //     // myClassLoader.addUrls(urls.toArray(new URL[0]));
            //     myClassLoader.addUrls(urls.toArray(new URL[0]));
            // }
            // else{
            //     // myClassLoader = new CustomClassLoader(urls.toArray(new URL[0]), classLoader);
            //     myClassLoader = new CustomClassLoader(urls.toArray(new URL[0]), classLoader);
            // }
            // 创建自定义类加载器，使用同一个 ClassLoader 加载所有 JAR 文件
            // CustomClassLoader myClassLoader = new CustomClassLoader(urls.toArray(new URL[0]), parentClassLoader);

            String fileName = mainJarFile.getName();
            myClassLoaderCenter.put(fileName, myClassLoader); // 针对共享集成也会添加，其实与common同一个loader，移除时需要特殊判断处理

            // 先加载依赖 JAR 文件
            // Map<String, Class<?>> depLoadedClasses = new HashMap<>();
            // if (depJarPath != null) {
            //     for (String depPath : depJarPath) {
            //         depLoadedClasses.putAll(loadClassesFromJar(depPath, myClassLoader));
            //     }
            // }

            // 加载主 JAR 文件
            // Map<String, Class<?>> mainLoadedClasses = loadClassesFromJar(jarPath, myClassLoader);

            // 合并加载的类
            // Map<String, Class<?>> allLoadedClasses = new HashMap<>();
            // allLoadedClasses.putAll(depLoadedClasses);
            // allLoadedClasses.putAll(mainLoadedClasses);

            // Map<String, Class<?>> loadedClasses = myClassloader.getLoadedClasses();
            // for(Map.Entry<String, Class<?>> entry : allLoadedClasses.entrySet()){
            //     String className = entry.getKey();
            //     Class<?> clazz = entry.getValue();
            //     // 此处beanName使用全路径名是为了防止beanName重复
            //     String packageName = className.substring(0, className.lastIndexOf(".") + 1);
            //     String beanName = className.substring(className.lastIndexOf(".") + 1);
            //     beanName = packageName + beanName.substring(0, 1).toLowerCase() + beanName.substring(1);
            //     // 2. 将有@spring注解的类交给spring管理
            //     // 2.1 判断类的类型
            //     String flag = hasSpringAnnotation(clazz);
            //     if(!flag.equals("pass")){
            //         if(flag.equals("Controller") || flag.equals("RestController")){
            //             // 2.2交给spring管理
            //             BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
            //             AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
            //             // 2.3注册到spring的beanFactory中
            //             beanFactory.registerBeanDefinition(beanName, beanDefinition);
            //             // 2.4允许注入和反向注入
            //             beanFactory.autowireBean(clazz);
            //             beanFactory.initializeBean(clazz, beanName);
            //             // 2.5手动构建实例，并注入base service 防止卸载之后不再生成
            //             Object obj = clazz.newInstance();
            //             beanFactory.registerSingleton(beanName, obj);
            //             //3.特殊处理
            //             //3.1不同的spring核心类不同的处理，实例中只是写了contrller
            //             handle(flag,beanName);
            //         }
            //         // else if(flag.equals("Configuration")){
            //         //     registerConfiguration(clazz);
            //         // }
            //     }
            // }

            if (returnClassName == null) {
                return null;
            }
            Class<?> clazz = myClassLoader.loadClass(returnClassName);
            return new LoadJarResult((IntegrationBase)clazz.getDeclaredConstructor().newInstance(), myClassLoader);


        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;

    }

    public URLClassLoader getEcatCoreClassLoader() {
        return myClassLoaderCenter.get(ECAT_CORE_CLASSLOADER_KEY);
    }

    public URLClassLoader getEcatDependentClassLoader() {
        return myClassLoaderCenter.get(ECAT_DEPENDENT_CLASSLOADER_KEY);
    }

    // private Map<String, Class<?>> loadClassesFromJar(String jarPath, CustomClassLoader classLoader) throws Exception {
    //     File jarFile = new File(jarPath);
    //     URL url = jarFile.toURI().toURL();
    //     url = new URL("jar:file:" + jarFile.getAbsolutePath() + "!/");
    //     URLConnection urlConnection = url.openConnection();
    //     JarURLConnection jarURLConnection = (JarURLConnection) urlConnection;
    //     JarFile jarFileObj = jarURLConnection.getJarFile();
    //     Enumeration<JarEntry> entries = jarFileObj.entries();

    //     Map<String, Class<?>> loadedClasses = new HashMap<>();

    //     while (entries.hasMoreElements()) {
    //         JarEntry jarEntry = entries.nextElement();
    //         if (jarEntry.getName().endsWith(".class")) {
    //             String className = jarEntry.getName().replace('/', '.').substring(0, jarEntry.getName().length() - 6);
    //             if (className.equals("module-info")) {
    //                 continue;
    //             }
    //             Class<?> clazz = classLoader.loadClass(className);
    //             loadedClasses.put(className, clazz);
    //             System.out.println("加载类: " + className + "，类加载器: " + clazz.getClassLoader());
    //         }
    //     }

    //     return loadedClasses;
    // }
}
