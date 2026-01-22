package com.ecat.core.Utils.NestedJarLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

/**
 * 归档文件(如JAR)的抽象接口
 */
public interface Archive {

    /**
     * 获取归档中的所有条目
     */
    Enumeration<ArchiveEntry> getEntries() throws IOException;

    /**
     * 获取指定名称的条目
     */
    ArchiveEntry getEntry(String name) throws IOException;

    /**
     * 获取指定条目的输入流
     */
    InputStream getInputStream(ArchiveEntry entry) throws IOException;

    /**
     * 获取归档的URL
     */
    URL getUrl() throws IOException;

    /**
     * 关闭归档
     */
    void close() throws IOException;

    /**
     * 归档中的条目
     */
    interface ArchiveEntry {
        String getName();
        long getSize();
        boolean isDirectory();
        RandomAccessData getRandomAccessData() throws IOException;
        int getMethod(); // ZIP压缩方法
    }
}
    