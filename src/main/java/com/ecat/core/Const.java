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

package com.ecat.core;

/**
 * Constants class containing various constant values used throughout the application.
 * This class provides centralized access to frequently used string constants.
 * 
 * @author coffee
 */
public class Const {

    /**
     * Core module coordinate (groupId:artifactId format)
     * Used as unique identifier for I18n to avoid conflicts between different groupIds
     */
    public static final String CORE_COORDINATE = "com.ecat:ecat-core";

    /**
     * Core module artifactId
     * @deprecated Use {@link #CORE_COORDINATE} instead for I18n purposes
     */
    @Deprecated
    public static final String CORE_ARTIFACT_ID = "ecat-core"; // 与pom.xml中保持一致，为i18n使用

    /**
     * 集成数据根目录。
     *
     * <p>各集成在目录下创建以 {groupId}/{artifactId} 为路径的子目录，自行管理数据。
     * 用途包括但不限于：原生库缓存、运行时配置等。
     *
     * <p>目录结构与 config_entries 保持一致：
     * <pre>
     * .ecat-data/integrations/
     *   └── com.ecat/
     *       ├── integration-media/native-libs/    ← integration-media 的原生库缓存
     *       ├── integration-xxx/                   ← 其他集成的数据
     *       └── ...
     * </pre>
     */
    public static final String INTEGRATIONS_DATA_DIR = ".ecat-data/integrations";

    /**
     * 集成原生库缓存子目录名。
     *
     * <p>位于 {@code INTEGRATIONS_DATA_DIR/{groupId}/{artifactId}/native-libs/} 下。
     * 供 NativeLibraryHelper 等工具使用。
     */
    public static final String INTEGRATION_NATIVE_LIBS_DIR = "native-libs";

    /**
     * 媒体文件存储根目录。
     *
     * <p>独立存储卷，与 core 管理目录分离，便于未来挂载 NAS 或对象存储。
     * <pre>
     * .ecat-data/storage/
     *   └── files/
     *       └── {groupId}/{artifactId}/
     *           └── snapshots/xxx.jpg
     * </pre>
     */
    public static final String MEDIA_STORAGE_DIR = ".ecat-data/storage";

    /**
     * 媒体存储文件子目录名。
     */
    public static final String MEDIA_STORAGE_FILES_DIR = "files";

}
