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

package com.ecat.core.Integration.IntegrationSubInfo;

/**
 * The {@code WebPlatformSupport} class represents the support status for Web UI and Web API
 * in a platform. It provides options to check whether the platform supports these features.
 * 
 * <p>This class includes:
 * <ul>
 *   <li>A no-argument constructor that initializes the support flags to {@code false}.</li>
 *   <li>A parameterized constructor to explicitly set the support flags.</li>
 *   <li>Getter methods to check the support status for Web UI and Web API.</li>
 * </ul>
 * </p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * WebPlatformSupport support = new WebPlatformSupport(true, false);
 * System.out.println(support); // Output: {ui=true, api=false}
 * }
 * </pre>
 * 
 * @author coffee
 */
public class WebPlatformSupport {

    private boolean ui; // 是否支持Web UI
    private boolean api; // 是否支持Web API
    
    // 无参构造函数 - 使用默认值
    public WebPlatformSupport() {
        this.ui = false;
        this.api = false;
    }
    
    // 有参构造函数 - 显式设置值
    public WebPlatformSupport(boolean ui, boolean api) {
        this.ui = ui;
        this.api = api;
    }
    
    @Override
    public String toString() {
        return "{ui=" + ui + ", api=" + api + "}";
    }

    public boolean hasUi() {
        return ui;
    }
    public boolean hasApi() {
        return api;
    }
}