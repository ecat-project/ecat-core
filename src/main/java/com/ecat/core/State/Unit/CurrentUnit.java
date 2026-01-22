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