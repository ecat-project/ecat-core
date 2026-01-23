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
 * Pressure unit class
 *
 * @author coffee
 */
public enum PressureUnit implements InternationalizedUnit {
    PA("Pa", 1.0), // 帕斯卡
    HPA("hPa", 100.0), // 百帕
    KPA("kPa", 1000.0), // 千帕
    MPA("MPa", 1000000.0), // 兆帕
    ATM("atm", 101325.0), // 标准大气压  1atm = 101325Pa
    MMHG("mmHg", 75960.3 ); // 毫米汞柱
    private final String name;
    private final Double ratio;

    PressureUnit(String name, Double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "pressure";
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
