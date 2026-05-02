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

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.ecat.core.I18n.I18nKeyPath;

/**
 * 媒体属性基类，定义在 ecat-core 中，所有集成可访问。
 * 物理设备（如门禁、摄像头）和逻辑设备都可以使用。
 *
 * <p>value 固定为 String 类型，存储 media URI（如 "ecat-media://..."）。
 * URI 是资源的唯一访问键，通过 mediaService.openStream(uri) 流式获取文件内容。
 *
 * <p><b>绝不能用 byte[] 作为 value</b>，否则大文件（视频、高清图片）会导致 OOM。
 * 文件内容始终通过流式 API 获取，不缓存在属性中。
 *
 * <p>元数据（mimeType, fileSize 等）在 store 时由外部调用方填充。
 * 持久化只存 URI 字符串（MapDB），元数据通过懒加载从 SQLite 恢复。
 *
 * <p>mimeType 和 mediaClass 字段直接使用 ecat-core 中定义的 MimeType/MediaClass 枚举类型。
 *
 * @apiNote displayName i18n supported, path: state.media_attr.{attributeID}
 * 
 * @author coffee
 */
public class MediaAttribute extends TextAttribute {

    // ===== 媒体元数据字段（MimeType/MediaClass 枚举定义在 ecat-core 中） =====

    /** 媒体文件大小（字节） */
    private long fileSize;

    /** 创建时间（UTC Instant） */
    private Instant createdTime;

    /** MIME 类型，使用 ecat-core 中的 MimeType 枚举 */
    private MimeType mimeType;

    /** 媒体分类，使用 ecat-core 中的 MediaClass 枚举 */
    private MediaClass mediaClass;

    // ===== 构造函数 =====

    /**
     * 基本 media 属性构造函数
     *
     * @param attributeID 属性ID
     * @param valueChangeable 是否允许外部修改值
     */
    public MediaAttribute(String attributeID, boolean valueChangeable) {
        super(attributeID, AttributeClass.TEXT, null, null, valueChangeable);
    }

    /**
     * 完整参数构造函数（包含 persistable + defaultValue）
     *
     * @param attributeID 属性ID
     * @param valueChangeable 是否允许外部修改值
     * @param persistable 是否持久化
     * @param defaultValue 默认值
     * @param onChangedCallback 值变更回调
     */
    public MediaAttribute(String attributeID, boolean valueChangeable,
            boolean persistable, String defaultValue,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, AttributeClass.TEXT, null, null, valueChangeable,
                persistable, defaultValue, onChangedCallback);
    }

    /**
     * 仅 attributeID 的构造函数，用于 "先 new 再 init" 模式。
     * 不要滥用，仅在此模式下使用。
     */
    protected MediaAttribute(String attributeID) {
        super(attributeID);
    }

    // ===== 媒体元数据 Getter/Setter（使用枚举类型） =====

    /** 获取 MIME 类型枚举 */
    public MimeType getMimeType() { return mimeType; }

    /** 设置 MIME 类型枚举 */
    public void setMimeType(MimeType mimeType) { this.mimeType = mimeType; }

    /** 获取媒体分类枚举 */
    public MediaClass getMediaClass() { return mediaClass; }

    /** 设置媒体分类枚举 */
    public void setMediaClass(MediaClass mediaClass) { this.mediaClass = mediaClass; }

    /** 获取文件大小（字节） */
    public long getFileSize() { return fileSize; }

    /** 设置文件大小 */
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    /** 获取创建时间（UTC Instant） */
    public Instant getCreatedTime() { return createdTime; }

    /** 设置创建时间 */
    public void setCreatedTime(Instant createdTime) { this.createdTime = createdTime; }

    /**
     * 判断是否有关联的媒体文件。
     * value（继承自 TextAttribute）存储 media URI。
     */
    public boolean hasMedia() {
        String v = getValue();
        return v != null && !v.isEmpty();
    }

    /**
     * 获取 media URI。等同于 getValue()，语义更清晰。
     */
    public String getMediaUri() {
        return getValue();
    }

    /**
     * 设置 media URI。同时更新 value 字段。
     */
    public void setMediaUri(String uri) {
        updateValue(uri, AttributeStatus.NORMAL);
    }

    // ===== Override =====

    @Override
    public String getDisplayValue(UnitInfo toUnit) {
        return getValue();
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.media_attr.", "");
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.TEXT;
    }
}
