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

import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import lombok.Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * DB dump/restore 执行器:按 (engine, dumpPolicy) 分派,四件套快照的 ④号件执行面。
 *
 * <p>分派矩阵:table-family:<逗号清单> 且 engine=pg → pg_dump 子进程(custom 格式,
 * 逐清单项 --table,flyway 机制自动附加 history 表——history 表属声明域足迹);
 * file-copy 且 engine=sqlite → 存储目录整拷(含 -wal/-shm 伴生文件);full/schema
 * 为预留值——出现即 manifest 异常,显式拒绝不静默(严格模式)。</p>
 *
 * <p>连接参数:POSTGRES_HOST/PORT/DB/USER/PASSWORD 取自进程环境(与 ruoyi-admin
 * 数据源同源注入,零新凭据面),映射为子进程环境 PGHOST/PGPORT/PGDATABASE/PGUSER/
 * PGPASSWORD;任一缺失 → DbDumpException 列出缺失变量名——不猜默认值,凭据字面值
 * 不入代码/日志/命令行。</p>
 *
 * <p>TimescaleDB 义务:含 hypertable 的域 restore 前后须 timescaledb_pre_restore()/
 * post_restore() 会话包装(官方 dump 指引)。分派信号=目标库 pg_extension 实际装载
 * timescaledb(psql 探测)——core 侧不内嵌任何集成的表名单;未装载的普通 PG 库不受
 * 包装影响。pre_restore 成功后 pg_restore 无论成败都执行 post_restore(官方指引:
 * 中止残留 pre_restore 态会使库不可用)。</p>
 *
 * <p>命令缝: {@link Runner} 构造注入(默认 ProcessBuilder 真执行;单测注入桩——
 * 禁真实 pg_dump 进单测,环境不可假设;真库 dump/restore 验证归运行时实测)。
 * 环境读取缝: {@code envLookup}(默认 System.getenv;单测注入受控映射,使
 * 「POSTGRES_* 缺失」可确定性构造)。</p>
 *
 * @author coffee
 */
public class DbDumpExecutor {

    private static final Log log = LogFactory.getLogger(DbDumpExecutor.class);

    /** dump 产物单文件哈希清单名(table-family 侧,与 dump.pgdump 同目录) */
    static final String DUMP_HASH_FILE = "dump.sha256";
    /** file-copy 产物清单名(位于 outDir 根,逐行 "<sha256>  <相对路径>") */
    static final String FILE_COPY_HASH_MANIFEST = "files.sha256";

    private static final String[] REQUIRED_PG_ENV_KEYS = {
        "POSTGRES_HOST", "POSTGRES_PORT", "POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD"
    };

    /** 命令执行缝:command=完整命令行;pgEnv=附加到子进程环境的连接参数映射(可为空) */
    public interface Runner {
        ExecResult run(List<String> command, Map<String, String> pgEnv) throws IOException;
    }

    /** 执行结果:退出码+合并输出尾部(诊断用;错误消息只引尾部,不引全量) */
    @Value
    public static class ExecResult {
        int exitCode;
        String outputTail;
    }

    private final Runner commandRunner;
    private final Function<String, String> envLookup;
    /** file-copy 源根:.ecat-data/storage(存储目录={root}/{groupId}/{artifactId}) */
    private final Path storageRoot;

    /** 生产装配:ProcessBuilder 真执行 + 进程环境读取 */
    public DbDumpExecutor() {
        this(new ProcessBuilderRunner(), System::getenv, Paths.get(".ecat-data", "storage"));
    }

    /** 全参构造:命令/环境/存储根三缝可注入(单测确定性) */
    public DbDumpExecutor(Runner commandRunner, Function<String, String> envLookup, Path storageRoot) {
        this.commandRunner = commandRunner;
        this.envLookup = envLookup;
        this.storageRoot = storageRoot;
    }

