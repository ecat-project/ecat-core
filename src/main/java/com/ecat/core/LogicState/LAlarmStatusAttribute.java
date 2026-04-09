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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Logic Alarm Status Attribute - derives alarm status from bound physical attribute's status.
 *
 * <p>Bound to a core concentration physical attribute. Alarm status is derived by checking
 * whether the physical attribute's {@link AttributeBase#getStatus()} is {@link AttributeStatus#ALARM}.
 *
 * <ul>
 *   <li>{@code bindAttr.getStatus() == ALARM} → {@code "alarm"}</li>
 *   <li>{@code bindAttr.getStatus() != ALARM} → {@code "normal"}</li>
 *   <li>{@code bindAttr} 为 null → 不更新</li>
 * </ul>
 *
 * <p>Read-only: {@link #setDisplayValue(String, UnitInfo)} always returns false.
 *
 * @see LStringSelectAttribute
 * @author coffee
 */
public class LAlarmStatusAttribute extends LStringSelectAttribute {

    /** Alarm status options */
    private static final List<String> ALARM_OPTIONS = Arrays.asList("normal", "alarm");

    /**
     * Bound constructor - binds to a core concentration physical attribute.
     *
     * @param bindAttr the physical attribute to monitor for alarm status
     */
    public LAlarmStatusAttribute(AttributeBase<?> bindAttr) {
        super(bindAttr, ALARM_OPTIONS, Collections.emptyMap());
    }

    /**
     * Derives alarm status from the bound physical attribute's status.
     *
     * <p>If the physical attribute's status is {@link AttributeStatus#ALARM}, sets value to "alarm".
     * Otherwise, sets value to "normal".
     *
     * @param updatedAttr the physical attribute whose value has been updated
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        AttributeBase<?> bindAttr = getBindAttr();
        if (bindAttr == null) return;

        AttributeStatus phyStatus = bindAttr.getStatus();
        updateValue(phyStatus == AttributeStatus.ALARM ? "alarm" : "normal", phyStatus);
    }

    /**
     * Read-only: alarm status cannot be set externally.
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
