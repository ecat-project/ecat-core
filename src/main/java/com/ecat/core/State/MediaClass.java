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

package com.ecat.core.State;

/**
 * 媒体分类枚举，定义在 ecat-core 中供所有集成共享。
 * 
 * @author coffee
 */
public enum MediaClass {
    DIRECTORY,
    IMAGE,
    VIDEO,
    AUDIO,
    STREAM,    // 实时流（RTSP、HLS 等）
    FILE;

    /** 根据文件扩展名推断 MediaClass */
    public static MediaClass fromExtension(String ext) {
        MimeType mt = MimeType.fromExtension(ext);
        return fromMimeType(mt);
    }

    /** 根据 MimeType 推断 MediaClass */
    public static MediaClass fromMimeType(MimeType mimeType) {
        if (mimeType == null) return FILE;
        if (mimeType.isImage()) return IMAGE;
        if (mimeType.isVideo() && mimeType == MimeType.VIDEO_RTSP) return STREAM;
        if (mimeType.isVideo()) return VIDEO;
        if (mimeType.isAudio()) return AUDIO;
        return FILE;
    }
}
