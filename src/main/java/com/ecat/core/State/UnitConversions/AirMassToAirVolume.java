package com.ecat.core.State.UnitConversions;

import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;

/**
 * Air mass to air volume unit converter.
 *
 * This converter transforms air mass concentration values (e.g., μg/m³, mg/m³)
 * to air volume concentration values (e.g., ppm, ppb) using the molecular weight
 * of the substance and the standard molar volume.
 *
 * <p>The conversion formula is based on the ideal gas law:
 * <pre>
 * C_volume = C_mass × V_molar / M_molecular × ratio_factor
 * where V_molar = 22.4 L/mol (standard conditions)
 * </pre>
 *
 * <p>Usage Example:
 * <pre>{@code
 * // Convert SO2 from μg/m³ to ppm
 * AirMassToAirVolume converter = new AirMassToAirVolume(
 *     AirMassUnit.UGM3,     // Source: micrograms per cubic meter
 *     AirVolumeUnit.PPM,    // Target: parts per million
 *     64.066               // SO2 molecular weight (g/mol)
 * );
 *
 * double massConcentration = 100.0; // 100 μg/m³
 * double volumeConcentration = converter.convert(massConcentration);
 * System.out.println("100 μg/m³ SO2 = " + volumeConcentration + " ppm");
 * }</pre>
 *
 * <p>Another example with CO:
 * <pre>{@code
 * // Convert CO from mg/m³ to ppb
 * AirMassToAirVolume converter = new AirMassToAirVolume(
 *     AirMassUnit.MGM3,     // Source: milligrams per cubic meter
 *     AirVolumeUnit.PPB,    // Target: parts per billion
 *     28.010               // CO molecular weight (g/mol)
 * );
 *
 * double massConcentration = 2.5; // 2.5 mg/m³
 * double volumeConcentration = converter.convert(massConcentration);
 * System.out.println("2.5 mg/m³ CO = " + volumeConcentration + " ppb");
 * }</pre>
 *
 * @author coffee
 * @version 1.0
 */
public class AirMassToAirVolume implements UnitConversion {
    private final AirMassUnit fromUnit;
    private final AirVolumeUnit toUnit;
    private final double molecularWeight;

    /**
     * Creates a converter for air mass to air volume concentration conversion.
     *
     * @param fromUnit The source air mass unit (e.g., μg/m³, mg/m³)
     * @param toUnit The target air volume unit (e.g., ppm, ppb)
     * @param molecularWeight The molecular weight of the substance in g/mol
     * @throws IllegalArgumentException if any parameter is null
     */
    public AirMassToAirVolume(AirMassUnit fromUnit, AirVolumeUnit toUnit, double molecularWeight) {
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
     * Converts air mass concentration to air volume concentration.
     *
     * <p>Conversion formula:
     * <pre>
     * result = value × fromUnit.ratio × 22.4 / molecularWeight / toUnit.ratio
     * </pre>
     *
     * @param value The air mass concentration value to convert
     * @return The equivalent air volume concentration value
     */
    @Override
    public double convert(double value) {
        double result = value * fromUnit.getRatio() * 22.4 / molecularWeight / toUnit.getRatio();
        return result;
    }

    /**
     * Gets the source air mass unit.
     *
     * @return The source unit
     */
    public AirMassUnit getFromUnit() {
        return fromUnit;
    }

    /**
     * Gets the target air volume unit.
     *
     * @return The target unit
     */
    public AirVolumeUnit getToUnit() {
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
        return String.format("AirMassToAirVolume{%s -> %s, MW=%.3f}",
                fromUnit.getName(), toUnit.getName(), molecularWeight);
    }
}
