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

package com.ecat.core.Utils.Mdc;

import org.slf4j.MDC;

/**
 * MDC 坐标转换器工具
 *
 * <p>提供便捷的 MDC 坐标读写方法。
 * 
 * @author coffee
 */
public final class MdcCoordinateConverter {
    public static final String COORDINATE_KEY = "integration.coordinate";

    private MdcCoordinateConverter() {
    }

    /**
     * 设置集成坐标
     *
     * @param coordinate 坐标
     */
    public static void setCoordinate(String coordinate) {
        if (coordinate != null && !coordinate.isEmpty()) {
            MDC.put(COORDINATE_KEY, coordinate);
        }
    }

    /**
     * 获取当前集成坐标
     *
     * @return 坐标
     */
    public static String getCoordinate() {
        return MDC.get(COORDINATE_KEY);
    }

    /**
     * 清除集成坐标
     */
    public static void clearCoordinate() {
        MDC.remove(COORDINATE_KEY);
    }
}
