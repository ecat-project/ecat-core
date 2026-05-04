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

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.*;

public class MediaAttributeTest {

    @Test
    public void testBasicCreation() {
        MediaAttribute attr = new MediaAttribute("last_photo", false);
        assertEquals("last_photo", attr.getAttributeID());
        assertEquals(AttributeClass.TEXT, attr.getAttrClass());
        assertFalse(attr.canValueChange());
        assertFalse(attr.hasMedia());
    }

    @Test
    public void testSetMediaUri() {
        MediaAttribute attr = new MediaAttribute("last_photo", false);
        attr.setMediaUri("ecat-media://com.ecat:integration-zkteco/photos/001.jpg");

        assertTrue(attr.hasMedia());
        assertEquals("ecat-media://com.ecat:integration-zkteco/photos/001.jpg", attr.getMediaUri());
        assertEquals("ecat-media://com.ecat:integration-zkteco/photos/001.jpg", attr.getValue());
        assertEquals("ecat-media://com.ecat:integration-zkteco/photos/001.jpg", attr.getDisplayValue());
        assertEquals(AttributeStatus.NORMAL, attr.getStatus());
    }

    @Test
    public void testMetadataSettersAndGetters() {
        MediaAttribute attr = new MediaAttribute("last_photo", false);
        attr.setMediaUri("ecat-media://com.ecat:integration-zkteco/photos/001.jpg");
        attr.setMimeType(MimeType.IMAGE_JPEG);
        attr.setMediaClass(MediaClass.IMAGE);
        attr.setFileSize(204800);
        Instant testTime = Instant.ofEpochSecond(1745884800L);
        attr.setCreatedTime(testTime);

        assertEquals(MimeType.IMAGE_JPEG, attr.getMimeType());
        assertEquals(MediaClass.IMAGE, attr.getMediaClass());
        assertEquals(204800, attr.getFileSize());
        assertEquals(testTime, attr.getCreatedTime());
    }

    @Test
    public void testPersistableConstructor() {
        MediaAttribute attr = new MediaAttribute("last_snapshot", true,
                true, null, null);
        assertTrue(attr.canValueChange());
        assertTrue(attr.isPersistable());
    }

    @Test
    public void testAttributeType() {
        MediaAttribute attr = new MediaAttribute("last_photo", false);
        assertEquals(AttributeType.MEDIA, attr.getAttributeType());
    }

    @Test
    public void testI18nPrefix() {
        MediaAttribute attr = new MediaAttribute("last_photo", false);
        assertNotNull(attr.getI18nPrefixPath());
        assertTrue(attr.getI18nPrefixPath().toString().contains("media_attr"));
    }

    // ===== MimeType 枚举测试 =====

    @Test
    public void testMimeTypeFromExtension() {
        assertEquals(MimeType.IMAGE_JPEG, MimeType.fromExtension("jpg"));
        assertEquals(MimeType.IMAGE_JPEG, MimeType.fromExtension("jpeg")); // 别名映射
        assertEquals(MimeType.IMAGE_JPEG, MimeType.fromExtension(".jpg"));
        assertEquals(MimeType.IMAGE_PNG, MimeType.fromExtension("png"));
        assertEquals(MimeType.VIDEO_MP4, MimeType.fromExtension("mp4"));
        assertEquals(MimeType.AUDIO_MP3, MimeType.fromExtension("mp3"));
        assertEquals(MimeType.APPLICATION_PDF, MimeType.fromExtension("pdf"));
        assertEquals(MimeType.UNKNOWN, MimeType.fromExtension("xyz"));
        assertEquals(MimeType.UNKNOWN, MimeType.fromExtension(null));
    }

    @Test
    public void testMimeTypeFromMediaType() {
        assertEquals(MimeType.IMAGE_JPEG, MimeType.fromMediaType("image/jpeg"));
        assertEquals(MimeType.IMAGE_JPEG, MimeType.fromMediaType("IMAGE/JPEG")); // 大小写不敏感
        assertEquals(MimeType.VIDEO_MP4, MimeType.fromMediaType("video/mp4"));
        assertEquals(MimeType.UNKNOWN, MimeType.fromMediaType("unknown/type"));
        assertEquals(MimeType.UNKNOWN, MimeType.fromMediaType(null));
    }

