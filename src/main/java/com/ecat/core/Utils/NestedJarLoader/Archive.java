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
    