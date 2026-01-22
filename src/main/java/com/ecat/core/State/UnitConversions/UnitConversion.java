package com.ecat.core.State.UnitConversions;

/**
 * Unit conversion interface
 *
 * This interface defines the contract for unit conversion implementations.
 * All unit converters must implement this interface to provide a consistent
 * conversion API.
 *
 * <p>Usage Example:
 * <pre>{@code
 * // Create a converter instance
 * UnitConversion converter = new AirVolumeToAirMass(
 *     AirVolumeUnit.PPM, AirMassUnit.MGM3, 44.01
 * );
 *
 * // Perform conversion
 * double result = converter.convert(10.0);
 *
 * // Use with UnitConverter
 * UnitConverter unitConverter = new UnitConverter();
 * double finalResult = unitConverter.convertValue(10.0, converter);
 * }</pre>
 *
 * @author coffee
 * @version 1.0
 */
public interface UnitConversion {

    /**
     * Convert a value from source unit to target unit.
     *
     * @param value The value to be converted
     * @return The converted value
     */
    double convert(double value);
}
