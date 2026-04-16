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

package com.ecat.core.LogicState;

/**
 * 数值型逻辑属性定义。
 *
 * <p>用于 standalone 数值属性（阈值、浓度、温度等），
 * 由 {@code mapping.getAttr()} 创建 {@link LNumericAttribute} 实例时使用。
 * 基类 {@link LogicAttributeDefine} 已包含 nativeUnit、displayUnit、displayPrecision 等字段，
 * 本类当前无额外字段，作为类型标记和未来扩展点。
 *
 * @see LogicAttributeDefine
 * @see LNumericAttribute
 * @see LNumericAttribute
 * @author coffee
 */
public class NumericAttrDef extends LogicAttributeDefine {
}
