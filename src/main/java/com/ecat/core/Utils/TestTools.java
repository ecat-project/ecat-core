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

package com.ecat.core.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.ecat.core.State.AttributeStatus;
import com.ecat.core.Device.DeviceBase;

/**
 * 单元测试反射辅助工具类
 * 
 * @author coffee
 */
public class TestTools {

    /**
     * 设置对象的私有字段值
     */
    public static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 获取对象的私有字段值
     */
    public static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    /**
     * 递归查找类及父类中的字段
     */
    public static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw e;
            }
            return findField(superClass, fieldName);
        }
    }

    /**
     * 反射调用对象的私有方法
     * 适合对象类型的自动识别，不适合short、int等基本类型
     * 更稳定的建议使用 invokePrivateMethodByClass
     */
    public static Object invokePrivateMethod(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof AttributeStatus) {
                parameterTypes[i] = AttributeStatus.class;
            } else {
                parameterTypes[i] = args[i].getClass();
            }
        }
        Method method = findMethod(target.getClass(), methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    /**
     * 反射调用对象的私有方法
     * invokePrivateMethodByClass(
            executorUnderTest,
            "addSettingFuture",
            new Class<?>[]{List.class, String.class, String.class, String.class, String.class},
            new Object[]{futures, deviceId, attributeId, value, unit}
        )
     */
    public static Object invokePrivateMethodByClass(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = findMethod(target.getClass(), methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    /**
     * 反射调用静态私有方法
     * 用于调用类的静态私有方法，target传null
     */
    public static Object invokePrivateStaticMethod(Class<?> targetClass, String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Short) {
                parameterTypes[i] = short.class;
            } else if (args[i] instanceof Integer) {
                parameterTypes[i] = int.class;
            } else if (args[i] instanceof Long) {
                parameterTypes[i] = long.class;
            } else if (args[i] instanceof Double) {
                parameterTypes[i] = double.class;
            } else if (args[i] instanceof Float) {
                parameterTypes[i] = float.class;
            } else if (args[i] instanceof Boolean) {
                parameterTypes[i] = boolean.class;
            } else if (args[i] instanceof String) {
                parameterTypes[i] = String.class;
            } else if (args[i] instanceof AttributeStatus) {
                parameterTypes[i] = AttributeStatus.class;
            } else {
                parameterTypes[i] = args[i].getClass();
            }
        }
        Method method = findMethod(targetClass, methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args); // 静态方法，target传null
    }

    /**
     * 递归查找类及父类中的方法
     */
    public static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        try {
            return clazz.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw e;
            }
            return findMethod(superClass, methodName, parameterTypes);
        }
    }

    /**
     * 验证设备属性的显示名称
     * @param device 设备实例
     * @param attributeId 属性ID
     * @param expectedDisplayName 期望的显示名称
     */
    public static void assertAttributeDisplayName(DeviceBase device, String attributeId, String expectedDisplayName) {
        if (device.getAttrs().get(attributeId) == null) {
            throw new AssertionError("设备应该存在属性: " + attributeId);
        }
        String actualDisplayName = device.getAttrs().get(attributeId).getDisplayName();
        if (!expectedDisplayName.equals(actualDisplayName)) {
            throw new AssertionError("属性显示名称应该匹配: " + attributeId + ", 期望: " + expectedDisplayName + ", 实际: " + actualDisplayName);
        }
    }

    /**
     * 验证设备属性的显示名称（带自定义错误消息）
     * @param device 设备实例
     * @param attributeId 属性ID
     * @param expectedDisplayName 期望的显示名称
     * @param message 自定义错误消息
     */
    public static void assertAttributeDisplayName(DeviceBase device, String attributeId, String expectedDisplayName, String message) {
        if (device.getAttrs().get(attributeId) == null) {
            throw new AssertionError("设备应该存在属性: " + attributeId);
        }
        String actualDisplayName = device.getAttrs().get(attributeId).getDisplayName();
        if (!expectedDisplayName.equals(actualDisplayName)) {
            throw new AssertionError(message + ", 期望: " + expectedDisplayName + ", 实际: " + actualDisplayName);
        }
    }
}
