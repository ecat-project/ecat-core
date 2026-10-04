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

/**
 * 夹具 jar 的最小入口类（顶层测试类，跨测试复用的 .class 来源：boot 失败隔离与
 * requires_core 门测试共用）。
 *
 * <p><b>必须是顶层类，不可改为静态嵌套类</b>：scanIntegrationEntryClass 跳过含 {@code $}
 * 的条目，嵌套类编译产物 {@code Outer$GateStubIntegration.class} 会被扫描器无视导致
 * NO_ENTRY_CLASS 误入；顶层类编译为独立 .class（target/test-classes 下），经资源流拷入
 * 夹具 jar 后恰命中 1 个。夹具 jar 全程不被真实实例化（扫描只 loadClass 验证继承关系）。
 */
public class GateStubIntegration extends IntegrationBase {

    @Override
    public void onInit() {
        // 夹具桩：不参与任何真实生命周期
    }

    @Override
    public void onStart() {
        // 夹具桩：不参与任何真实生命周期
    }

    @Override
    public void onPause() {
        // 夹具桩：不参与任何真实生命周期
    }
}
