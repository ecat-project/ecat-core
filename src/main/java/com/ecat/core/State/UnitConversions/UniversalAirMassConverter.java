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

package com.ecat.core.State.UnitConversions;

import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.State.UnitInfo;

/**
 * Universal air mass concentration converter.
 *
 * This converter provides a unified interface to convert any supported air quality unit
 * to a target air mass unit (μg/m³ or mg/m³). It intelligently handles different source
 * unit types and applies the appropriate conversion logic.
 *
 * <p>Features:
 * <ul>
 *   <li>Supports conversion from both air volume units (ppm, ppb) and air mass units</li>
 *   <li>Automatic detection of source unit type</li>
 *   <li>Integrated molecular weight constants for common pollutants</li>
 *   <li>Simple, one-step conversion interface</li>
 * </ul>
 *
 * <p>Conversion Logic:
 * <ul>
 *   <li><strong>Air Volume Units (ppm, ppb):</strong> Uses molecular weight for chemical conversion</li>
 *   <li><strong>Air Mass Units (μg/m³, mg/m³):</strong> Direct ratio conversion between mass units</li>
 *   <li><strong>Other Units:</strong> Not supported, throws IllegalArgumentException</li>
 * </ul>
 *
 * <p>Usage Examples:
 *
 * <p><strong>Example 1: Convert from volume concentration</strong>
 * <pre>{@code
 * // Convert SO2 from ppm to μg/m³
 * UniversalAirMassConverter converter = new UniversalAirMassConverter(
 *     AirVolumeUnit.PPM,           // Source: parts per million
 *     AirMassUnit.UGM3,            // Target: micrograms per cubic meter
 *     MolecularWeights.SO2         // SO2 molecular weight (64.066 g/mol)
 * );
 *
 * double ppmValue = 0.1; // 0.1 ppm SO2
 * double ugm3Value = converter.convert(ppmValue);
 * System.out.println("0.1 ppm SO2 = " + ugm3Value + " μg/m³");
 * }</pre>
 *
 * <p><strong>Example 2: Convert between mass units</strong>
 * <pre>{@code
 * // Convert CO from mg/m³ to μg/m³ (simple ratio conversion)
 * UniversalAirMassConverter converter = new UniversalAirMassConverter(
 *     AirMassUnit.MGM3,            // Source: milligrams per cubic meter
 *     AirMassUnit.UGM3,            // Target: micrograms per cubic meter
 *     MolecularWeights.CO          // CO molecular weight (28.010 g/mol)
 * );
 *
 * double mgm3Value = 2.5; // 2.5 mg/m³ CO
 * double ugm3Value = converter.convert(mgm3Value);
 * System.out.println("2.5 mg/m³ CO = " + ugm3Value + " μg/m³");
 * }</pre>
 *
 * <p><strong>Example 3: Convert from ppb to mg/m³</strong>
 * <pre>{@code
 * // Convert NO2 from ppb to mg/m³
 * UniversalAirMassConverter converter = new UniversalAirMassConverter(
 *     AirVolumeUnit.PPB,           // Source: parts per billion
 *     AirMassUnit.MGM3,            // Target: milligrams per cubic meter
 *     MolecularWeights.NO2         // NO2 molecular weight (46.006 g/mol)
 * );
 *
 * double ppbValue = 100.0; // 100 ppb NO2
 * double mgm3Value = converter.convert(ppbValue);
 * System.out.println("100 ppb NO2 = " + mgm3Value + " mg/m³");
 * }</pre>
 *
 * <p><strong>Example 4: Using with UnitConverter</strong>
 * <pre>{@code
 * // Use with the UnitConverter framework
 * UnitConverter unitConverter = new UnitConverter();
 * UniversalAirMassConverter converter = new UniversalAirMassConverter(
 *     AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.O3
 * );
 *
 * double ppmValue = 0.05;
 * double ugm3Value = unitConverter.convertValue(ppmValue, converter);
 * System.out.println("0.05 ppm O3 = " + ugm3Value + " μg/m³");
 * }</pre>
 *
 * <p><strong>Common Molecular Weights:</strong>
 * <pre>
 * SO₂: MolecularWeights.SO2  = 64.066 g/mol
 * NO₂: MolecularWeights.NO2 = 46.006 g/mol
 * CO:  MolecularWeights.CO  = 28.010 g/mol
 * O₃:  MolecularWeights.O3  = 47.998 g/mol
 * NO:  MolecularWeights.NO  = 30.006 g/mol
 * </pre>
 *
 * @author coffee
 * @version 1.0
 */
