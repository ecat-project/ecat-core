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
    