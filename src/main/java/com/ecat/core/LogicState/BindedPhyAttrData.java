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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * BindedPhyAttrData holds the binding metadata for a physical attribute
 * that is bound to a logic attribute (specifically LMixNumericAttribute).
 *
 * <p>Each entry tracks:
 * <ul>
 *   <li>{@code phyattrid} - the physical attribute's ID</li>
 *   <li>{@code bindPhyAttr} - reference to the physical AttributeBase instance</li>
 *   <li>{@code isUpdated} - whether the physical attribute has been updated within the current time window</li>
 *   <li>{@code updatetime} - timestamp (ms) of the last update</li>
 * </ul>
 *
 * @see LMixNumericAttribute
 * @author coffee
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BindedPhyAttrData {
    /** Physical attribute ID */
    private String phyattrid;
    /** Reference to the bound physical attribute */
    private AttributeBase<?> bindPhyAttr;
    /** Whether the physical attribute has been updated within the current time window */
    private boolean isUpdated;
    /** Timestamp (ms) of the last update from the physical attribute */
    private long updatetime;
}
