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

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * YAML 文件原子替换写共用件(P2-4):tmp → dump → flush → 回读验证 → 原子 rename(带重试/降级)。
 *
 * <p>写强度=与 integrations.yml 现行实现同级:任意时刻读者打开目标文件看到的要么是旧完整内容、
 * 要么是新完整内容,不存在「已截断、未写完」中间态。明确不做 fsync:目标是并发原子性而非掉电
 * 持久性——掉电最坏情况与既有实现同级(保留旧文件或留下孤儿 tmp),不引入新的写延迟。
 *
 * <p>无内置锁:同文件并发写互斥由调用方自理(写-写并发配锁,见消费方用法)。
 * 失败一律抛 IOException,不吞不兜底;失败时目标文件保持旧完整内容。
 *
 * <p>消费者(本仓两处+跨片):IntegrationManager.updateIntegrationsConfig(integrations.yml)、
 * YmlConfigEntryPersistence.save(entry yml)、片1 升级工件 state.yml/ledger.yml、
 * 片5 消费面(以片5 设计定稿为准)。
 *
 * <p>实体自 IntegrationManager.updateIntegrationsConfig 内联原子写段整段抽出(逐字随迁):
 * dump 配置、fsync 立场注释、重试参数、降级分支、tmp 清理语义均与抽出前一致。
 *
 * @author coffee
 */
public class YamlAtomicFileWriter {

    private static final Log log = LogFactory.getLogger(YamlAtomicFileWriter.class);

    public YamlAtomicFileWriter() {
    }

