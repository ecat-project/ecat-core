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

/**
 * Logic Alarm Status Attribute - derives alarm status from bound physical attribute's status.
 *
 * <p>Bound to a core concentration physical attribute. Alarm status is derived by checking
 * whether the physical attribute's {@link AttributeBase#getStatus()} is {@link AttributeStatus#ALARM}.
 *
 * <ul>
 *   <li>{@code bindAttr.getStatus() == ALARM} → {@code true} (alarm)</li>
 *   <li>{@code bindAttr.getStatus() != ALARM} → {@code false} (no alarm)</li>
 *   <li>{@code bindAttr} 为 null → 不更新</li>
 * </ul>
 *
 * <p>Read-only: {@link #setDisplayValue(String, com.ecat.core.State.UnitInfo)} returns false.
 *
 * @see LBinaryAttribute
 * @author coffee
 */
public class LAlarmStatusAttribute extends LBinaryAttribute {

    /**
     * Bound constructor - binds to a core concentration physical attribute.
     *
     * @param bindAttr the physical attribute to monitor for alarm status
     */
    public LAlarmStatusAttribute(AttributeBase<?> bindAttr) {
        super(bindAttr);
    }

    /**
     * Derives alarm status from the bound physical attribute's status.
     *
     * <p>If the physical attribute's status is {@link AttributeStatus#ALARM}, sets value to true.
     * Otherwise, sets value to false.
     *
     * @param updatedAttr the physical attribute whose value has been updated
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        AttributeBase<?> bindAttr = getBindAttr();
        if (bindAttr == null) return;

        AttributeStatus phyStatus = bindAttr.getStatus();
        updateValue(phyStatus == AttributeStatus.ALARM, phyStatus);
    }
}
