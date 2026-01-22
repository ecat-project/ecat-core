package com.ecat.core.Utils.NestedJarLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * 支持嵌套JAR的自定义JarFile
 */
public class NestedJarFile extends JarFile {

    private final RandomAccessData data;
    private final Archive.ArchiveEntry parentEntry;
    private final List<JarEntry> entryList = new ArrayList<>();

    public NestedJarFile(File file) throws IOException {
        super(file);
        this.data = new FileRandomAccessData(file);
        this.parentEntry = null;
        parseEntries();
    }

    public NestedJarFile(JarFile parentJarFile, Archive.ArchiveEntry parentEntry, RandomAccessData data)
            throws IOException {
        super(File.createTempFile("temp", ".jar")); // 临时文件仅用于满足父类构造器
        this.parentEntry = parentEntry;
        this.data = data;
        parseEntries();
    }

    private void parseEntries() throws IOException {
        entryList.clear();
        try (InputStream is = data.getInputStream();
             java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                JarEntry jarEntry = new JarEntry(entry.getName());
                jarEntry.setSize(entry.getSize());
                jarEntry.setTime(entry.getTime());
                jarEntry.setMethod(entry.getMethod());
                entryList.add(jarEntry);
            }
        }
    }

    @Override
    public Enumeration<JarEntry> entries() {
        return java.util.Collections.enumeration(entryList);
    }

    @Override
    public JarEntry getJarEntry(String name) {
        for (JarEntry entry : entryList) {
            if (entry.getName().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public InputStream getInputStream(ZipEntry ze) throws IOException {
        if (ze instanceof JarEntry) {
            long offset = -1;
            try {
                java.lang.reflect.Field f = ZipEntry.class.getDeclaredField("localHeaderOffset");
                f.setAccessible(true);
                offset = f.getLong(ze);
            } catch (Exception e) {
                throw new IOException("Cannot access localHeaderOffset via reflection", e);
            }
            return data.getInputStream(offset, ze.getSize());
        }
        return super.getInputStream(ze);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
