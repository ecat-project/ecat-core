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
import com.ecat.core.Science.AirQuality.Consts.MolecularWeights;

/**
 * Air volume to air mass unit converter.
 *
 * This converter transforms air volume concentration values (e.g., ppm, ppb)
 * to air mass concentration values (e.g., μg/m³, mg/m³) using the molecular weight
 * of the substance and the standard molar volume.
 *
 * <p>The conversion formula is based on the ideal gas law:
 * <pre>
 * C_mass = C_volume × M_molecular / V_molar × ratio_factor
 * where V_molar = 24.45 L/mol (standard conditions: 25°C, 1 atm)
 * </pre>
 *
 * <p>Usage Example:
 * <pre>{@code
 * // Convert SO2 from ppm to μg/m³
 * AirVolumeToAirMass converter = new AirVolumeToAirMass(
 *     AirVolumeUnit.PPM,    // Source: parts per million
 *     AirMassUnit.UGM3,     // Target: micrograms per cubic meter
 *     64.066               // SO2 molecular weight (g/mol)
 * );
 *
 * double volumeConcentration = 0.5; // 0.5 ppm
 * double massConcentration = converter.convert(volumeConcentration);
 * System.out.println("0.5 ppm SO2 = " + massConcentration + " μg/m³");
 * }</pre>
 *
 * <p>Another example with O₃ at higher concentrations:
 * <pre>{@code
 * // Convert O3 from ppb to mg/m³
 * AirVolumeToAirMass converter = new AirVolumeToAirMass(
 *     AirVolumeUnit.PPB,    // Source: parts per billion
 *     AirMassUnit.MGM3,     // Target: milligrams per cubic meter
 *     47.998               // O3 molecular weight (g/mol)
 * );
 *
 * double volumeConcentration = 150.0; // 150 ppb
 * double massConcentration = converter.convert(volumeConcentration);
 * System.out.println("150 ppb O₃ = " + massConcentration + " mg/m³");
 * }</pre>
 *
 * <p>Common molecular weights for reference:
 * <pre>
 * SO₂: 64.066 g/mol
 * NO₂: 46.006 g/mol
 * CO:  28.010 g/mol
 * O₃:  47.998 g/mol
 * NO:  30.006 g/mol
 * </pre>
 *
 * @author coffee
 * @version 1.0
 */
public class AirVolumeToAirMass implements UnitConversion {
    private final AirVolumeUnit fromUnit;
    private final AirMassUnit toUnit;
    private final double molecularWeight;

    /**
     * Creates a converter for air volume to air mass concentration conversion.
     *
     * @param fromUnit The source air volume unit (e.g., ppm, ppb)
     * @param toUnit The target air mass unit (e.g., μg/m³, mg/m³)
     * @param molecularWeight The molecular weight of the substance in g/mol
     * @throws IllegalArgumentException if any parameter is null
     */
    public AirVolumeToAirMass(AirVolumeUnit fromUnit, AirMassUnit toUnit, double molecularWeight) {
        if (fromUnit == null) {
            throw new IllegalArgumentException("Source unit cannot be null");
        }
        if (toUnit == null) {
            throw new IllegalArgumentException("Target unit cannot be null");
        }
        if (molecularWeight <= 0) {
            throw new IllegalArgumentException("Molecular weight must be positive");
        }

        this.fromUnit = fromUnit;
        this.toUnit = toUnit;
        this.molecularWeight = molecularWeight;
    }

    /**
     * Converts air volume concentration to air mass concentration.
     *
     * <p>Conversion formula:
     * <pre>
     * result = value × fromUnit.ratio × molecularWeight / MOLAR_VOLUME_25C / toUnit.ratio
     * </pre>
     *
     * @param value The air volume concentration value to convert
     * @return The equivalent air mass concentration value
     */
    @Override
    public double convert(double value) {
        double result = value * fromUnit.getRatio() * molecularWeight / MolecularWeights.MOLAR_VOLUME_25C / toUnit.getRatio();
        return result;
    }

    /**
     * Gets the source air volume unit.
     *
     * @return The source unit
     */
    public AirVolumeUnit getFromUnit() {
        return fromUnit;
    }

    /**
     * Gets the target air mass unit.
     *
     * @return The target unit
     */
    public AirMassUnit getToUnit() {
        return toUnit;
    }

    /**
     * Gets the molecular weight used for conversion.
     *
     * @return The molecular weight in g/mol
     */
    public double getMolecularWeight() {
        return molecularWeight;
    }

    @Override
    public String toString() {
        return String.format("AirVolumeToAirMass{%s -> %s, MW=%.3f}",
                fromUnit.getName(), toUnit.getName(), molecularWeight);
    }
}