public class UniversalAirMassConverter implements UnitConversion {
    private final UnitInfo sourceUnit;
    private final AirMassUnit targetUnit;
    private final double molecularWeight;

    /**
     * Creates a universal converter that can convert from any supported unit to air mass units.
     *
     * @param sourceUnit The source unit (must be AirVolumeUnit or AirMassUnit)
     * @param targetUnit The target air mass unit (μg/m³ or mg/m³)
     * @param molecularWeight The molecular weight of the substance in g/mol
     * @throws IllegalArgumentException if sourceUnit or targetUnit is null
     * @throws IllegalArgumentException if sourceUnit is not supported
     */
    public UniversalAirMassConverter(UnitInfo sourceUnit, AirMassUnit targetUnit, double molecularWeight) {
        if (sourceUnit == null) {
            throw new IllegalArgumentException("Source unit cannot be null");
        }
        if (targetUnit == null) {
            throw new IllegalArgumentException("Target unit cannot be null");
        }
        if (molecularWeight <= 0) {
            throw new IllegalArgumentException("Molecular weight must be positive");
        }

        this.sourceUnit = sourceUnit;
        this.targetUnit = targetUnit;
        this.molecularWeight = molecularWeight;
    }

    /**
     * Converts a value from the source unit to the target air mass unit.
     *
     * <p>Conversion logic:
     * <ul>
     *   <li>If source is {@link AirMassUnit}: Direct ratio conversion between mass units</li>
     *   <li>If source is {@link AirVolumeUnit}: Chemical conversion using molecular weight</li>
     *   <li>Otherwise: Throws {@link IllegalArgumentException}</li>
     * </ul>
     *
     * @param value The value to be converted
     * @return The converted value in the target air mass unit
     * @throws IllegalArgumentException if source unit type is not supported
     */
    @Override
    public double convert(double value) {
        if (sourceUnit instanceof AirMassUnit) {
            // Same type mass units: direct ratio conversion
            double sourceRatio = ((AirMassUnit) sourceUnit).getRatio();
            return value * sourceRatio / targetUnit.getRatio();

        } else if (sourceUnit instanceof AirVolumeUnit) {
            // Volume units: need molecular weight for chemical conversion
            double sourceRatio = ((AirVolumeUnit) sourceUnit).getRatio();
            return value * sourceRatio * molecularWeight / 22.4 / targetUnit.getRatio();

        } else {
            // Other types not supported
            throw new IllegalArgumentException("Unsupported source unit type: " +
                    sourceUnit.getClass().getSimpleName() +
                    ". Supported types: AirVolumeUnit, AirMassUnit");
        }
    }

    /**
     * Gets the source unit used for conversion.
     *
     * @return The source unit
     */
    public UnitInfo getSourceUnit() {
        return sourceUnit;
    }

    /**
     * Gets the target air mass unit.
     *
     * @return The target air mass unit
     */
    public AirMassUnit getTargetUnit() {
        return targetUnit;
    }

    /**
     * Gets the molecular weight used for conversion.
     *
     * @return The molecular weight in g/mol
     */
    public double getMolecularWeight() {
        return molecularWeight;
    }

    /**
     * Checks if the source unit is a volume concentration unit.
     *
     * @return true if source unit is AirVolumeUnit, false otherwise
     */
    public boolean isFromVolumeUnit() {
        return sourceUnit instanceof AirVolumeUnit;
    }

    /**
     * Checks if the source unit is a mass concentration unit.
     *
     * @return true if source unit is AirMassUnit, false otherwise
     */
    public boolean isFromMassUnit() {
        return sourceUnit instanceof AirMassUnit;
    }

    @Override
    public String toString() {
        return String.format("UniversalAirMassConverter{%s -> %s, MW=%.3f, %s}",
                sourceUnit.toString(),
                targetUnit.getName(),
                molecularWeight,
                isFromVolumeUnit() ? "volume->mass" : "mass->mass");
    }
}
