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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 升级域文件级公共件:快照哈希对账与目录树删除。
 *
 * <p>应用场景:manifest 逐条 sha256 对账(快照成立/恢复前置)、快照失败清理、
 * 保留期剪除、半快照清理——原随 DbDumpExecutor 搭载,该执行器随 db: 块退役后
 * 这两个与数据库无关的纯文件工具无处安放,收容于此(全包内消费,不外溢)。</p>
 *
 * @author coffee
 */
final class SnapshotFiles {

    private SnapshotFiles() {
    }

    /** 文件 sha256 十六进制(小写);manifest/哈希清单统一出口 */
    static String sha256Hex(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 计算失败: " + file + " - " + e.getMessage(), e);
        }
    }

    /** 目录树删除(根不存在=无操作);删除中任一失败上抛,调用方决定显形或记日志 */
    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walked = Files.walk(root)) {
            for (Path path : walked.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                Files.delete(path);
            }
        }
    }
}
