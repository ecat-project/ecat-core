package com.ecat.core.State.Unit;

import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.State.UnitInfo;

/**
 * 国际化单位基础接口，统一处理所有Unit类的国际化逻辑
 *
 * 子类应该实现枚举，并重写 getEnumName() 方法来返回枚举常量名称。
 * 这样可以使用枚举常量名称作为JSON键，避免特殊字符问题，
 * 确保键值稳定和标准化。
 *
 * @author coffee
 */
public interface InternationalizedUnit extends UnitInfo {

    /**
     * 获取单位类别名称（用于JSON键的路径）
     * @return 单位类别名称，如 "temperature", "current" 等
     */
    String getUnitCategory();

    /**
     * 获取枚举常量名称（作为JSON键）
     * @return 枚举常量名称的小写形式
     */
    String getEnumName();

    /**
     * 获取单位显示名称（使用i18n国际化）
     * @return 国际化后的显示名称
     */
    default String getDisplayName() {
        // 使用枚举常量名称作为JSON键，避免特殊字符
        String jsonKey = getEnumName(); // 如 "l_per_second", "milliampere"

        return I18nHelper.t("state.unit." + getUnitCategory() + "." + jsonKey);
    }
}
