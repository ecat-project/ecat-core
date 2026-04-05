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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Logic Running Status Attribute - derives running status from bound physical attribute's status.
 *
 * <p>Bound to a core concentration physical attribute. Running status is derived by reading
 * the physical attribute's {@link AttributeBase#getStatus()} name (e.g., "Normal", "Alarm").
 *
 * <ul>
 *   <li>{@code bindAttr.getStatus().getName()} → set as the current value</li>
 *   <li>{@code bindAttr} 为 null → 不更新</li>
 * </ul>
 *
 * <p>Options are populated from {@link AttributeStatus#getNames()}, excluding
 * {@link AttributeStatus#EMPTY} and {@link AttributeStatus#OTHER} (deprecated).
 *
 * <p>Read-only: value cannot be changed externally.
 *
 * @see LStringSelectAttribute
 * @author coffee
 */
public class LRunningStatusAttribute extends LStringSelectAttribute {

    /** Status options, excluding EMPTY and OTHER */
    private static final List<String> RUNNING_STATUS_OPTIONS = buildOptions();

    /**
     * Bound constructor - binds to a core concentration physical attribute.
     *
     * @param bindAttr the physical attribute to monitor for running status
     */
    public LRunningStatusAttribute(AttributeBase<?> bindAttr) {
        super(bindAttr, RUNNING_STATUS_OPTIONS);
    }

    /**
     * Derives running status from the bound physical attribute's status.
     *
     * <p>Reads the physical attribute's status name and updates the logic attribute's value.
     * If the status name is not in the options list, the value is not updated.
     *
     * @param updatedAttr the physical attribute whose value has been updated
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        AttributeBase<?> bindAttr = getBindAttr();
        if (bindAttr == null) return;

        AttributeStatus status = bindAttr.getStatus();
        if (status == null || status == AttributeStatus.EMPTY) return;

        String statusName = status.getName();
        if (RUNNING_STATUS_OPTIONS.contains(statusName)) {
            updateValue(statusName, status);
        }
    }

    /**
     * Gets the valid running status options.
     *
     * @return list of valid status names (excludes EMPTY and OTHER)
     */
    public static List<String> getRunningStatusOptions() {
        return RUNNING_STATUS_OPTIONS;
    }

    /**
     * Builds the options list from AttributeStatus, excluding EMPTY and OTHER.
     */
    private static List<String> buildOptions() {
        return Arrays.stream(AttributeStatus.values())
                .filter(s -> s != AttributeStatus.EMPTY && s != AttributeStatus.OTHER)
                .map(AttributeStatus::getName)
                .collect(Collectors.toList());
    }
}
