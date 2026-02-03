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

import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.State.UnitConversions.AirVolumeToAirMass;
import com.ecat.core.State.UnitConversions.AirMassToAirVolume;
import com.ecat.core.Utils.NumberFormatter;
import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

/**
 * Air Quality Attribute for environmental monitoring parameters.
 *
 * <p>This attribute supports unit conversion for air quality measurements, including:
 * <ul>
 *   <li>Gas pollutants (SO2, NO2, O3, CO): Support cross-class conversion between mass concentration
 *       (e.g., µg/m³, mg/m³) and volume concentration (e.g., ppm, ppb) when molecularWeight is set.</li>
 *   <li>Particulate matter (PM2.5, PM10): Only support same-class conversion within mass concentration units
 *       (e.g., µg/m³ to mg/m³). Molecular weight should be null to prevent invalid cross-class conversion.</li>
 * </ul>
 *
 * @apiNote displayName i18n supported, path: state.aq_attr.{attributeID}
 *
 * @author coffee
 */
public class AQAttribute extends NumericAttribute {

    /**
     * Molecular weight in g/mol for gas pollutants.
     *
     * <p>Required for cross-class unit conversion (mass concentration <-> volume concentration).
     * For gas pollutants (SO2, NO2, O3, CO), set to the molecular weight of the gas.
     * For particulate matter (PM2.5, PM10), leave as null to disable cross-class conversion.
     *
     * <p>Common molecular weights:
     * <ul>
     *   <li>SO2: 64.066 g/mol</li>
     *   <li>NO2: 46.006 g/mol</li>
     *   <li>O3: 47.998 g/mol</li>
     *   <li>CO: 28.010 g/mol</li>
     *   <li>NO: 30.006 g/mol</li>
     * </ul>
     */
    public Double molecularWeight;

