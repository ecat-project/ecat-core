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

import java.net.URLClassLoader;

import com.ecat.core.Integration.IntegrationBase;

import lombok.Getter;

/**
 * LoadJarResult is a class that encapsulates the result of loading a jar file.
 * It contains the loaded integration instance and the custom class loader used for loading.
 * @author coffee
 */
public class LoadJarResult {
    @Getter
    private IntegrationBase integration;
    @Getter
    private URLClassLoader classLoader;

    public LoadJarResult(IntegrationBase integration, URLClassLoader classLoader) {
        this.integration = integration;
        this.classLoader = classLoader;
    }
}
