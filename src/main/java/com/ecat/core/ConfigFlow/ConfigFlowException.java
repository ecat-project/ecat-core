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

package com.ecat.core.ConfigFlow;

/**
 * 配置流程异常
 * <p>flow 编排能力下沉到 ecat-core（2026-06-22）：flow 推进/管理（ConfigFlowService）由 core 提供，
 * api（REST 适配）与同进程第三方使用集成都调 core 的 flow 能力，不再经 ecat-core-api。
 *
 * @author coffee
 */
public class ConfigFlowException extends RuntimeException {

    public ConfigFlowException(String message) {
        super(message);
    }

    public ConfigFlowException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigFlowException(Throwable cause) {
        super(cause);
    }
}
