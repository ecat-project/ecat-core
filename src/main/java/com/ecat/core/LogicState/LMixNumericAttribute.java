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
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.UnitInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Multi-physical-to-single-logic numeric aggregation attribute.
 *
 * <p>LMixNumericAttribute extends {@link LNumericAttribute} to support binding multiple
 * physical attributes and aggregating their values into a single logic value.
 *
 * <p><b>Time window mechanism:</b> The aggregation only fires when ALL bound physical
 * attributes have been updated within the configured time window. This ensures data
 * consistency when physical attributes arrive from different devices or at different rates.
 *
 * <p><b>Thread safety:</b> {@link #updateBindAttrValue(AttributeBase)} is synchronized
 * because bus async dispatch may invoke it from multiple thread pools concurrently.
 *
 * <p><b>Read-only:</b> LMixNumericAttribute is read-only. Calling {@link #setDisplayValue(String, UnitInfo)}
 * will throw {@link UnsupportedOperationException}.
 *
 * <p>Usage example:
 * <pre>
 *   class AvgTemperatureAttr extends LMixNumericAttribute {
 *       AvgTemperatureAttr() {
 *           super("avg_temp", attrClass, null, null, 2, 5000);
 *       }
 *       protected Double calcRawValue() {
 *           // calculate average from bindAttrs
 *       }
 *   }
 *   AvgTemperatureAttr avg = new AvgTemperatureAttr();
 *   avg.registerBindAttrValue(phyTemp1);
 *   avg.registerBindAttrValue(phyTemp2);
 * </pre>
 *
 * @see LNumericAttribute
 * @see BindedPhyAttrData
 * @author coffee
 */
public abstract class LMixNumericAttribute extends LNumericAttribute {

    /** Map of bound physical attributes, keyed by attributeID, preserving insertion order */
    protected Map<String, BindedPhyAttrData> bindAttrs = new LinkedHashMap<>();

    /** Time window in milliseconds within which all bound attributes must be updated */
    private final long windowSize;

    /**
     * Constructor - creates a mix numeric attribute with the specified metadata and time window.
     *
     * @param attributeID the logic attribute ID
     * @param attrClass the attribute class
     * @param nativeUnit the native unit for this logic attribute
     * @param displayUnit the display unit for this logic attribute
     * @param displayPrecision the number of decimal places to display
     * @param windowSize time window in milliseconds within which all bound attributes must be updated
     */
    public LMixNumericAttribute(String attributeID, AttributeClass attrClass,
            UnitInfo nativeUnit, UnitInfo displayUnit, int displayPrecision, long windowSize) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision);
        this.windowSize = windowSize;
    }

    /**
     * When a bound physical attribute value is updated, check if all attributes
     * are updated within the time window. If so, calculate and update the logic value.
     *
     * <p><b>CRITICAL:</b> This method is synchronized because bus async dispatch
     * (2 thread pools) may invoke it concurrently.
     *
     * @param updatedAttr the physical attribute whose value has been updated
     */
    @Override
    public synchronized void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        String attrId = updatedAttr.getAttributeID();
        BindedPhyAttrData bad = bindAttrs.get(attrId);
        if (bad == null) return;

        bad.setUpdated(true);
        bad.setUpdatetime(System.currentTimeMillis());

        // Find the newest update time across all bound attributes
        long newestTime = bad.getUpdatetime();
        for (BindedPhyAttrData b : bindAttrs.values()) {
            if (b.getUpdatetime() > newestTime) {
                newestTime = b.getUpdatetime();
            }
        }

        // Mark expired attrs as not updated (outside time window from newest)
        for (BindedPhyAttrData b : bindAttrs.values()) {
            if (newestTime - b.getUpdatetime() > windowSize) {
                b.setUpdated(false);
            }
        }

        // Check if all bound attributes are updated within the window
        boolean allUpdated = true;
        for (BindedPhyAttrData b : bindAttrs.values()) {
            if (!b.isUpdated()) {
                allUpdated = false;
                break;
            }
        }

        if (allUpdated) {
            updateValue(calcRawValue(), AttributeStatus.NORMAL);
            // Reset all updated flags after successful aggregation
            for (BindedPhyAttrData b : bindAttrs.values()) {
                b.setUpdated(false);
            }
        }
    }

    /**
     * Returns the list of all bound physical attributes.
     *
     * @return list of all bound physical attributes
     */
    @Override
    public List<AttributeBase<?>> getBindedAttrs() {
        List<AttributeBase<?>> result = new ArrayList<>();
        for (BindedPhyAttrData bad : bindAttrs.values()) {
            result.add(bad.getBindPhyAttr());
        }
        return result;
    }

    /**
     * LMixNumericAttribute is read-only. Always throws UnsupportedOperationException.
     *
     * @param newDisplayValue ignored
     * @param fromUnit ignored
     * @return never returns normally
     * @throws UnsupportedOperationException always
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        throw new UnsupportedOperationException("LMixNumericAttribute is read-only");
    }

    /**
     * Registers a physical attribute to be bound to this mix attribute.
     *
     * <p>Must be called before {@link #updateBindAttrValue(AttributeBase)}.
     *
     * @param phyAttr the physical attribute to register
     */
    protected void registerBindAttrValue(AttributeBase<?> phyAttr) {
        bindAttrs.put(phyAttr.getAttributeID(),
                new BindedPhyAttrData(phyAttr.getAttributeID(), phyAttr, false, 0));
    }

    /**
     * Calculates the raw logic value from all bound physical attribute values.
     *
     * <p>Called only when all bound attributes have been updated within the time window.
     * Subclasses must implement this method to provide the aggregation logic
     * (e.g., sum, average, weighted average, etc.).
     *
     * @return the calculated logic value, or null if calculation is not possible
     */
    protected abstract Double calcRawValue();
}
