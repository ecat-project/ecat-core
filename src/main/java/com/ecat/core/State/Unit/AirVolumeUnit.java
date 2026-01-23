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
 * Air volume unit class
 *
 * @author coffee
 */
public enum AirVolumeUnit implements InternationalizedUnit {
    PPB("ppb", 1000.0), // 十亿分之一
    PPM("ppm", 1000.0 * 1000.0), // 百万分之一
    UMOL_PER_MOL("μmol/mol", 1000.0), // 微摩尔每摩尔
    NMOL_PER_MOL("nmol/mol", 1000.0 * 1000.0); // 纳摩尔每摩尔

    private final String name;
    private final Double ratio;

    AirVolumeUnit(String name, Double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "airvolume";
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
