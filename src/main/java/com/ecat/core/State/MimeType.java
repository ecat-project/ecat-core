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
 * MIME 类型枚举，定义在 ecat-core 中供所有集成共享。
 * 每个枚举值包含标准 mediaType 字符串和对应文件扩展名。
 * 
 * @author coffee
 */
public enum MimeType {
    IMAGE_JPEG("image/jpeg", "jpg"),
    IMAGE_PNG("image/png", "png"),
    IMAGE_GIF("image/gif", "gif"),
    IMAGE_BMP("image/bmp", "bmp"),
    IMAGE_WEBP("image/webp", "webp"),
    VIDEO_MP4("video/mp4", "mp4"),
    VIDEO_FMP4("video/iso.segment", "m4s"),
    VIDEO_RTSP("video/rtsp", null),
    VIDEO_HLS("application/vnd.apple.mpegurl", "m3u8"),
    VIDEO_AVI("video/x-msvideo", "avi"),
    AUDIO_AAC("audio/aac", "aac"),
    AUDIO_MP3("audio/mpeg", "mp3"),
    AUDIO_WAV("audio/wav", "wav"),
    APPLICATION_PDF("application/pdf", "pdf"),
    APPLICATION_JSON("application/json", "json"),
    TEXT_CSV("text/csv", "csv"),
    TEXT_PLAIN("text/plain", "txt"),
    UNKNOWN("application/octet-stream", null);

    private final String mediaType;
    private final String extension;

    MimeType(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String getMediaType() { return mediaType; }
    public String getExtension() { return extension; }

    /** 判断是否为图片类型 */
    public boolean isImage() { return this.name().startsWith("IMAGE_"); }

    /** 判断是否为视频类型 */
    public boolean isVideo() { return this.name().startsWith("VIDEO_"); }

    /** 判断是否为音频类型 */
    public boolean isAudio() { return this.name().startsWith("AUDIO_"); }

    /** 判断是否为媒体类型（图片/视频/音频） */
    public boolean isMedia() { return isImage() || isVideo() || isAudio(); }

    /**
     * 根据文件扩展名获取 MimeType。
     * 注意：jpeg → IMAGE_JPEG（别名映射）
     */
    public static MimeType fromExtension(String ext) {
        if (ext == null) return UNKNOWN;
        String lower = ext.toLowerCase().replace(".", "");
        // jpeg → jpg 别名
        if ("jpeg".equals(lower)) lower = "jpg";
        for (MimeType mt : values()) {
            if (lower.equals(mt.extension)) return mt;
        }
        return UNKNOWN;
    }

    /** 根据 MIME mediaType 字符串获取 MimeType */
    public static MimeType fromMediaType(String mediaType) {
        if (mediaType == null) return UNKNOWN;
        for (MimeType mt : values()) {
            if (mediaType.equalsIgnoreCase(mt.mediaType)) return mt;
        }
        return UNKNOWN;
    }
}
