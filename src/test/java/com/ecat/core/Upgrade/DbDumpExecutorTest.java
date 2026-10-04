/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import com.ecat.core.Upgrade.DbDumpExecutor.ExecResult;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * DbDumpExecutor 分派矩阵单测(命令缝注入桩,零真实 pg_dump/psql)。
 *
 * <p>锁定风险:预留策略显式拒绝(不静默跳过)、POSTGRES_* 缺失列出变量名(不猜默认
 * 连接)、命令形状(table-family 逐项 --table/flyway 附加 history 表/PG* 环境映射)、
 * file-copy 整拷与清单校验、TimescaleDB pre/post 包装序。</p>
 *
 * @author coffee
 */
public class DbDumpExecutorTest {

    private Path tempDir;
    private Path storageRoot;
    /** 桩执行记录:每次 run 的 [命令行, 环境映射] */
    private final List<String[]> commands = new ArrayList<>();
    private final List<Map<String, String>> envs = new ArrayList<>();
    /** 桩脚本:按命令首词决定行为(退出码/是否造产物) */
    private final Map<String, Behavior> behaviorByCommand = new HashMap<>();

    private interface Behavior {
        DbDumpExecutor.ExecResult run(List<String> command, Map<String, String> pgEnv) throws IOException;
    }

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("dbdump-test");
        storageRoot = tempDir.resolve("storage");
        behaviorByCommand.clear();
        commands.clear();
        envs.clear();
    }

    private DbDumpExecutor executor(Map<String, String> env) {
        return new DbDumpExecutor((command, pgEnv) -> {
            commands.add(command.toArray(new String[0]));
            envs.add(pgEnv == null ? null : new LinkedHashMap<>(pgEnv));
            Behavior behavior = behaviorByCommand.get(command.get(0));
            if (behavior != null) {
                return behavior.run(command, pgEnv);
            }
            return new ExecResult(0, "");
        }, env::get, storageRoot);
    }

    private Map<String, String> fullPgEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("POSTGRES_HOST", "dbhost");
        env.put("POSTGRES_PORT", "5432");
        env.put("POSTGRES_DB", "ecatdb");
        env.put("POSTGRES_USER", "ecat");
        env.put("POSTGRES_PASSWORD", "secret");
        return env;
    }

    private DbDumpSpec tableFamilySpec(String... tables) {
        StringBuilder policy = new StringBuilder("table-family:");
        for (int i = 0; i < tables.length; i++) {
            if (i > 0) {
                policy.append(',');
            }
            policy.append(tables[i]);
        }
        return DbDumpSpec.builder().domain("his").engine("pg").mechanism("flyway")
                .historyTable("flyway_schema_history").dumpPolicy(policy.toString())
                .groupId("com.ecat").artifactId("integration-his").build();
    }

    private DbDumpSpec fileCopySpec() {
        return DbDumpSpec.builder().domain("media").engine("sqlite").mechanism("legacy")
                .dumpPolicy("file-copy").groupId("com.ecat").artifactId("integration-media").build();
    }

    // ==================== T6 负向:预留策略/非法值/环境缺失 ====================

    @Test
    public void t6_full_policy_rejected() throws IOException {
        DbDumpSpec spec = DbDumpSpec.builder().domain("x").engine("pg").dumpPolicy("full").build();
        try {
            executor(fullPgEnv()).dump("x", spec, tempDir.resolve("out"));
            fail("full 预留策略必须显式拒绝");
        } catch (DbDumpException e) {
            assertTrue("消息含策略名: " + e.getMessage(), e.getMessage().contains("full"));
        }
    }

    @Test
    public void t6_schema_policy_rejected() throws IOException {
        DbDumpSpec spec = DbDumpSpec.builder().domain("x").engine("pg").dumpPolicy("schema").build();
        try {
            executor(fullPgEnv()).dump("x", spec, tempDir.resolve("out"));
            fail("schema 预留策略必须显式拒绝");
        } catch (DbDumpException e) {
            assertTrue(e.getMessage().contains("schema"));
        }
    }

    @Test
    public void t6_unknown_prefix_rejected() throws IOException {
        DbDumpSpec spec = DbDumpSpec.builder().domain("x").engine("pg").dumpPolicy("bogus:x").build();
        try {
            executor(fullPgEnv()).dump("x", spec, tempDir.resolve("out"));
            fail("未知策略必须显式拒绝");
        } catch (DbDumpException e) {
            assertTrue(e.getMessage().contains("bogus:x"));
        }
    }

    @Test
    public void t6_missing_postgres_env_listed_not_guessed() throws IOException {
        Map<String, String> partial = new HashMap<>(fullPgEnv());
        partial.remove("POSTGRES_HOST");
        partial.remove("POSTGRES_PASSWORD");
        try {
            executor(partial).dump("his", tableFamilySpec("t1"), tempDir.resolve("out"));
            fail("环境缺失必须拒绝");
        } catch (DbDumpException e) {
            assertTrue("列出缺失变量名: " + e.getMessage(), e.getMessage().contains("POSTGRES_HOST"));
            assertTrue(e.getMessage().contains("POSTGRES_PASSWORD"));
            assertFalse("不猜默认连接", e.getMessage().contains("localhost"));
        }
        assertTrue("拒绝在先,零命令执行", commands.isEmpty());
    }

    @Test
    public void t6_table_family_requires_pg_engine() throws IOException {
        DbDumpSpec spec = DbDumpSpec.builder().domain("x").engine("sqlite")
                .dumpPolicy("table-family:t1").build();
        try {
            executor(fullPgEnv()).dump("x", spec, tempDir.resolve("out"));
            fail("engine 错配必须显形");
        } catch (DbDumpException e) {
            assertTrue(e.getMessage().contains("manifest 异常"));
        }
    }

    // ==================== dump 命令形状与环境映射 ====================

    @Test
    public void dump_table_family_command_shape_and_env_mapping() throws IOException {
        Path outDir = tempDir.resolve("dump-out");
        Files.createDirectories(outDir);
        // 桩:pg_dump 成功并落产物(执行器校验产物存在+非空)
        behaviorByCommand.put("pg_dump", (command, pgEnv) -> {
            for (String arg : command) {
                if (arg.startsWith("--file=")) {
                    Files.write(Paths.get(arg.substring("--file=".length())),
                            "PGDMP".getBytes(StandardCharsets.UTF_8));
                }
            }
            return new ExecResult(0, "");
        });
        executor(fullPgEnv()).dump("his", tableFamilySpec("his_data", "realdata"), outDir);

        String[] command = commands.get(0);
        assertEquals("pg_dump", command[0]);
        List<String> args = Arrays.asList(command);
        assertTrue(args.contains("--format=custom"));
        assertTrue("逐清单项 --table", args.contains("--table=his_data"));
        assertTrue(args.contains("--table=realdata"));
        assertTrue("flyway 机制自动附加 history 表", args.contains("--table=flyway_schema_history"));
        Map<String, String> pgEnv = envs.get(0);
        assertEquals("POSTGRES_HOST→PGHOST", "dbhost", pgEnv.get("PGHOST"));
        assertEquals("POSTGRES_DB→PGDATABASE", "ecatdb", pgEnv.get("PGDATABASE"));
        assertEquals("POSTGRES_USER→PGUSER", "ecat", pgEnv.get("PGUSER"));
        assertEquals("POSTGRES_PORT→PGPORT", "5432", pgEnv.get("PGPORT"));
        assertEquals("POSTGRES_PASSWORD→PGPASSWORD", "secret", pgEnv.get("PGPASSWORD"));
        assertEquals("环境键恰好五个", 5, pgEnv.size());
        // 产物哈希清单落盘且值正确
        String recorded = new String(Files.readAllBytes(outDir.resolve("dump.sha256")),
                StandardCharsets.UTF_8);
        assertEquals(DbDumpExecutor.sha256Hex(outDir.resolve("dump.pgdump")), recorded);
    }

    @Test
    public void dump_exit_nonzero_raises_with_output_tail() throws IOException {
        behaviorByCommand.put("pg_dump", (command, pgEnv) ->
                new ExecResult(1, "pg_dump: error: connection refused"));
        try {
            executor(fullPgEnv()).dump("his", tableFamilySpec("t1"), tempDir.resolve("out"));
            fail("退出码非零必须抛");
        } catch (DbDumpException e) {
            assertTrue("错误含 stderr 尾部: " + e.getMessage(),
                    e.getMessage().contains("connection refused"));
        }
    }

    @Test
    public void dump_empty_product_rejected() throws IOException {
        Path outDir = tempDir.resolve("empty-out");
        Files.createDirectories(outDir);
        behaviorByCommand.put("pg_dump", (command, pgEnv) -> {
            for (String arg : command) {
                if (arg.startsWith("--file=")) {
                    Files.write(Paths.get(arg.substring("--file=".length())), new byte[0]);
                }
            }
            return new ExecResult(0, "");
        });
        try {
            executor(fullPgEnv()).dump("his", tableFamilySpec("t1"), outDir);
            fail("空产物必须拒绝");
        } catch (DbDumpException e) {
            assertTrue(e.getMessage().contains("产物为空"));
        }
    }

    // ==================== T7 file-copy 整拷 ====================

    @Test
    public void t7_file_copy_captures_whole_dir_with_companion_files() throws IOException {
        Path mediaDir = storageRoot.resolve("com.ecat").resolve("integration-media");
        Files.createDirectories(mediaDir);
        Files.write(mediaDir.resolve("media.db"), "SQLITE-MAIN".getBytes(StandardCharsets.UTF_8));
        Files.write(mediaDir.resolve("media.db-wal"), "SQLITE-WAL".getBytes(StandardCharsets.UTF_8));

        Path outDir = tempDir.resolve("media-dump");
        executor(fullPgEnv()).dump("media", fileCopySpec(), outDir);

        assertTrue(Files.isRegularFile(outDir.resolve("files").resolve("media.db")));
        assertTrue("伴生 -wal 文件随整拷", Files.isRegularFile(outDir.resolve("files").resolve("media.db-wal")));
        String manifest = new String(Files.readAllBytes(outDir.resolve("files.sha256")),
                StandardCharsets.UTF_8);
        assertTrue(manifest.contains("media.db"));
        assertTrue(manifest.contains("media.db-wal"));
        assertEquals("字节级一致", "SQLITE-MAIN",
                new String(Files.readAllBytes(outDir.resolve("files").resolve("media.db")),
                        StandardCharsets.UTF_8));
    }

    @Test
    public void t7_file_copy_missing_dir_rejected() throws IOException {
        try {
            executor(fullPgEnv()).dump("media", fileCopySpec(), tempDir.resolve("out"));
            fail("存储目录缺失必须拒绝(无库可拷即异常,非空拷)");
        } catch (DbDumpException e) {
            assertTrue(e.getMessage().contains("存储目录缺失"));
        }
    }

    // ==================== restore 分派 ====================

    @Test
    public void restore_table_family_plain_pg_runs_pg_restore_without_wrap() throws IOException {
        Path dumpDir = tempDir.resolve("dump");
        Files.createDirectories(dumpDir);
        Files.write(dumpDir.resolve("dump.pgdump"), "PGDMP".getBytes(StandardCharsets.UTF_8));
        // psql 探测:无 timescaledb(空输出)
        behaviorByCommand.put("psql", (command, pgEnv) -> new ExecResult(0, ""));

        executor(fullPgEnv()).restore("his", tableFamilySpec("t1"), dumpDir);

        // commands[0]=psql 探测,其余=pg_restore(无 pre/post 包装)
        List<String[]> restores = new ArrayList<>();
        for (String[] command : commands) {
            if (command[0].equals("pg_restore")) {
                restores.add(command);
            }
        }
        assertEquals("仅 pg_restore 一条(无 pre/post 包装)", 1, restores.size());
        List<String> args = Arrays.asList(restores.get(0));
        assertTrue(args.contains("--clean"));
        assertTrue(args.contains("--if-exists"));
        assertTrue(args.contains("--no-owner"));
        assertTrue("归档路径入参", args.contains(dumpDir.resolve("dump.pgdump").toString()));
        assertFalse("普通 PG 库不跑 pre_restore 包装",
                anyCommandContains("timescaledb_pre_restore"));
    }

    @Test
    public void restore_timescale_wraps_pre_post_and_post_runs_on_failure() throws IOException {
        Path dumpDir = tempDir.resolve("dump");
        Files.createDirectories(dumpDir);
        Files.write(dumpDir.resolve("dump.pgdump"), "PGDMP".getBytes(StandardCharsets.UTF_8));
        behaviorByCommand.put("psql", (command, pgEnv) -> {
            if (command.contains("SELECT 1 FROM pg_extension WHERE extname='timescaledb'")) {
                return new ExecResult(0, "1\n");
            }
            return new ExecResult(0, "");
        });
        behaviorByCommand.put("pg_restore", (command, pgEnv) ->
                new ExecResult(1, "pg_restore: error"));

        try {
            executor(fullPgEnv()).restore("his", tableFamilySpec("t1"), dumpDir);
            fail("pg_restore 失败必须抛");
        } catch (DbDumpException e) {
            // 预期
        }
        // 序列:[探测, pre_restore, pg_restore, post_restore]——失败也跑 post
        assertEquals("pre → pg_restore → post(失败也跑 post,含前置探测共 4 条)", 4, commands.size());
        assertTrue(commands.get(1)[0].equals("psql")
                && arrayContains(commands.get(1), "timescaledb_pre_restore"));
        assertEquals("pg_restore", commands.get(2)[0]);
        assertTrue(commands.get(3)[0].equals("psql")
                && arrayContains(commands.get(3), "timescaledb_post_restore"));
    }

    @Test
    public void restore_file_copy_replaces_live_dir_and_rejects_tampered_product() throws IOException {
        Path dumpDir = tempDir.resolve("media-dump");
        Path liveDb = storageRoot.resolve("com.ecat").resolve("integration-media").resolve("media.db");
        Files.createDirectories(liveDb.getParent());
        Files.write(liveDb, "OLD".getBytes(StandardCharsets.UTF_8));
        executor(fullPgEnv()).dump("media", fileCopySpec(), dumpDir);

        // 篡改快照产物 → 拒绝恢复(坏产物不落生产存储面)
        Files.write(dumpDir.resolve("files").resolve("media.db"),
                "TAMPERED".getBytes(StandardCharsets.UTF_8));
        try {
            executor(fullPgEnv()).restore("media", fileCopySpec(), dumpDir);
            fail("产物校验失败必须拒绝");
        } catch (DbDumpException e) {
            assertTrue(e.getMessage().contains("校验失败"));
        }

        // dump 后 live 数据已变;快照产物复原(解除篡改)→ restore 用快照产物整目录替换 live
        Files.write(dumpDir.resolve("files").resolve("media.db"), "OLD".getBytes(StandardCharsets.UTF_8));
        Files.write(liveDb, "CHANGED".getBytes(StandardCharsets.UTF_8));
        executor(fullPgEnv()).restore("media", fileCopySpec(), dumpDir);
        assertEquals("快照字节回填", "OLD",
                new String(Files.readAllBytes(liveDb), StandardCharsets.UTF_8));
    }

    // ==================== 部署前置预检 ====================

    @Test
    public void verify_pg_dump_missing_fails_with_deploy_prerequisite_message() {
        behaviorByCommand.put("pg_dump", (command, pgEnv) -> {
            throw new IOException("Cannot run program \"pg_dump\"");
        });
        try {
            executor(fullPgEnv()).verifyPgDumpAvailable();
            fail("pg_dump 缺失必须显形为部署前置错误");
        } catch (DbDumpException e) {
            assertTrue(e.getMessage().contains("部署前置缺失"));
        }
    }

    @Test
    public void verify_pg_dump_available_passes() {
        executor(fullPgEnv()).verifyPgDumpAvailable();
        assertEquals(1, commands.size());
    }

    // ==================== sha256 助手 ====================

    @Test
    public void sha256_hex_matches_known_vector() throws IOException {
        Path file = tempDir.resolve("known.txt");
        Files.write(file, "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                DbDumpExecutor.sha256Hex(file));
    }

    private static boolean arrayContains(String[] array, String fragment) {
        for (String item : array) {
            if (item.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyCommandContains(String fragment) {
        for (String[] command : commands) {
            if (arrayContains(command, fragment)) {
                return true;
            }
        }
        return false;
    }
}
