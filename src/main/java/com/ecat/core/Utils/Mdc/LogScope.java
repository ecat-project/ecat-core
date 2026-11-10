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
 * 受害者身份 MDC scope：把身份键（entry.id / device.id）临时写入当前线程 MDC，
 * try-with-resources 关闭时恢复外层旧值——外层已设同名键则写回旧值（非删），外层缺席才移除，
 * 嵌套 scope 因此具备分层语义（内层关闭后外层身份仍在场）。
 *
 * <p><b>线程本地性</b>：MDC 是 ThreadLocal，本 scope 只影响当前线程；跨线程传递须由调用方经
 * {@link MdcContext#capture()} 完成，本类不代劳。
 *
 * <p><b>泄漏防护</b>：必须 try-with-resources 使用。复用线程（ConfigFlow worker / 轮询链线程）
 * 上裸 put 不还，身份键会污染同线程后续无关日志的身份归属。
 *
 * <p><b>双消费者</b>：写入的身份键被两处消费——{@code ErrorRateLimitFilter} 限频签名
 * （同模板异受害者分窗，汇总行能说出「是谁的」）与 CommTrace 帧归属（CommTraceBuffer 反查
 * MDC 身份键）。键常量统一取自 {@link MdcContext} 键族，防键字符串漂移。
 * 
 * @author coffee
 */
public final class LogScope implements AutoCloseable {

    private final String key;
    private final String previousValue;

    private LogScope(String key, String value) {
        this.key = key;
        this.previousValue = MDC.get(key);
        MDC.put(key, value);
    }

    /** entry 身份 scope：ConfigEntry 通知/装载链路的受害者是 entry（entry.id） */
    public static LogScope entry(String entryId) {
        return new LogScope(MdcContext.ENTRY_ID_KEY, entryId);
    }

    /** device 身份 scope：设备链路的受害者是设备（device.id） */
    public static LogScope device(String deviceId) {
        return new LogScope(MdcContext.DEVICE_ID_KEY, deviceId);
    }

    @Override
    public void close() {
        if (previousValue != null) {
            MDC.put(key, previousValue);
        } else {
            MDC.remove(key);
        }
    }
}
