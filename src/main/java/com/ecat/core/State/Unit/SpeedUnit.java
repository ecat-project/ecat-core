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
 * 速度单位类
 *
 * @author coffee
 */
public enum SpeedUnit implements InternationalizedUnit {
    MILLIMETER_PER_SECOND("mm/s", 0.001), // 毫米每秒
    METER_PER_SECOND("m/s", 1.0), // 米每秒
    KILOMETER_PER_HOUR("km/h", 1000.0/3600.0), // 千米每小时
    KNOT("knot", 1852.0/3600.0), // 节
    MILE_PER_HOUR("mph", 1609.344/3600.0), // 英里每小时
    FOOT_PER_SECOND("ft/s", 0.3048); // 英尺每秒

    private final String name;
    private final double ratio;

    SpeedUnit(String name, double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "speed";
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