    /**
     * 部署前置预检:pg_dump 不在 PATH → DbDumpException。
     * 升级编排器在变更窗第一步前调用(无 pg_dump 不进变更,快照能力是前提)。
     */
    public void verifyPgDumpAvailable() {
        ExecResult result;
        try {
            result = commandRunner.run(Arrays.asList("pg_dump", "--version"), null);
        } catch (IOException e) {
            throw new DbDumpException("部署前置缺失: pg_dump 不在 PATH(" + e.getMessage() + ")", e);
        }
        if (result.getExitCode() != 0) {
            throw new DbDumpException("部署前置缺失: pg_dump 不可用, 退出码 " + result.getExitCode()
                    + "——" + tailOf(result));
        }
    }

    /**
     * dump 一域到 outDir。
     *
     * <p>产物:table-family → {outDir}/dump.pgdump + dump.sha256;file-copy →
     * {outDir}/files/(逐文件)+ files.sha256 清单。任一失败抛 DbDumpException
     * (调用方=BackupService 负责已写内容清理)。</p>
     */
    public void dump(String domain, DbDumpSpec block, Path outDir) throws IOException {
        String policy = block.getDumpPolicy();
        if (policy == null) {
            throw new DbDumpException("db 约定缺 dumpPolicy(域 " + domain + "): manifest 异常");
        }
        if (policy.startsWith("table-family:")) {
            if (!"pg".equals(block.getEngine())) {
                throw new DbDumpException("table-family 策略要求 engine=pg(域 " + domain
                        + ", 实际 " + block.getEngine() + "): manifest 异常");
            }
            dumpTableFamily(domain, block, outDir);
            return;
        }
        if ("file-copy".equals(policy)) {
            if (!"sqlite".equals(block.getEngine())) {
                throw new DbDumpException("file-copy 策略要求 engine=sqlite(域 " + domain
                        + ", 实际 " + block.getEngine() + "): manifest 异常");
            }
            dumpFileCopy(domain, block, outDir);
            return;
        }
        throw new DbDumpException("dumpPolicy 预留策略,当前无域使用,出现即 manifest 异常(域 "
                + domain + "): " + policy);
    }

    /**
     * restore 一域(从 dumpDir 的产物)。
     *
     * <p>table-family → pg_restore --clean --if-exists --no-owner(连接走 PG* 环境,
     * 归档内仅域表族+history 表,--clean 限定归档范围);file-copy → 目录整还原
     * (现有存储目录替换,还原前逐文件校验 files.sha256)。</p>
     */
    public void restore(String domain, DbDumpSpec block, Path dumpDir) throws IOException {
        String policy = block.getDumpPolicy();
        if (policy != null && policy.startsWith("table-family:")) {
            restoreTableFamily(domain, block, dumpDir);
            return;
        }
        if ("file-copy".equals(policy)) {
            restoreFileCopy(domain, block, dumpDir);
            return;
        }
        throw new DbDumpException("restore 遇到不可分派策略(域 " + domain + "): " + policy);
    }

    // ==================== table-family(pg_dump / pg_restore) ====================

    private void dumpTableFamily(String domain, DbDumpSpec block, Path outDir) throws IOException {
        Map<String, String> pgEnv = requirePgEnv(domain);
        Path product = outDir.resolve("dump.pgdump");
        List<String> command = new ArrayList<>();
        command.add("pg_dump");
        command.add("--format=custom");
        command.add("--file=" + product);
        for (String table : tableFamilyOf(domain, block)) {
            command.add("--table=" + table);
        }
        // history 表属声明域足迹(迁移机制契约):清单不含、执行器附加
        if ("flyway".equals(block.getMechanism()) && block.getHistoryTable() != null
                && !block.getHistoryTable().isEmpty()) {
            command.add("--table=" + block.getHistoryTable());
        }
        ExecResult result = commandRunner.run(command, pgEnv);
        if (result.getExitCode() != 0) {
            throw new DbDumpException("pg_dump 失败(域 " + domain + ", 退出码 " + result.getExitCode()
                    + "): " + tailOf(result));
        }
        if (!Files.isRegularFile(product) || Files.size(product) == 0) {
            throw new DbDumpException("pg_dump 产物为空(域 " + domain + "): " + product);
        }
        Files.write(outDir.resolve(DUMP_HASH_FILE),
                sha256Hex(product).getBytes(StandardCharsets.UTF_8));
    }