    /**
     * Constructor with I18n support and molecular weight for gas pollutants.
     *
     * <p>Use this constructor for gas pollutants that require cross-class unit conversion
     * between mass concentration and volume concentration.
     *
     * @param attributeID The unique identifier for this attribute
     * @param attrClass The attribute class (e.g., AttributeClass.SO2, AttributeClass.NO2)
     * @param nativeUnit The native unit of the attribute
     * @param displayUnit The display unit of the attribute
     * @param displayPrecision The number of decimal places to display
     * @param unitChangeable Whether the unit can be changed by the user
     * @param valueChangeable Whether the value can be changed by the user
     * @param molecularWeight The molecular weight in g/mol (must be positive for cross-class conversion)
     * @throws IllegalArgumentException if molecularWeight is not null and not positive
     */
    public AQAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Double molecularWeight) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable);

        if (molecularWeight != null && molecularWeight <= 0) {
            throw new IllegalArgumentException("Molecular weight must be positive or null");
        }
        this.molecularWeight = molecularWeight;
    }

    /**
     * Constructor with I18n support without molecular weight for particulate matter.
     *
     * <p>Use this constructor for particulate matter (PM2.5, PM10) that only requires
     * same-class unit conversion within mass concentration units.
     * Cross-class conversion to volume concentration units (ppm, ppb) is not supported.
     *
     * @param attributeID The unique identifier for this attribute
     * @param attrClass The attribute class (e.g., AttributeClass.PM2_5, AttributeClass.PM10)
     * @param nativeUnit The native unit of the attribute (should be AirMassUnit)
     * @param displayUnit The display unit of the attribute (should be AirMassUnit)
     * @param displayPrecision The number of decimal places to display
     * @param unitChangeable Whether the unit can be changed by the user
     * @param valueChangeable Whether the value can be changed by the user
     */
    public AQAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable) {
        this(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision,
             unitChangeable, valueChangeable, null);
    }

    /**
     * Constructor with displayName and molecular weight for gas pollutants.
     *
     * <p>Use this constructor for gas pollutants that require cross-class unit conversion
     * and need a custom display name.
     *
     * @param attributeID The unique identifier for this attribute
     * @param displayName The custom display name (takes priority over I18n)
     * @param attrClass The attribute class (e.g., AttributeClass.SO2, AttributeClass.NO2)
     * @param nativeUnit The native unit of the attribute
     * @param displayUnit The display unit of the attribute
     * @param displayPrecision The number of decimal places to display
     * @param unitChangeable Whether the unit can be changed by the user
     * @param valueChangeable Whether the value can be changed by the user
     * @param molecularWeight The molecular weight in g/mol (must be positive for cross-class conversion)
     * @throws IllegalArgumentException if molecularWeight is not null and not positive
     */
    public AQAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Double molecularWeight) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable);

        if (molecularWeight != null && molecularWeight <= 0) {
            throw new IllegalArgumentException("Molecular weight must be positive or null");
        }
        this.molecularWeight = molecularWeight;
    }

    /**
     * Constructor with displayName without molecular weight for particulate matter.
     *
     * <p>Use this constructor for particulate matter (PM2.5, PM10) that only requires
     * same-class unit conversion within mass concentration units and needs a custom display name.
     * Cross-class conversion to volume concentration units (ppm, ppb) is not supported.
     *
     * @param attributeID The unique identifier for this attribute
     * @param displayName The custom display name (takes priority over I18n)
     * @param attrClass The attribute class (e.g., AttributeClass.PM2_5, AttributeClass.PM10)
     * @param nativeUnit The native unit of the attribute (should be AirMassUnit)
     * @param displayUnit The display unit of the attribute (should be AirMassUnit)
     * @param displayPrecision The number of decimal places to display
     * @param unitChangeable Whether the unit can be changed by the user
     * @param valueChangeable Whether the value can be changed by the user
     */
    public AQAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable) {
        this(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision,
             unitChangeable, valueChangeable, null);
    }

    /**
     * Checks whether this attribute supports cross-class unit conversion.
     *
     * <p>Cross-class conversion refers to conversion between mass concentration units
     * (e.g., µg/m³, mg/m³) and volume concentration units (e.g., ppm, ppb).
     *
     * @return true if molecularWeight is set (gas pollutants), false otherwise (particulate matter)
     */
    public boolean supportsCrossClassConversion() {
        return molecularWeight != null && molecularWeight > 0;
    }

    /**
     * Validates that cross-class conversion is supported before attempting conversion.
     *
     * @throws IllegalStateException if molecularWeight is not set or invalid
     */
    private void validateCrossClassConversion() {
        if (!supportsCrossClassConversion()) {
            throw new IllegalStateException(
                "Cross-class unit conversion is not supported for this attribute. " +
                "The attribute does not have a molecular weight set. " +
                "For particulate matter (PM2.5, PM10), use mass concentration units (µg/m³, mg/m³) only."
            );
        }
    }

    @Override
    public boolean updateValue(Double value) {
        return super.updateValue(value);
    }

    @Override
    public boolean updateValue(Double value, AttributeStatus newStatus) {
        return super.updateValue(value, newStatus);
    }

    @Override
    public String getDisplayValue(UnitInfo toUnit){
        if (value == null) return null;

        if (toUnit == null || nativeUnit == null) {
            return NumberFormatter.formatValue(value, displayPrecision);
        }

        Double displayValue;
        // Same class conversion (e.g., µg/m³ to mg/m³)
        if(nativeUnit.getClass().equals(toUnit.getClass())){
            displayValue = value * nativeUnit.convertUnit(toUnit);
        }
        // Cross-class conversion (e.g., µg/m³ to ppm) requires molecular weight
        else{
            validateCrossClassConversion();

            UnitConverter converter = new UnitConverter();
            if(nativeUnit.getClass().equals(AirVolumeUnit.class) && toUnit.getClass().equals(AirMassUnit.class) ){
                AirVolumeToAirMass volumeToMass = new AirVolumeToAirMass(
                    (AirVolumeUnit)nativeUnit, (AirMassUnit)toUnit, molecularWeight);
                displayValue = converter.convertValue(value, volumeToMass);
            }
            else if(nativeUnit.getClass().equals(AirMassUnit.class) && toUnit.getClass().equals(AirVolumeUnit.class) ){
                AirMassToAirVolume massToVolume = new AirMassToAirVolume(
                    (AirMassUnit)nativeUnit, (AirVolumeUnit)toUnit, molecularWeight);
                displayValue = converter.convertValue(value, massToVolume);
            }
            else{
                throw new RuntimeException(I18nHelper.t("error.invalid_unit_conversion"));
            }
        }
        return NumberFormatter.formatValue(displayValue, displayPrecision);
    }

    @Override
    protected Double convertFromUnitImp(Double fromValue, UnitInfo fromUnit)
    {
        if (fromValue == null) return null;
        if (fromUnit == null || nativeUnit == null) return fromValue;

        UnitInfo toUnit = nativeUnit;
        Double displayValue;

        // Same class conversion
        if(fromUnit.getClass().equals(toUnit.getClass())){
            displayValue = fromValue * fromUnit.convertUnit(toUnit);
        }
        // Cross-class conversion requires molecular weight
        else{
            validateCrossClassConversion();

            UnitConverter converter = new UnitConverter();
            if(fromUnit.getClass().equals(AirVolumeUnit.class) && toUnit.getClass().equals(AirMassUnit.class) ){
                AirVolumeToAirMass volumeToMass = new AirVolumeToAirMass(
                    (AirVolumeUnit)fromUnit, (AirMassUnit)toUnit, molecularWeight);
                displayValue = converter.convertValue(fromValue, volumeToMass);
            }
            else if(fromUnit.getClass().equals(AirMassUnit.class) && toUnit.getClass().equals(AirVolumeUnit.class) ){
                AirMassToAirVolume massToVolume = new AirMassToAirVolume(
                    (AirMassUnit)fromUnit, (AirVolumeUnit)toUnit, molecularWeight);
                displayValue = converter.convertValue(fromValue, massToVolume);
            }
            else{
                throw new RuntimeException(I18nHelper.t("error.invalid_unit_conversion"));
            }
        }

        return displayValue;
    }

    @Override
    public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) {
        if (value == null) {
            return null;
        }
        if (fromUnit == null || toUnit == null) {
            throw new NullPointerException("fromUnit and toUnit cannot be null");
        }

        // Same class conversion: delegate to parent class
        if (fromUnit.getClass().equals(toUnit.getClass())) {
            return super.convertValueToUnit(value, fromUnit, toUnit);
        }

        // Cross-class conversion: validate molecular weight first
        validateCrossClassConversion();

        // Volume concentration <-> Mass concentration
        UnitConverter converter = new UnitConverter();
        if (fromUnit.getClass().equals(AirVolumeUnit.class) && toUnit.getClass().equals(AirMassUnit.class)) {
            AirVolumeToAirMass conversion = new AirVolumeToAirMass(
                (AirVolumeUnit) fromUnit, (AirMassUnit) toUnit, molecularWeight);
            return converter.convertValue(value, conversion);
        }
        else if (fromUnit.getClass().equals(AirMassUnit.class) && toUnit.getClass().equals(AirVolumeUnit.class)) {
            AirMassToAirVolume conversion = new AirMassToAirVolume(
                (AirMassUnit) fromUnit, (AirVolumeUnit) toUnit, molecularWeight);
            return converter.convertValue(value, conversion);
        }
        else {
            throw new RuntimeException(I18nHelper.t("error.invalid_unit_conversion"));
        }
    }

    @Override
    public ConfigDefinition getValueDefinition() {
        // AQ attributes typically don't need validation by default
        // Subclasses can override this to add specific validation rules
        return null;
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.aq_attr.", "");
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.NUMERIC;
    }

}
