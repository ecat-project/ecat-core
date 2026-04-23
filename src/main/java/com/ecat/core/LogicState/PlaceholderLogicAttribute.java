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

/**
 * Placeholder 逻辑属性标记接口。
 *
 * <p>实现此接口的逻辑属性表示它不是一个真实绑定物理设备的属性，
 * 而是一个占位属性（Placeholder），用于在属性槽位上保持逻辑设备属性的完整性。
 *
 * <p>两种占位类型：
 * <ul>
 *   <li>{@link Kind#ALARM_MISSING_DEVICE} — 物理设备未配置或未找到，状态为 ALARM</li>
 *   <li>{@link Kind#NORMAL_NO_ATTR} — 物理设备存在但该设备没有对应属性，状态为 NORMAL</li>
 * </ul>
 *
 * <p>调用者可通过 {@link ILogicAttribute#isPlaceholder()} 检查属性是否为占位属性，
 * 或通过 {@link #getPlaceholderKind()} 获取具体的占位类型。
 *
 * @see ILogicAttribute#isPlaceholder()
 * @see PlaceholderNumericAttribute
 * @see PlaceholderStringSelectAttribute
 * @author coffee
 */
public interface PlaceholderLogicAttribute {

    /**
     * 占位属性的类型。
     */
    enum Kind {
        /** 物理设备未配置或未找到 */
        ALARM_MISSING_DEVICE,
        /** 物理设备存在但该设备没有对应的物理属性 */
        NORMAL_NO_ATTR
    }

    /**
     * 获取占位属性的具体类型。
     *
     * @return 占位类型
     */
    Kind getPlaceholderKind();
}