    private void restoreTableFamily(String domain, DbDumpSpec block, Path dumpDir) throws IOException {
        Map<String, String> pgEnv = requirePgEnv(domain);
        Path product = dumpDir.resolve("dump.pgdump");
        if (!Files.isRegularFile(product)) {
            throw new DbDumpException("无 dump 产物可恢复(域 " + domain + "): " + product);
        }
        boolean timescale = detectTimescaleDb(pgEnv);
        List<String> restoreCommand = Arrays.asList(
                "pg_restore", "--clean", "--if-exists", "--no-owner", product.toString());
        if (!timescale) {
            runOrThrow(restoreCommand, pgEnv, "pg_restore 失败(域 " + domain + ")");
            return;
        }
        // TimescaleDB 官方指引包装:pre_restore → pg_restore → post_restore(成败都跑 post)
        runOrThrow(Arrays.asList("psql", "-v", "ON_ERROR_STOP=1",
                "-c", "SELECT timescaledb_pre_restore();"), pgEnv,
                "timescaledb_pre_restore 失败(域 " + domain + ")");
        try {
            runOrThrow(restoreCommand, pgEnv, "pg_restore 失败(域 " + domain + ")");
        } finally {
            runOrThrow(Arrays.asList("psql", "-v", "ON_ERROR_STOP=1",
                    "-c", "SELECT timescaledb_post_restore();"), pgEnv,
                    "timescaledb_post_restore 失败(域 " + domain + ")");
        }
    }

    /** hypertable 装载探测:pg_extension 存在 timescaledb → true(探测失败=按未装载,restore 走普通路径) */
    private boolean detectTimescaleDb(Map<String, String> pgEnv) throws IOException {
        ExecResult result = commandRunner.run(Arrays.asList("psql", "-At", "-c",
                "SELECT 1 FROM pg_extension WHERE extname='timescaledb'"), pgEnv);
        return result.getExitCode() == 0 && "1".equals(result.getOutputTail().trim());
    }

    private List<String> tableFamilyOf(String domain, DbDumpSpec block) {
        String list = block.getDumpPolicy().substring("table-family:".length());
        if (list.isEmpty()) {
            throw new DbDumpException("table-family 清单为空(域 " + domain + "): manifest 异常");
        }
        return Arrays.asList(list.split(",", -1));
    }

    // ==================== file-copy(sqlite 目录整拷) ====================

