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

package com.ecat.core.State.Unit;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * IrradianceUnit（辐照度单位 W/m²）枚举单测。
 *
 * <p>用于气象参数 solar_radiation（太阳总辐射）与 uv_radiation（太阳紫外辐射），
 * 两者 nativeUnit/displayUnit 均为 W_PER_SQUARE_METER。
 *
 * @author coffee
 */
public class IrradianceUnitTest {

    @Test
    public void testEnumValues() {
        IrradianceUnit[] units = IrradianceUnit.class.getEnumConstants();
        assertEquals(1, units.length);
        assertEquals(IrradianceUnit.W_PER_SQUARE_METER, units[0]);
    }

    @Test
    public void testGetName() {
        assertEquals("W/m²", IrradianceUnit.W_PER_SQUARE_METER.getName());
    }

    @Test
    public void testGetRatio() {
        assertEquals(1.0, IrradianceUnit.W_PER_SQUARE_METER.getRatio(), 0.0001);
    }

    @Test
    public void testGetUnitCategory() {
        assertEquals("irradiance", IrradianceUnit.W_PER_SQUARE_METER.getUnitCategory());
    }

    @Test
    public void testGetEnumName() {
        assertEquals("w_per_square_meter", IrradianceUnit.W_PER_SQUARE_METER.getEnumName());
    }

    @Test
    public void testGetDisplayName() {
        assertEquals("W/m²", IrradianceUnit.W_PER_SQUARE_METER.getDisplayName());
    }

    @Test
    public void testToString() {
        assertEquals("W/m²", IrradianceUnit.W_PER_SQUARE_METER.toString());
    }
}
