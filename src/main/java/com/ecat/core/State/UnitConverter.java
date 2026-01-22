package com.ecat.core.State;

import com.ecat.core.State.UnitConversions.UnitConversion;
import com.ecat.core.State.UnitConversions.SameUnitClassConverter;
import com.ecat.core.State.UnitInfo;

/**
 * Unit converter class that provides a unified interface for performing unit conversions.
 *
 * <p>This class acts as a facade for various unit conversion implementations. It uses the
 * {@link UnitConversion} interface to perform the actual conversion logic.
 *
 * <p>Usage Example:
 * <pre>{@code
 * // Create converter instance
 * UnitConverter converter = new UnitConverter();
 *
 * // Create a specific conversion implementation
 * AirVolumeToAirMass conversion = new AirVolumeToAirMass(
 *     AirVolumeUnit.PPM, AirMassUnit.MGM3, 44.01
 * );
 *
 * // Perform conversion
 * double result = converter.convertValue(10.0, conversion);
 *
 * // Or use the universal converter
 * UniversalAirMassConverter universal = new UniversalAirMassConverter(
 *     AirVolumeUnit.PPM, AirMassUnit.UGM3, MolecularWeights.SO2
 * );
 * double universalResult = converter.convertValue(5.0, universal);
 * }</pre>
 *
 * @author coffee
 * @version 1.1
 */
public class UnitConverter {

    /**
     * Converts a value using the provided unit conversion implementation.
     *
     * @param value The value to be converted
     * @param conversion The unit conversion implementation to use
     * @return The converted value
     * @throws IllegalArgumentException if conversion is null
     */
    public double convertValue(double value, UnitConversion conversion) {
        if (conversion == null) {
            throw new IllegalArgumentException("Conversion cannot be null");
        }
        return conversion.convert(value);
    }

    /**
     * Converts a value between units of the same class (same enumeration type).
     * This is a convenience method that delegates to {@link SameUnitClassConverter}.
     *
     * <p>Usage Example:
     * <pre>{@code
     * // Convert between mass concentration units
     * double result = converter.convertSameUnitClass(1.5, AirMassUnit.UGM3, AirMassUnit.MGM3);
     *
     * // Convert between volume concentration units
     * double result2 = converter.convertSameUnitClass(100, AirVolumeUnit.PPM, AirVolumeUnit.PPB);
     * }</pre>
     *
     * @param value The value to be converted
     * @param from The source unit
     * @param to The target unit
     * @return The converted value
     * @throws IllegalArgumentException if any parameter is null or if units are from different classes
     */
    public double convertSameUnitClass(double value, UnitInfo from, UnitInfo to) {
        return SameUnitClassConverter.convert(value, from, to);
    }
}
