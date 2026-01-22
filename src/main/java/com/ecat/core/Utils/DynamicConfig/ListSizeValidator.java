package com.ecat.core.Utils.DynamicConfig;

import java.util.List;

public class ListSizeValidator<T> implements ConstraintValidator<List<T>> {
    private final int minSize;
    private final int maxSize;

    public ListSizeValidator(int minSize, int maxSize) {
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    @Override
    public boolean validate(List<T> value) {
        if (value == null) {
            return true;
        }
        int size = value.size();
        return size >= minSize && size <= maxSize;
    }

    @Override
    public String getErrorMessage() {
        return "列表大小必须在 " + minSize + " 到 " + maxSize + " 之间";
    }

    public boolean isSingleSelect() {
        return minSize == 1 && maxSize == 1;
    }
}
