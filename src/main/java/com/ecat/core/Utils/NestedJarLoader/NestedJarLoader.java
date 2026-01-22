package com.ecat.core.Utils.NestedJarLoader;

import java.io.File;
import java.lang.reflect.Method;

/**
 * 嵌套JAR加载器入口类
 */
public class NestedJarLoader {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java NestedJarLoader <jar-file> <main-class> [args...]");
            System.exit(1);
        }

        String jarFilePath = args[0];
        String mainClassName = args[1];
        String[] appArgs = new String[args.length - 2];
        System.arraycopy(args, 2, appArgs, 0, appArgs.length);

        NestedJarClassLoader classLoader = null;
        try {
            // 创建自定义类加载器
            classLoader = new NestedJarClassLoader(jarFilePath, NestedJarLoader.class.getClassLoader());
            
            // 加载主类
            Class<?> mainClass = classLoader.loadClass(mainClassName);
            
            // 调用主类的main方法
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) appArgs);
        } catch (Exception e) {
            System.err.println("Failed to run application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // 关闭类加载器，释放资源
            if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (Exception e) {
                    // 忽略关闭异常
                }
            }
        }
    }
}
    