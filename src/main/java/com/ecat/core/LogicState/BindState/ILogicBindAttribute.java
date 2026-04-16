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

package com.ecat.core.LogicState.BindState;

import com.ecat.core.LogicState.ILogicAttribute;

/**
 * 逻辑设备间绑定属性标记接口。纯类型标记，无新增方法。
 *
 * <p>ILogicBindAttribute 扩展 {@link ILogicAttribute}，作为逻辑设备间属性绑定的
 * 类型标记接口。实现类表示该逻辑属性的值来源于另一个逻辑设备的属性，
 * 而非直接绑定物理设备的属性。
 *
 * <p>所有方法继承自 {@link ILogicAttribute}：
 * <ul>
 *   <li>{@link ILogicAttribute#updateBindAttrValue(com.ecat.core.State.AttributeBase)} -
 *       物理源和逻辑源统一复用此方法进行值更新</li>
 *   <li>{@link ILogicAttribute#getBindedAttrs()} -
 *       用于 buildReverseIndex 构建反向索引</li>
 * </ul>
 *
 * @param <T> 属性值的类型（如 Double 等）
 * @see ILogicAttribute
 * @see LNumericBindAttribute
 * @see LNumericBindMixAttribute
 * @author coffee
 */
public interface ILogicBindAttribute<T> extends ILogicAttribute<T> {
    // 纯类型标记接口，无新增方法
    // updateBindAttrValue() 继承自 ILogicAttribute，物理源和逻辑源统一复用
    // getBindedAttrs() 继承自 ILogicAttribute，用于 buildReverseIndex 构建索引
}
