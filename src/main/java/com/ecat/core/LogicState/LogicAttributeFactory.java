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
 * 逻辑属性工厂，统一创建 standalone 逻辑属性实例。
 *
 * <p>通过 {@link ILogicAttribute} 子类的 protected 构造函数（接收 attributeID）
 * 创建模版，再调用 {@link ILogicAttribute#initFromDefinition(LogicAttributeDefine)} 完成初始化。
 *
 * <p>使用示例：
 * <pre>
 *   // genAttrMap 中创建 standalone 属性
 *   attr = LogicAttributeFactory.create(def.getAttrClassType(), def);
 * </pre>
 *
 * <p>新增 L*Attribute 类型的步骤：
 * <ol>
 *   <li>在 L*Attribute 子类中添加 protected 构造函数 {@code protected LXxxAttribute(String attributeID)}</li>
 *   <li>（可选）创建对应的 LogicAttributeDefine 子类携带额外参数</li>
 * </ol>
 * 工厂、genAttrMap、Mapping 文件均不需要修改。
 *
 * @see ILogicAttribute
 * @see LogicAttributeDefine
 * @author coffee
 */
public class LogicAttributeFactory {

    private LogicAttributeFactory() {
        // 工具类，不允许实例化
    }

    /**
     * 创建 standalone 逻辑属性实例。
     *
     * <p>通过反射调用 attrClass 的 protected 构造函数（接收 attributeID）创建实例，
     * 然后调用 {@link ILogicAttribute#initFromDefinition(LogicAttributeDefine)} 完成初始化。
     * attributeID 从 def 中获取，确保 i18n 在构造阶段正确初始化。
     *
     * @param attrClass 逻辑属性类（如 LNumericAttribute.class），
     *                  必须有 protected 构造函数 {@code (String attributeID)}
     * @param def       属性定义，提供初始化元数据（attributeID、attrClass、unit 等）
     * @param <T>       逻辑属性类型
     * @return 已初始化的逻辑属性实例
     * @throws RuntimeException 如果创建或初始化失败
     */
    @SuppressWarnings("unchecked")
    public static <T extends ILogicAttribute<?>> T create(
            Class<? extends ILogicAttribute<?>> attrClass, LogicAttributeDefine def) {
        try {
            ILogicAttribute<?> instance = attrClass
                .getDeclaredConstructor(String.class)
                .newInstance(def.getAttrId());
            instance.initFromDefinition(def);
            return (T) instance;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(attrClass.getSimpleName()
                + " missing protected constructor (String attributeID) required by LogicAttributeFactory", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create " + attrClass.getSimpleName()
                + " from def: " + def.getAttrId(), e);
        }
    }
}
