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

package com.ecat.core.Integration;

import java.net.URLClassLoader;

import lombok.Getter;
import lombok.Setter;


/**
 * IntegrationLoadOption encapsulates the options for loading an integration.
 * It includes the class loader used for loading the integration and a child class loader.
 *
 * @author coffee
 */
public class IntegrationLoadOption {

    @Getter
    private URLClassLoader classLoader;

    @Getter
    @Setter
    private URLClassLoader childClassLoader;

    @Getter
    @Setter
    private IntegrationInfo integrationInfo;

    public IntegrationLoadOption(URLClassLoader classLoader) {
        this.classLoader = classLoader;
    }

}
