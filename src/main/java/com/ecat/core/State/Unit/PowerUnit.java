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
 * 功率单位类
 *
 * @author coffee
 */
public enum PowerUnit implements InternationalizedUnit {
    MILLIWATT("mW", 0.001), // 毫瓦
    WATT("W", 1.0), // 瓦
    KILOWATT("kW", 1000.0), // 千瓦
    MEGAWATT("MW", 1000000.0), // 兆瓦
    GIGAWATT("GW", 1000000000.0), // 吉瓦
    VOLT_AMPERE("VA", 1.0); // 伏特安

    private final String name;
    private final double ratio;

    PowerUnit(String name, double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "power";
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