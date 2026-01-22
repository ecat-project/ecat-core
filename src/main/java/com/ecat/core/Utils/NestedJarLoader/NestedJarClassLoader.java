package com.ecat.core.Utils.NestedJarLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 嵌套JAR类加载器，能够加载JAR文件中包含的嵌套JAR
 */
public class NestedJarClassLoader extends java.net.URLClassLoader {

    final List<Archive> archives = new ArrayList<>();
    private final JarURLConnectionHandler urlHandler;

    public NestedJarClassLoader(URL[] urls, ClassLoader classLoader) throws IOException {
        super(urls, classLoader);
        this.urlHandler = new JarURLConnectionHandler(this);
    }

    public NestedJarClassLoader(String jarFile, ClassLoader parent) throws IOException {
        super(new URL[] { new File(jarFile).toURI().toURL() }, parent);
        this.urlHandler = new JarURLConnectionHandler(this);
        this.archives.add(new JarFileArchive(new File(jarFile)));
        initNestedArchives(jarFile);
    }

    private void initNestedArchives(String jarFile) throws IOException {
        List<Archive> newArchives = new ArrayList<>();
        JarFileArchive archive = new JarFileArchive(new File(jarFile));
        Enumeration<Archive.ArchiveEntry> entries = archive.getEntries();
        while (entries.hasMoreElements()) {
            Archive.ArchiveEntry entry = entries.nextElement();
            String name = entry.getName();

            // 识别嵌套JAR（通常在BOOT-INF/lib目录下）
            if (name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")) {
                Archive nestedArchive = ((JarFileArchive) archive).createNestedArchive(entry);
                newArchives.add(nestedArchive);
            }
        }
        // for (Archive archive : archives) {
        //     Enumeration<Archive.ArchiveEntry> entries = archive.getEntries();
        //     while (entries.hasMoreElements()) {
        //         Archive.ArchiveEntry entry = entries.nextElement();
        //         String name = entry.getName();

        //         // 识别嵌套JAR（通常在BOOT-INF/lib目录下）
        //         if (name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")) {
        //             Archive nestedArchive = ((JarFileArchive) archive).createNestedArchive(entry);
        //             newArchives.add(nestedArchive);
        //         }
        //     }
        // }

        archives.addAll(newArchives);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException e) {
            String path = name.replace('.', '/') + ".class";
            try {
                for (Archive archive : archives) {
                    Archive.ArchiveEntry entry = archive.getEntry(path);
                    if (entry != null && !entry.isDirectory()) {
                        try (InputStream is = archive.getInputStream(entry)) {
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                baos.write(buffer, 0, len);
                            }
                            byte[] bytes = baos.toByteArray();
                            return defineClass(name, bytes, 0, bytes.length);
                        }
                    }
                }
            } catch (IOException ex) {
                throw new ClassNotFoundException("Failed to load class: " + name, ex);
            }
            throw e;
        }
    }

    @Override
    public URL findResource(String name) {
        URL url = super.findResource(name);
        if (url != null) return url;
        try {
            for (Archive archive : archives) {
                Archive.ArchiveEntry entry = archive.getEntry(name);
                if (entry != null && !entry.isDirectory()) {
                    return new URL(null, "jar:" + archive.getUrl() + "!/" + name, urlHandler);
                }
            }
        } catch (IOException e) {
            // 忽略异常，继续查找
        }
        return null;
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        Enumeration<URL> superUrls = super.findResources(name);
        List<URL> urls = new ArrayList<>();
        while (superUrls.hasMoreElements()) {
            urls.add(superUrls.nextElement());
        }
        for (Archive archive : archives) {
            Archive.ArchiveEntry entry = archive.getEntry(name);
            if (entry != null && !entry.isDirectory()) {
                urls.add(new URL(null, "jar:" + archive.getUrl() + "!/" + name, urlHandler));
            }
        }
        return new Enumeration<URL>() {
            private int index = 0;
            @Override
            public boolean hasMoreElements() {
                return index < urls.size();
            }
            @Override
            public URL nextElement() {
                return urls.get(index++);
            }
        };
    }

    public void close() throws IOException {
        for (Archive archive : archives) {
            archive.close();
        }
    }

    @Override
    public void addURL(URL url) {
        String path = url.getPath();
        // 判断是否为嵌套jar（如 jar:file:xxx.jar!/lib/xxx.jar）
        if (path.contains(".jar!/lib")) {
            try {
                // 解析主jar和嵌套entry
                String[] parts = path.split("!/");
                File mainJar = new File(parts[0].replaceFirst("^file:", ""));
                String entryName = parts[1];
                JarFileArchive mainArchive = new JarFileArchive(mainJar);
                Archive.ArchiveEntry entry = mainArchive.getEntry(entryName);
                if (entry != null && entry.getName().endsWith(".jar")) {
                    Archive nestedArchive = mainArchive.createNestedArchive(entry);
                    archives.add(nestedArchive);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to add nested jar: " + url, e);
            }
        } else {
            // super.addURL(url);
            try {
                String absolutePath = url.getPath(); // 拿到的是 /root/.../xxx.jar!/.../...

                // 去掉前缀 jar:file: 或 file:
                if (absolutePath.startsWith("file:")) {
                    absolutePath = absolutePath.substring(5);
                } else if (absolutePath.startsWith("jar:file:")) {
                    absolutePath = absolutePath.substring(9);
                }

                // 去掉第一个 ! 后面的内容
                int exclIndex = absolutePath.indexOf('!');
                if (exclIndex != -1) {
                    absolutePath = absolutePath.substring(0, exclIndex);
                }

                initNestedArchives(absolutePath);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public void addUrls(URL[] urls) {
        if (urls != null) {
            for (URL url : urls) {
                addURL(url);
            }
        }
    }
}
