package com.ecat.core.Utils.DynamicConfig.Validator;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Supplier;

/**
 * 动态字符串字典验证器
 */
public class DynamicStringDictValidator implements ConstraintValidator<String> {

    private final Supplier<Map<String, String>> dictSupplier;
    private final boolean caseSensitive;
    private Map<String, String> cachedDict;
    private long lastUpdateTime = 0;

    /**
     * 构造函数
     * @param dictSupplier 字典提供者，key 为有效值，value 为显示名称
     */
    public DynamicStringDictValidator(Supplier<Map<String, String>> dictSupplier) {
        this(dictSupplier, true);
    }

    /**
     * 构造函数
     * @param dictSupplier 字典提供者，key 为有效值，value 为显示名称
     * @param caseSensitive 是否区分大小写
     */
    public DynamicStringDictValidator(Supplier<Map<String, String>> dictSupplier, boolean caseSensitive) {
        if (dictSupplier == null) {
            throw new IllegalArgumentException("dictSupplier cannot be null");
        }
        this.dictSupplier = dictSupplier;
        this.caseSensitive = caseSensitive;
    }

    @Override
    public boolean validate(String value) {
        if (value == null) {
            return false;
        }

        Map<String, String> currentDict = getCurrentDict();
        if (currentDict == null || currentDict.isEmpty()) {
            return false;
        }

        if (caseSensitive) {
            return currentDict.containsKey(value);
        } else {
            for (String key : currentDict.keySet()) {
                if (key.equalsIgnoreCase(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public String getErrorMessage() {
        Map<String, String> currentDict = getCurrentDict();
        if (currentDict == null || currentDict.isEmpty()) {
            return "没有可用的选项";
        }
        return "字符串值必须是 " + currentDict.keySet() + " 中的一个";
    }

    /**
     * 获取当前字典（带缓存）
     * @return 当前字典
     */
    private Map<String, String> getCurrentDict() {
        long currentTime = System.currentTimeMillis();
        // 缓存1秒，避免频繁调用
        if (cachedDict == null || currentTime - lastUpdateTime > 1000) {
            cachedDict = dictSupplier.get();
            lastUpdateTime = currentTime;
        }
        return cachedDict;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        cachedDict = null;
        lastUpdateTime = 0;
    }

    /**
     * 获取有效字典（只读）
     * @return 有效字典
     */
    public Map<String, String> getValidDict() {
        Map<String, String> currentDict = getCurrentDict();
        return currentDict != null ? new HashMap<>(currentDict) : new HashMap<>();
    }

    /**
     * 查找匹配的键（支持大小写不敏感）
     * @param value 输入值
     * @return 匹配的键，如果找不到则返回 null
     */
    public String findMatchingKey(String value) {
        if (value == null) {
            return null;
        }

        Map<String, String> currentDict = getCurrentDict();
        if (currentDict == null || currentDict.isEmpty()) {
            return null;
        }

        if (caseSensitive) {
            return currentDict.containsKey(value) ? value : null;
        } else {
            for (String key : currentDict.keySet()) {
                if (key.equalsIgnoreCase(value)) {
                    return key;
                }
            }
            return null;
        }
    }

    /**
     * 获取是否区分大小写
     * @return 是否区分大小写
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * 获取字典大小
     * @return 字典大小
     */
    public int getDictSize() {
        Map<String, String> currentDict = getCurrentDict();
        return currentDict != null ? currentDict.size() : 0;
    }
}