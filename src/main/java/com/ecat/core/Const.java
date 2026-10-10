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

import java.nio.file.Path;
import java.nio.file.Paths;

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
     * 集成应用目录分配器（应用安装面执法）：{@code .ecat-data/integrations/{g}/{a}}。
     *
     * <p><b>治理规则：integrations 只放应用</b>——子进程二进制、原生库缓存、
     * 随程序生命周期的生成配置（可再生、不增长）。日志/数据库/容器卷等
     * 可增长数据一律走 {@link #integrationStorageDir}。</p>
     *
     * <p>groupId 作为一个目录段保留点号（不转斜杠——m2 式拆段是
     * {@code IntegrationManager.getJarPath} 的仓库布局，与数据目录无关），
     * 与 config_entries 布局对齐。禁止再自拼路径（历史分叉根因：常量有、
     * 分配机制无，media/llm-agent 坐标式与 mediagateway 短名直挂由此而来）。</p>
     *
     * @param groupId    Maven groupId（非空安全段，如 {@code com.ecat}）
     * @param artifactId Maven artifactId（非空安全段）
     * @return 应用目录相对路径（调用方自行 createDirectories 并管理内容）
     */
    public static Path integrationAppDir(String groupId, String artifactId) {
        requireSafeSegment(groupId, "groupId");
        requireSafeSegment(artifactId, "artifactId");
        return Paths.get(INTEGRATIONS_DATA_DIR, groupId, artifactId);
    }

    /**
     * 集成存储目录分配器（数据面执法）：{@code .ecat-data/storage/{g}/{a}}。
     *
     * <p><b>数据面语义：可增长内容归此</b>——日志、数据库、容器数据卷等。
     * 与 media（{@code storage/com.ecat/integration-media/media.db}）/
     * vision-analysis（{@code storage/com.ecat/integration-vision-analysis/frigate}）
     * 既有坐标布局实况一致，收编为统一分配面。</p>
     *
     * @param groupId    Maven groupId（非空安全段）
     * @param artifactId Maven artifactId（非空安全段）
     * @return 存储目录相对路径（调用方自行 createDirectories 并管理内容）
     */
    public static Path integrationStorageDir(String groupId, String artifactId) {
        requireSafeSegment(groupId, "groupId");
        requireSafeSegment(artifactId, "artifactId");
        return Paths.get(MEDIA_STORAGE_DIR, groupId, artifactId);
    }

    /** 目录段安全校验：非空、无路径分隔符、非当前/父目录段。 */
    private static void requireSafeSegment(String segment, String name) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空（集成数据目录坐标段）");
        }
        if (segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(name + " 含路径分隔符，拒绝（防路径穿越）: " + segment);
        }
        if (".".equals(segment) || "..".equals(segment)) {
            throw new IllegalArgumentException(name + " 不得为当前/父目录段: " + segment);
        }
    }

    /**
     * 媒体文件存储根目录。
     *
     * <p>独立存储卷，与 core 管理目录分离，便于未来挂载 NAS 或对象存储。
     * 集成数据面统一走 {@link #integrationStorageDir}（storage/{g}/{a}/）。
     * <pre>
     * .ecat-data/storage/
     *   └── com.ecat/
     *       └── integration-media/
     *           ├── files/snapshots/xxx.jpg      ← media 媒体文件
     *           └── media.db                      ← media 数据库
     * </pre>
     */
    public static final String MEDIA_STORAGE_DIR = ".ecat-data/storage";

    /**
     * 媒体存储文件子目录名。
     */
    public static final String MEDIA_STORAGE_FILES_DIR = "files";

}
