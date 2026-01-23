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
