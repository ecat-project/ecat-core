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

package com.ecat.core.LogicState;

import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;

import java.util.List;

/**
 * Placeholder 命令属性 — 标记逻辑设备中不可用的命令类型属性槽位。
 *
 * <p>两种工厂方法：
 * <ul>
 *   <li>{@link #createAlarm} — 物理设备未配置/未找到，status = ALARM</li>
 *   <li>{@link #createBlank} — 物理设备存在但无对应属性，status = NORMAL</li>
 * </ul>
 *
 * <p>{@code instanceof LCommandAttribute} 为 true，兼容现有的类型检查。
 *
 * @see PlaceholderLogicAttribute
 * @see LCommandAttribute
 * @author coffee
 */
public class PlaceholderCommandAttribute extends LCommandAttribute implements PlaceholderLogicAttribute {

    private final Kind kind;

    private PlaceholderCommandAttribute(String attributeID, AttributeClass attrClass,
            List<String> standardCommands, Kind kind) {
        super(attributeID, attrClass, standardCommands);
        this.kind = kind;
    }

    /**
     * 创建 ALARM 状态的占位属性（物理设备未配置/未找到）。
     */
    public static PlaceholderCommandAttribute createAlarm(String attributeID, AttributeClass attrClass,
            List<String> standardCommands) {
        PlaceholderCommandAttribute attr = new PlaceholderCommandAttribute(
            attributeID, attrClass, standardCommands, Kind.ALARM_MISSING_DEVICE);
        attr.setStatus(AttributeStatus.ALARM);
        return attr;
    }

    /**
     * 创建 NORMAL 状态的占位属性（物理设备存在但无对应属性）。
     */
    public static PlaceholderCommandAttribute createBlank(String attributeID, AttributeClass attrClass,
            List<String> standardCommands) {
        PlaceholderCommandAttribute attr = new PlaceholderCommandAttribute(
            attributeID, attrClass, standardCommands, Kind.NORMAL_NO_ATTR);
        attr.setStatus(AttributeStatus.NORMAL);
        return attr;
    }

    @Override
    public boolean isPlaceholder() {
        return true;
    }

    @Override
    public Kind getPlaceholderKind() {
        return kind;
    }
}
