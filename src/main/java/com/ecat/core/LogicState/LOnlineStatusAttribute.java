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
import com.ecat.core.State.UnitInfo;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Logic Online Status Attribute - derives online status from bound physical attribute's update time.
 *
 * <p>Bound to a core concentration physical attribute. Online status is derived by checking
 * whether the physical attribute's {@link AttributeBase#getUpdateTime()} is within the last minute.
 *
 * <ul>
 *   <li>{@code bindAttr.getUpdateTime()}距今 &lt; 1分钟 → {@code "online"}</li>
 *   <li>{@code bindAttr.getUpdateTime()}距今 ≥ 1分钟 → {@code "offline"}</li>
 *   <li>{@code bindAttr} 为 null → 不更新</li>
 * </ul>
 *
 * <p>Read-only: {@link #setDisplayValue(String, UnitInfo)} always returns false.
 *
 * @see LStringSelectAttribute
 * @author coffee
 */
public class LOnlineStatusAttribute extends LStringSelectAttribute {

    /** Online status options */
    private static final List<String> ONLINE_OPTIONS = Arrays.asList("online", "offline");

    /**
     * Bound constructor - binds to a core concentration physical attribute.
     *
     * @param bindAttr the physical attribute to monitor for online status
     */
    public LOnlineStatusAttribute(AttributeBase<?> bindAttr) {
        super(bindAttr, ONLINE_OPTIONS, Collections.emptyMap());
    }

    /**
     * Derives online status from the bound physical attribute's update time.
     *
     * <p>If the physical attribute was updated within the last minute, sets value to "online".
     * Otherwise, sets value to "offline".
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
            updateValue("offline", phyStatus != null ? phyStatus : AttributeStatus.EMPTY);
            return;
        }
        long elapsedSeconds = java.time.Duration.between(updateTime, java.time.LocalDateTime.now()).getSeconds();
        updateValue(elapsedSeconds < 60 ? "online" : "offline", phyStatus);
    }

    /**
     * Read-only: online status cannot be set externally.
     *
     * @param newDisplayValue ignored
     * @param fromUnit ignored
     * @return CompletableFuture with false (always fails)
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        return CompletableFuture.completedFuture(false);
    }
}
