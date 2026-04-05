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

import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeStatus;

import java.time.LocalDateTime;

/**
 * Logic Online Status Attribute - derives online status from bound physical attribute's update time.
 *
 * <p>Bound to a core concentration physical attribute. Online status is derived by checking
 * whether the physical attribute's {@link AttributeBase#getUpdateTime()} is within the last minute.
 *
 * <ul>
 *   <li>{@code bindAttr.getUpdateTime()}距今 &lt; 1分钟 → {@code true} (online)</li>
 *   <li>{@code bindAttr.getUpdateTime()}距今 ≥ 1分钟 → {@code false} (offline)</li>
 *   <li>{@code bindAttr} 为 null → 不更新</li>
 * </ul>
 *
 * <p>Read-only: {@link #setDisplayValue(String, com.ecat.core.State.UnitInfo)} returns false.
 *
 * @see LBinaryAttribute
 * @author coffee
 */
public class LOnlineStatusAttribute extends LBinaryAttribute {

    /**
     * Bound constructor - binds to a core concentration physical attribute.
     *
     * @param bindAttr the physical attribute to monitor for online status
     */
    public LOnlineStatusAttribute(AttributeBase<?> bindAttr) {
        super(bindAttr);
    }

    /**
     * Derives online status from the bound physical attribute's update time.
     *
     * <p>If the physical attribute was updated within the last minute, sets value to true (online).
     * Otherwise, sets value to false (offline).
     *
     * @param updatedAttr the physical attribute whose value has been updated
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        AttributeBase<?> bindAttr = getBindAttr();
        if (bindAttr == null) return;

        AttributeStatus phyStatus = bindAttr.getStatus();
        LocalDateTime updateTime = bindAttr.getUpdateTime();
        if (updateTime == null) {
            updateValue(false, phyStatus != null ? phyStatus : AttributeStatus.EMPTY);
            return;
        }
        long elapsedSeconds = java.time.Duration.between(updateTime, java.time.LocalDateTime.now()).getSeconds();
        updateValue(elapsedSeconds < 60, phyStatus);
    }
}
