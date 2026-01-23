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
 * Liter flow unit class
 *
 * @author coffee
 */
public enum LiterFlowUnit implements InternationalizedUnit {
    L_PER_SECOND("L/s", 1.0), // 升每秒
    ML_PER_MINUTE("ml/min", 1.0 / 1000 * 60), // 毫升每分钟
    L_PER_MINUTE("L/min", 1.0 * 60), // 升每分钟
    L_PER_HOUR("L/h", 1.0 * 60 * 60); // 升每小时

    private final String name;
    private final Double ratio;

    LiterFlowUnit(String name, Double ratio) {
        this.name = name;
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "literflow";
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
