/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * manifest.json 原子落盘器:tmp 写→原子替换,失败清 tmp(零半写残留)。
 *
 * <p>全静态,勿增实体(不建实例生命周期)。JSON 文件面自含原子写——非 yml 面
 * 不经 YamlAtomicFileWriter(JSON≠YAML 合法分工);原子 move 语义与 T-2-3 契约
 * 同款:5×20ms 重试(ATOMIC_MOVE+REPLACE_EXISTING),文件系统不支持原子 rename
 * →WARN 降级普通 move(能力缺失非暂时态,不重试)。写后不显式 fsync,沿用
 * core 既有文件写路径强度,不升级不降级。</p>
 *
 * <p>写前目标已存在(同 planId 重试)→原子替换覆盖,内容恒同因输入恒同
 * (manifest 一经写入不可变语义:重试=重写同内容后原子替换)。失败时 tmp 残留
 * 清理(deleteIfExists,清理失败仅 log 不抛——tmp 是孤立垃圾,不阻塞主流程)。</p>
 *
 * @author coffee
 */
public final class ManifestFileWriter {

    private static final Log log = LogFactory.getLogger(ManifestFileWriter.class);

    private static final int MOVE_RETRY_TIMES = 5;

    private static final long MOVE_RETRY_INTERVAL_MILLIS = 20L;

    private ManifestFileWriter() {
    }

    /**
     * 序列化(PrettyFormat 人可读+WriteMapNullValue null 键位保留)→父目录创建→
     * tmp 写→原子替换→finally 清 tmp。
     *
     * <p>WriteMapNullValue 与 T-1-3 队列写入器同款:新装坐标 installed_version=null
     * 等语义性 null 键位须落盘保形(T-1-6 降级条目判定字段),两产源键位同形。</p>
     *
     * @throws IOException 序列化外任意 IO 失败(重试穷尽含内);目标保持旧完整文件
     */
    public static void write(UpgradeManifest manifest, Path target) throws IOException {
        byte[] bytes = JSON.toJSONString(manifest, JSONWriter.Feature.PrettyFormat,
                JSONWriter.Feature.WriteMapNullValue).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, bytes);
            moveAtomicallyWithRetry(tmp, target);
        } catch (AtomicMoveNotSupportedException e) {
            log.warn("{} 文件系统不支持原子 rename,降级为非原子替换", target.getFileName().toString());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * 5×20ms 原子 move 重试(ATOMIC_MOVE+REPLACE_EXISTING);
     * AtomicMoveNotSupportedException 原样上抛交调用方降级分支(能力缺失非暂时态)。
     */
    private static void moveAtomicallyWithRetry(Path tmp, Path target) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < MOVE_RETRY_TIMES; attempt++) {
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AtomicMoveNotSupportedException notSupported) {
                throw notSupported;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(MOVE_RETRY_INTERVAL_MILLIS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("manifest 原子替换被中断", ie);
                }
            }
        }
        throw last;
    }
}