    /**
     * 原子替换写:doc 序列化为 YAML 后经 tmp+rename 覆盖 targetFile。
     *
     * <p>行为步骤:①父目录不存在则 mkdirs(失败且仍不存在→IOException);②同目录建
     * tmp(createTempFile,前缀带目标名,防同目录并发写者残留互踩);③BLOCK+prettyFlow+
     * indent(2) 写 UTF-8,close 前 flush;④回读验证(tmp 必须能解析回非 null 的 Map);
     * ⑤moveAtomicallyWithRetry 原子替换(ATOMIC_MOVE 不支持→WARN 降级普通 move);
     * ⑥finally 清残留 tmp(删不掉→deleteOnExit 兜底)。
     *
     * <p>失败矩阵:任一步失败抛 IOException,目标文件未被触碰(保持旧完整内容);
     * snakeyaml 内部运行时异常不刻意包装,语义同失败(目标未触碰+tmp 清理)。
     *
     * @param doc        YAML 根级文档树(调用方组装,含 version 戳与否是调用方事务)
     * @param targetFile 目标文件(任意绝对/相对路径,本件不绑定存储根)
     * @throws IOException 步骤①-⑤任一失败;成功出口=目标已原子替换
     */
    public void write(Map<String, Object> doc, File targetFile) throws IOException {
        // 步骤①:父目录(同目录 tmp 是 rename 原子性的前提——跨文件系统 rename 退化为拷贝)
        File parent = targetFile.getParentFile() != null
            ? targetFile.getParentFile() : new File(".");
        if (!parent.exists() && !parent.mkdirs()) {
            // mkdirs false 且目录仍不存在:明确失败,目标文件未被触碰
            throw new IOException("无法创建目标文件父目录: " + parent.getAbsolutePath());
        }

        File tmpFile = null;
        try {
            // 步骤②:同目录 tmp,前缀带目标名+随机后缀(防残留互踩)
            tmpFile = File.createTempFile(targetFile.getName() + ".", ".tmp", parent);

            // 步骤③:dump + flush。close 前 flush(try-with-resources 的 close 亦会 flush);
            // 不做 fsync——旧实现从不 fsync,本修复目标是并发原子性而非掉电持久性,rename 后掉电最坏
            // 情况与旧实现同级(保留旧文件或留下孤儿 tmp),不引入新的写延迟。
            try (FileOutputStream fos = new FileOutputStream(tmpFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
                DumperOptions options = new DumperOptions();
                options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                options.setPrettyFlow(true);
                options.setIndent(2);

                // snakeyaml Yaml 非线程安全:每次写新建实例(随原实现形态)
                Yaml yaml = new Yaml(options);
                yaml.dump(doc, writer);
                writer.flush();
            }

            // 步骤④:回读验证(沿承自早期原子写设计的写前验证语义,backup/rollback 不吸收):
            // dump 成功不等于内容可 round-trip(未来 dump 配置演化可能产出解析不了的输出),
            // 放行一个解析不了的文件到 rename 等于把损坏盖到旧完整文件上。
            requireParseableMap(tmpFile);

            // 步骤⑤:原子替换
            try {
                moveAtomicallyWithRetry(tmpFile, targetFile);
                tmpFile = null; // move 成功,文件已不存在,跳过清理
            } catch (AtomicMoveNotSupportedException e) {
                // 个别文件系统不支持原子 rename(FAT/部分网络盘):退化为普通覆盖 move。
                // 写-写互斥随消费者各自锁状态而定,本件不补锁:IntegrationManager 通道持
                // configFileSync,写者仍互斥,仅对锁外读者的窗口从原子降为「删除+重建」瞬态;
                // YmlConfigEntryPersistence 通道无锁,同 entryId 并发写者此分支不被互斥,
                // 终态=其一完整文件,lost-update 语义与现状等价(并发自理,由调用方声明)。
                // POSIX 生产上本分支不可达(ATOMIC_MOVE 恒支持),仅作告警记录。
                log.warn(targetFile.getName() + " 文件系统不支持原子 rename,降级为非原子替换: " + e.getMessage());
                Files.move(tmpFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
                tmpFile = null;
            }
        } finally {
            // 步骤⑥:清理残留 tmp(成功路径 tmpFile 已置 null;失败路径目标未被触碰,只清 tmp)。
            // 删不掉(Windows 占用等)→deleteOnExit 兜底,JVM 退出清理。
            if (tmpFile != null && tmpFile.exists() && !tmpFile.delete()) {
                tmpFile.deleteOnExit();
            }
        }
    }

    /**
     * 原子替换(tmp → target rename),带 Windows 短暂占用重试。
     *
     * <p>语义自 IntegrationManager.moveAtomicallyWithRetry 整段迁入,逐字对齐:
     * 5 次 × 20ms;ATOMIC_MOVE+REPLACE_EXISTING;AtomicMoveNotSupportedException 原样上抛
     * (能力缺失非暂时态,由 write() 降级分支接);中断→还原中断标记+抛 IOException。
     *
     * <p>protected 非 final 是刻意测试缝(随原 IntegrationManager 包级实例方法缝迁入):测试子类
     * 覆写注入 Windows「删除目标+窗口停顿+重命名」非原子替换形状,复现并锁死静默撕裂缺陷
     * (IntegrationsYmlWindowsReplaceShapeRedTest 消费)。缝从原包级升为 protected:
     * 缝随共用件迁至 Utils 包,而红测试仍在 Integration 测试包,跨包子类化必须 protected——
     * 勿改回包级(红测试将不可覆写),勿改 final/static(同)。非 static 另有一层原因:
     * 覆写注入依赖虚方法分派。
     */
    protected void moveAtomicallyWithRetry(File tmpFile, File targetFile) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.move(tmpFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AtomicMoveNotSupportedException notSupported) {
                throw notSupported; // 能力缺失不是暂时态,交给调用方降级分支
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("YAML 原子替换被中断", ie);
                }
            }
        }
        throw last;
    }

    /**
     * 回读验证:tmp 内容必须能解析为非 null 的 Map 才放行 rename。
     *
     * <p>包级静态纯函数(非 private):负向用例同包直测,免开额外缝。
     *
     * @param tmpFile 待验证的 tmp 文件
     * @throws IOException 解析失败/结果为 null/结果非 Map,消息带原因
     */
    static void requireParseableMap(File tmpFile) throws IOException {
        Yaml yaml = new Yaml();
        Object parsed;
        try (InputStream in = new FileInputStream(tmpFile)) {
            parsed = yaml.load(in);
        } catch (Exception e) {
            throw new IOException("写前回读验证失败: tmp 内容无法解析为 YAML: " + e.getMessage(), e);
        }
        if (parsed == null) {
            throw new IOException("写前回读验证失败: tmp 内容解析结果为 null(空文档)");
        }
        if (!(parsed instanceof Map)) {
            throw new IOException("写前回读验证失败: tmp 内容解析为 "
                + parsed.getClass().getSimpleName() + ",非 Map");
        }
    }
}
