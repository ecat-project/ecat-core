package com.ecat.core.Utils.DynamicConfig;

public interface ConstraintValidator<T> {
    boolean validate(T value);
    String getErrorMessage();
}
