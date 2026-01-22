package com.ecat.core.Utils.NestedJarLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * 提供对数据的随机访问能力，用于处理嵌套JAR
 */
public interface RandomAccessData {

    /**
     * 获取数据大小
     */
    long getSize() throws IOException;

    /**
     * 获取指定范围的数据
     */
    ByteBuffer getByteBuffer(long position, int length) throws IOException;

    /**
     * 获取整个数据的输入流
     */
    InputStream getInputStream() throws IOException;

    /**
     * 获取指定范围的输入流
     */
    InputStream getInputStream(long position, long length) throws IOException;
}
    