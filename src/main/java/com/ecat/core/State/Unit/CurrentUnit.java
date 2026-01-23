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

import com.ecat.core.I18n.I18nHelper;

/**
 * Current unit class
 *
 * @author coffee
 */
public enum CurrentUnit implements InternationalizedUnit {
    MILLIAMPERE(1.0), // 毫安
    AMPERE(1000.0); // 安

    private final double ratio;

    CurrentUnit(Double ratio) {
        this.ratio = ratio;
    }

    @Override
    public String getUnitCategory() {
        return "current";
    }

    @Override
    public String getEnumName() {
        return name().toLowerCase();
    }

    @Override
    public String getName() {
        return I18nHelper.t("state.unit.current." + name().toLowerCase());
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