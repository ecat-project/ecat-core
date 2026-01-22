package com.ecat.core.Utils.NestedJarLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * 嵌套在其他JAR中的数据的随机访问实现
 */
public class NestedRandomAccessData implements RandomAccessData {

    private final RandomAccessData parent;
    private final long offset;
    private final long size;

    public NestedRandomAccessData(RandomAccessData parent, long offset, long size) {
        this.parent = parent;
        this.offset = offset;
        this.size = size;
    }

    @Override
    public long getSize() throws IOException {
        return size;
    }

    @Override
    public ByteBuffer getByteBuffer(long position, int length) throws IOException {
        if (position + length > size) {
            throw new IndexOutOfBoundsException("Position + length exceeds data size");
        }
        return parent.getByteBuffer(offset + position, length);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return getInputStream(0, size);
    }

    @Override
    public InputStream getInputStream(long position, long length) throws IOException {
        if (position + length > size) {
            throw new IndexOutOfBoundsException("Position + length exceeds data size");
        }
        return parent.getInputStream(offset + position, length);
    }
}
    