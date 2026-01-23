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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 基于文件的随机访问数据实现
 */
public class FileRandomAccessData implements RandomAccessData {

    private final File file;

    public FileRandomAccessData(File file) {
        this.file = file;
    }

    @Override
    public long getSize() throws IOException {
        return file.length();
    }

    @Override
    public ByteBuffer getByteBuffer(long position, int length) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            FileChannel channel = inputStream.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(length);
            channel.read(buffer, position);
            buffer.flip();
            return buffer;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

@Override
public InputStream getInputStream(long position, long length) throws IOException {
    FileInputStream fis = new FileInputStream(file);
    FileChannel channel = fis.getChannel();
    channel.position(position);
    return new InputStream() {
        private long remaining = length;

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = fis.read();
            if (b != -1) remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            len = (int) Math.min(len, remaining);
            int read = fis.read(b, off, len);
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException {
            fis.close();
        }
    };
}
}