    private void dumpFileCopy(String domain, DbDumpSpec block, Path outDir) throws IOException {
        Path sourceDir = storageRoot.resolve(block.getGroupId()).resolve(block.getArtifactId());
        if (!Files.isDirectory(sourceDir)) {
            throw new DbDumpException("存储目录缺失,无库可拷(域 " + domain + "): " + sourceDir);
        }
        Path filesDir = outDir.resolve("files");
        Files.createDirectories(filesDir);
        StringBuilder manifest = new StringBuilder();
        try (Stream<Path> walked = Files.walk(sourceDir)) {
            for (Path file : walked.filter(Files::isRegularFile).sorted().toArray(Path[]::new)) {
                String relative = sourceDir.relativize(file).toString().replace('\\', '/');
                Files.copy(file, filesDir.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                manifest.append(sha256Hex(file)).append("  ").append(relative).append('\n');
            }
        }
        if (manifest.length() == 0) {
            throw new DbDumpException("存储目录为空,无库可拷(域 " + domain + "): " + sourceDir);
        }
        Files.write(outDir.resolve(FILE_COPY_HASH_MANIFEST),
                manifest.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void restoreFileCopy(String domain, DbDumpSpec block, Path dumpDir) throws IOException {
        Path filesDir = dumpDir.resolve("files");
        Path manifestFile = dumpDir.resolve(FILE_COPY_HASH_MANIFEST);
        if (!Files.isDirectory(filesDir) || !Files.isRegularFile(manifestFile)) {
            throw new DbDumpException("无 file-copy 产物可恢复(域 " + domain + "): " + dumpDir);
        }
        Path targetDir = storageRoot.resolve(block.getGroupId()).resolve(block.getArtifactId());
        // 还原前逐文件校验清单(坏产物不落生产存储面)
        List<String[]> entries = new ArrayList<>();
        for (String line : new String(Files.readAllBytes(manifestFile), StandardCharsets.UTF_8).split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            int split = line.indexOf("  ");
            if (split <= 0) {
                throw new DbDumpException("file-copy 清单行非法(域 " + domain + "): " + line);
            }
            String expected = line.substring(0, split);
            String relative = line.substring(split + 2);
            Path snapshotFile = filesDir.resolve(relative);
            if (!Files.isRegularFile(snapshotFile)
                    || !expected.equals(sha256Hex(snapshotFile))) {
                throw new DbDumpException("file-copy 产物校验失败(域 " + domain + "): " + relative);
            }
            entries.add(new String[]{expected, relative});
        }
        if (entries.isEmpty()) {
            throw new DbDumpException("file-copy 清单为空(域 " + domain + ")");
        }
        // 目录整还原:现有存储目录替换
        deleteRecursively(targetDir);
        Files.createDirectories(targetDir);
        for (String[] entry : entries) {
            Files.copy(filesDir.resolve(entry[1]), targetDir.resolve(entry[1]),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ==================== 环境 / 执行基础设施 ====================

    /** POSTGRES_* 五变量 → PG* 映射;任一缺失/空白 → DbDumpException 列出缺失变量名 */
    private Map<String, String> requirePgEnv(String domain) {
        Map<String, String> pgEnv = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_PG_ENV_KEYS) {
            String value = envLookup.apply(key);
            if (value == null || value.trim().isEmpty()) {
                missing.add(key);
                continue;
            }
            pgEnv.put(pgKeyOf(key), value);
        }
        if (!missing.isEmpty()) {
            throw new DbDumpException("部署配置缺失环境变量(域 " + domain + "): "
                    + String.join(", ", missing) + "——不猜默认连接");
        }
        return pgEnv;
    }

    private String pgKeyOf(String postgresKey) {
        switch (postgresKey) {
            case "POSTGRES_HOST": return "PGHOST";
            case "POSTGRES_PORT": return "PGPORT";
            case "POSTGRES_DB": return "PGDATABASE";
            case "POSTGRES_USER": return "PGUSER";
            case "POSTGRES_PASSWORD": return "PGPASSWORD";
            default: throw new IllegalArgumentException("未知 POSTGRES 环境键: " + postgresKey);
        }
    }

    private void runOrThrow(List<String> command, Map<String, String> pgEnv, String what) throws IOException {
        ExecResult result = commandRunner.run(command, pgEnv);
        if (result.getExitCode() != 0) {
            throw new DbDumpException(what + ", 退出码 " + result.getExitCode() + ": " + tailOf(result));
        }
    }

    private String tailOf(ExecResult result) {
        return result.getOutputTail() == null ? "" : result.getOutputTail().trim();
    }

    /** 文件 sha256 十六进制(小写);manifest/哈希清单统一出口 */
    public static String sha256Hex(Path file) {
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

    /** 默认命令执行:ProcessBuilder+合并输出(限尾 8KB,防巨量 stderr 撑爆错误消息) */
    static class ProcessBuilderRunner implements Runner {
        @Override
        public ExecResult run(List<String> command, Map<String, String> pgEnv) throws IOException {
            // 命令行日志安全:本执行器命令行不含凭据(连接参数一律走 PG* 环境)
            log.info("执行: " + String.join(" ", command));
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            if (pgEnv != null) {
                builder.environment().putAll(pgEnv);
            }
            Process process = builder.start();
            String tail;
            try (InputStream in = process.getInputStream()) {
                tail = drainTail(in);
            }
            int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("命令执行被中断: " + command.get(0), e);
            }
            return new ExecResult(exitCode, tail);
        }

        /** 限尾读合并输出:仅保留末 8KB(防巨量 stderr 撑爆错误消息),诊断够用 */
        private String drainTail(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            byte[] bytes = buffer.toByteArray();
            int from = Math.max(0, bytes.length - 8192);
            return new String(bytes, from, bytes.length - from, StandardCharsets.UTF_8);
        }
    }
}
