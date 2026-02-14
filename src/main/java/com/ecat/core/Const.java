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

package com.ecat.core;

/**
 * Constants class containing various constant values used throughout the application.
 * This class provides centralized access to frequently used string constants.
 * 
 * @author coffee
 */
public class Const {

    /**
     * Core module coordinate (groupId:artifactId format)
     * Used as unique identifier for I18n to avoid conflicts between different groupIds
     */
    public static final String CORE_COORDINATE = "com.ecat:ecat-core";

    /**
     * Core module artifactId
     * @deprecated Use {@link #CORE_COORDINATE} instead for I18n purposes
     */
    @Deprecated
    public static final String CORE_ARTIFACT_ID = "ecat-core"; // 与pom.xml中保持一致，为i18n使用

}
