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

package com.ecat.core.Upgrade;

/**
 * DB dump/restore 失败明确异常。
 *
 * <p>调用方=BackupService(快照/恢复)→ 升级编排器失败路径与状态机:编排器须以
 * 非受检异常穿 B 窗流程,故为 RuntimeException。消息始终携带 domain 定位与可处置
 * 原因链(命令 stderr 尾部/缺失环境变量名),不吞不兜底、不猜默认连接。</p>
 *
 * @author coffee
 */
public class DbDumpException extends RuntimeException {

    public DbDumpException(String message) {
        super(message);
    }

    public DbDumpException(String message, Throwable cause) {
        super(message, cause);
    }
}
