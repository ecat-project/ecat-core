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

/**
 * 辐照度单位枚举（太阳辐射 / 紫外辐射，W/m²）。
 *
 * <p>用于气象参数 solar_radiation（太阳总辐射）与 uv_radiation（太阳紫外辐射），
 * 两者 nativeUnit/displayUnit 均为 {@link #W_PER_SQUARE_METER}。
 *
 * @author coffee
 */
public enum IrradianceUnit implements InternationalizedUnit {
    W_PER_SQUARE_METER("W/m²", 1.0);

    private final String name;
    private final double ratio;

    IrradianceUnit(String name, double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "irradiance";
    }

    @Override
    public String getEnumName() {
        return name().toLowerCase();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Double getRatio() {
        return ratio;
    }

    @Override
    public String toString() {
        return getName();
    }
}
