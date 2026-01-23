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

package com.ecat.core.State;

import java.util.concurrent.CompletableFuture;

/**
 * Attribute public interface for WebUI
 * 
 * @author coffee
 */
public interface AttributeAbility<T>{
    boolean canUnitChange(); //是否可修改单位
    boolean changeDisplayUnit(UnitInfo newDisplayUnit); //修改单位
    boolean changeDisplayPrecision(int newPrecision); //修改小数位
    int getDisplayPrecision(); //获取小数位
    boolean canValueChange(); //是否允许外部修改值
    boolean setStatus(AttributeStatus newStatus); //设置数据状态
    /**
     * 获取数据状态
     * @return
     */
    AttributeStatus getStatus();

    /**
     * 获取原始数据
     * @return
     *      无值时为null
     */
    T getValue();

    /**
     * 设置展示数据（可能是转换后的）
     * <pre>
     * 适合子类override实现具体逻辑，最后调用setDisplayValue(String newDisplayValue, UnitInfo fromUnit)
     * </pre>
     * @param newDisplayValue
     * @return
     */
    CompletableFuture<Boolean> setDisplayValue(String newDisplayValue); 

    /**
     * 设置展示数据，使用单次指定的展示单位，不修改设置的显示单位
     * <pre>
     * 用于外部用户或其他设备设置本设备数据使用
     * 不适合子类override
     * </pre>
     * @param newDisplayValue 指定单位的value
     * @param fromUnit newDisplayValue的单位
     * @return
     */
    CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit);

    /**
     * 获取符合展示单位的数据（可能是转换后的），使用已设置的展示单位
     * @return
     *      String值或无值时为null
     */
    String getDisplayValue();

    /**
     * 获取符合展示单位的数据（可能是转换后的），使用单次指定的展示单位，不修改设置的显示单位
     * @param toUnit
     * @return
     *      String值或无值时为null
     */
    String getDisplayValue(UnitInfo toUnit);

    /**
     * 获取展示单位字符串
     * @return
     *      未设置单位返回""
     */
    String getDisplayUnitStr();

    /**
     * 获取展示单位
     * @return
     *      UnitInfo 或 null
     */
    UnitInfo getDisplayUnit();

    /**
     * 获取属性的类型
     * @return 属性类型枚举
     */
    AttributeType getAttributeType();
}
