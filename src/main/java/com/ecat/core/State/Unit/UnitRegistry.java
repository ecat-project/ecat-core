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

import com.ecat.core.State.UnitInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 单位注册表:统一登记 ecat-core 中所有 {@link InternationalizedUnit} 单位枚举类。
 *
 * <p>用途:供需要枚举「全部单位」的场景使用(例如 ConfigFlow 的 output_unit 下拉框
 * 动态生成选项:key = {@link UnitInfo#getFullUnitString()}、label =
 * {@link UnitInfo#getDisplayName()}),避免各集成模块各自维护一份硬编码单位列表。</p>
 *
 * <p>自动同步范围:</p>
 * <ul>
 *   <li>已有单位类<b>新增常量</b>:自动出现({@link Class#getEnumConstants()} 遍历)。</li>
 *   <li><b>新增单位类</b>:在 {@link #UNIT_CLASSES} 加一行(此为 ecat-core 单一维护点,
 *       类比 {@code AttributeClass.unitClasses} 的显式列举惯用法)。</li>
 * </ul>
 *
 * <p>排除项:{@code InternationalizedUnit}(接口基类)、{@code NoConversionUnit}
 * (动态 {@code of(name)} 包装类,非 enum、不实现 InternationalizedUnit)。</p>
 *
 * <p>ClassLoader 约束:本注册表必须放在 ecat-core——单位类原生驻留于此,
 * 集成模块运行于隔离 ClassLoader,应由 core 统一发现后返回结果,避免跨 ClassLoader 扫描。</p>
 *
 * @author coffee
 */
public final class UnitRegistry {

    private UnitRegistry() {
    }

    /**
     * 所有 {@link InternationalizedUnit} 单位枚举类(ecat-core 单一维护点)。
     * 新增单位类时在此追加一行即可被 {@link #allUnitClasses()} / {@link #allUnits()} 发现。
     */
    @SuppressWarnings("unchecked")
    private static final Class<? extends UnitInfo>[] UNIT_CLASSES = new Class[]{
            AirMassUnit.class,
            AirVolumeUnit.class,
            CurrentUnit.class,
            DistanceUnit.class,
            FrequencyUnit.class,
            LiterFlowUnit.class,
            NoiseUnit.class,
            PowerUnit.class,
            PressureUnit.class,
            RatioUnit.class,
            RotationSpeedUnit.class,
            SpeedUnit.class,
            TemperatureUnit.class,
            TimeDeltaUnit.class,
            VoltageUnit.class,
            VolumeUnit.class,
            WeightUnit.class,
    };

    /**
     * 所有已登记的单位枚举类(不可变)。
     *
     * @return 单位类列表,供需要按类遍历常量的场景使用
     */
    public static List<Class<? extends UnitInfo>> allUnitClasses() {
        return Collections.unmodifiableList(Arrays.asList(UNIT_CLASSES));
    }

    /**
     * 所有已登记的单位枚举常量(扁平化,不可变副本)。
     *
     * @return 所有单位常量,顺序为 {@link #UNIT_CLASSES} 列举顺序、类内为枚举声明顺序
     */
    public static List<UnitInfo> allUnits() {
        List<UnitInfo> all = new ArrayList<>();
        for (Class<? extends UnitInfo> cls : UNIT_CLASSES) {
            UnitInfo[] constants = cls.getEnumConstants();
            if (constants != null) {
                all.addAll(Arrays.asList(constants));
            }
        }
        return Collections.unmodifiableList(all);
    }
}
