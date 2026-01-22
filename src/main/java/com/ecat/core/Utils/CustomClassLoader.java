package com.ecat.core.Utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.ecat.core.Utils.loader.LaunchedURLClassLoader;


/**
 * 自定义类加载器
 */
public class CustomClassLoader extends LaunchedURLClassLoader{


    // /**
    //  * 支持 nested jar 加载的构造方法
    //  * @param mainJarPath 主JAR文件的路径
    //  * @param parent 父类加载器
    //  * @throws IOException 加载JAR时发生IO异常
    //  */
    // public CustomClassLoader(String mainJarPath, ClassLoader parent) throws IOException {
    //     super(mainJarPath, parent);
    // }

    /**
     * 普通加载，不支持嵌套JAR加载的构造方法
     * @param urls JAR文件的URL数组
     * @param classLoader 父类加载器
     */
    public CustomClassLoader(URL[] urls, ClassLoader classLoader) throws IOException  {
        super(urls, classLoader);
    }

    /**
     * 静态方法：解析指定JAR文件中lib目录下的所有JAR，返回它们的URL数组
     * @param jarPath JAR文件的路径
     * @return 嵌套JAR的URL数组，若没有则返回空数组
     * @throws IOException 当JAR文件无法打开或读取时抛出
     */
    public static URL[] getLibJarUrls(String jarPath) throws IOException {
        List<URL> libUrls = new ArrayList<>();
        File jarFile = new File(jarPath);
        
        // 验证JAR文件是否存在
        if (!jarFile.exists() || !jarFile.isFile()) {
            throw new IOException("JAR文件不存在或不是有效的文件: " + jarPath);
        }

        // 打开JAR文件并遍历所有条目
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // 筛选出lib目录下的JAR文件（排除目录本身）
                if (!entry.isDirectory() && entryName.startsWith("lib/") && entryName.endsWith(".jar")) {
                    // 构建嵌套JAR的URL（格式：jar:file:/path/to/main.jar!/lib/dependency.jar）
                    String nestedJarUrl = "jar:file:" + jarFile.getAbsolutePath() + "!/" + entryName + "!/";
                    try {
                        libUrls.add(new URL(nestedJarUrl));
                    } catch (MalformedURLException e) {
                        // 理论上不会发生，因为URL格式是严格构造的
                        throw new IOException("无效的嵌套JAR URL: " + nestedJarUrl, e);
                    }
                }
            }
        }

        // 转换为URL数组并返回（空列表会返回空数组）
        return libUrls.toArray(new URL[0]);
    }


}