    @Test
    public void testMimeTypeCategories() {
        // 图片类型
        assertTrue(MimeType.IMAGE_JPEG.isImage());
        assertTrue(MimeType.IMAGE_PNG.isImage());
        assertFalse(MimeType.IMAGE_JPEG.isVideo());
        assertFalse(MimeType.IMAGE_JPEG.isAudio());

        // 视频类型
        assertTrue(MimeType.VIDEO_MP4.isVideo());
        assertTrue(MimeType.VIDEO_RTSP.isVideo());
        assertFalse(MimeType.VIDEO_MP4.isImage());

        // 音频类型
        assertTrue(MimeType.AUDIO_AAC.isAudio());
        assertTrue(MimeType.AUDIO_MP3.isAudio());
        assertFalse(MimeType.AUDIO_AAC.isImage());

        // 媒体类型总判断
        assertTrue(MimeType.IMAGE_JPEG.isMedia());
        assertTrue(MimeType.VIDEO_MP4.isMedia());
        assertTrue(MimeType.AUDIO_MP3.isMedia());
        assertFalse(MimeType.APPLICATION_PDF.isMedia());
        assertFalse(MimeType.UNKNOWN.isMedia());
    }

    @Test
    public void testMimeTypeProperties() {
        assertEquals("image/jpeg", MimeType.IMAGE_JPEG.getMediaType());
        assertEquals("jpg", MimeType.IMAGE_JPEG.getExtension());
        assertNull(MimeType.VIDEO_RTSP.getExtension());
        assertEquals("application/octet-stream", MimeType.UNKNOWN.getMediaType());
        assertNull(MimeType.UNKNOWN.getExtension());
    }

    // ===== MediaClass 枚举测试 =====

    @Test
    public void testMediaClassFromExtension() {
        assertEquals(MediaClass.IMAGE, MediaClass.fromExtension("jpg"));
        assertEquals(MediaClass.IMAGE, MediaClass.fromExtension("png"));
        assertEquals(MediaClass.VIDEO, MediaClass.fromExtension("mp4"));
        assertEquals(MediaClass.AUDIO, MediaClass.fromExtension("mp3"));
        assertEquals(MediaClass.FILE, MediaClass.fromExtension("pdf"));
        assertEquals(MediaClass.FILE, MediaClass.fromExtension("xyz"));
    }

    @Test
    public void testMediaClassFromMimeType() {
        assertEquals(MediaClass.IMAGE, MediaClass.fromMimeType(MimeType.IMAGE_JPEG));
        assertEquals(MediaClass.VIDEO, MediaClass.fromMimeType(MimeType.VIDEO_MP4));
        assertEquals(MediaClass.STREAM, MediaClass.fromMimeType(MimeType.VIDEO_RTSP));
        assertEquals(MediaClass.AUDIO, MediaClass.fromMimeType(MimeType.AUDIO_MP3));
        assertEquals(MediaClass.FILE, MediaClass.fromMimeType(MimeType.APPLICATION_PDF));
        assertEquals(MediaClass.FILE, MediaClass.fromMimeType(null));
    }

    @Test
    public void testMediaClassStreamNotVideo() {
        // RTSP 应分类为 STREAM 而非 VIDEO
        assertEquals(MediaClass.STREAM, MediaClass.fromMimeType(MimeType.VIDEO_RTSP));
        // 非 RTSP 视频应分类为 VIDEO
        assertEquals(MediaClass.VIDEO, MediaClass.fromMimeType(MimeType.VIDEO_MP4));
        assertEquals(MediaClass.VIDEO, MediaClass.fromMimeType(MimeType.VIDEO_HLS));
    }

    // ===== "先 new 再 init" 构造函数测试 =====

    @Test
    public void testProtectedConstructor() {
        // 通过子类暴露的 protected 构造函数测试
        MediaAttribute attr = new MediaAttribute("test_attr") {};
        assertEquals("test_attr", attr.getAttributeID());
        assertFalse(attr.hasMedia());
        assertEquals(AttributeType.MEDIA, attr.getAttributeType());
    }
}
