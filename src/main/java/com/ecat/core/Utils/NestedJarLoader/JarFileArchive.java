package com.ecat.core.Utils.NestedJarLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * JAR文件的归档实现
 */
public class JarFileArchive implements Archive {

    final JarFile jarFile;
    private final RandomAccessData jarData;
    private final String pathFromRoot;

    public JarFileArchive(File file) throws IOException {
        this(new NestedJarFile(file), new FileRandomAccessData(file), "");
    }

    public JarFileArchive(NestedJarFile jarFile, RandomAccessData jarData, String pathFromRoot) {
        this.jarFile = jarFile;
        this.jarData = jarData;
        this.pathFromRoot = pathFromRoot;
    }

    @Override
    public Enumeration<ArchiveEntry> getEntries() throws IOException {
        Enumeration<JarEntry> entries = jarFile.entries();
        return new Enumeration<ArchiveEntry>() {
            @Override
            public boolean hasMoreElements() {
                return entries.hasMoreElements();
            }

            @Override
            public ArchiveEntry nextElement() {
                return new JarFileArchiveEntry(entries.nextElement());
            }
        };
    }

    @Override
    public ArchiveEntry getEntry(String name) throws IOException {
        JarEntry entry = jarFile.getJarEntry(name);
        return entry != null ? new JarFileArchiveEntry(entry) : null;
    }

    @Override
    public InputStream getInputStream(ArchiveEntry entry) throws IOException {
        if (!(entry instanceof JarFileArchiveEntry)) {
            throw new IllegalArgumentException("Invalid entry type");
        }
        return jarFile.getInputStream(((JarFileArchiveEntry) entry).getJarEntry());
    }

    @Override
    public URL getUrl() throws IOException {
        try {
            return new URL("jar:" + jarFile.getName() + "!/" + pathFromRoot);
        } catch (MalformedURLException e) {
            throw new IOException("Invalid JAR URL", e);
        }
    }

    @Override
    public void close() throws IOException {
        jarFile.close();
    }

    /**
     * JAR文件中的条目实现
     */
    class JarFileArchiveEntry implements ArchiveEntry {
        private final JarEntry jarEntry;

        public JarFileArchiveEntry(JarEntry jarEntry) {
            this.jarEntry = jarEntry;
        }

        @Override
        public String getName() {
            return jarEntry.getName();
        }

        @Override
        public long getSize() {
            return jarEntry.getSize();
        }

        @Override
        public boolean isDirectory() {
            return jarEntry.isDirectory();
        }

        @Override
        public RandomAccessData getRandomAccessData() throws IOException {
            long offset = -1;
            try {
                java.lang.reflect.Field f = java.util.zip.ZipEntry.class.getDeclaredField("localHeaderOffset");
                f.setAccessible(true);
                offset = f.getLong(jarEntry);
            } catch (Exception e) {
                throw new IOException("Cannot access localHeaderOffset via reflection", e);
            }
            return new NestedRandomAccessData(jarData, offset, jarEntry.getSize());
        }

        @Override
        public int getMethod() {
            return jarEntry.getMethod();
        }

        public JarEntry getJarEntry() {
            return jarEntry;
        }
    }

    /**
     * 创建嵌套JAR的归档
     */
    public Archive createNestedArchive(Archive.ArchiveEntry entry) throws IOException {
        // 检查压缩方式，嵌套JAR必须是未压缩的
        if (entry.getMethod() != ZipEntry.STORED) {
            throw new IllegalStateException(
                    "Unable to open nested entry '" + entry.getName() + "'. It has been compressed and nested "
                            + "jar files must be stored without compression. Please check the "
                            + "mechanism used to create your executable jar file");
        }

        RandomAccessData entryData = entry.getRandomAccessData();
        String nestedPath = pathFromRoot + "!/" + entry.getName();
        return new JarFileArchive(new NestedJarFile(jarFile, entry, entryData), entryData, nestedPath);
    }
}
