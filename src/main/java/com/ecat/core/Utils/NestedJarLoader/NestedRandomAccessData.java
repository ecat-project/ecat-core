/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    