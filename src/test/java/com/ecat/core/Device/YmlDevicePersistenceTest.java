package com.ecat.core.Device;

import com.ecat.core.Utils.DateTimeUtils;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** 00-core Task 3：DeviceRecord + YmlDevicePersistence round-trip / delete / 目录结构。 */
public class YmlDevicePersistenceTest {

    @Test
    public void save_then_loadAll_roundtrip() throws Exception {
        File tmp = Files.createTempDirectory("ecat-device-persist-test").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        ZonedDateTime now = DateTimeUtils.now();
        DeviceRecord r = DeviceRecord.builder()
                .id("dev-1").coordinate("com.ecat:integration-x")
                .uniqueId("sn-1").entryId("ent-1")
                .name("n").vendor("v").model("m")
                .createTime(now).updateTime(now).build();
        p.save(r);

        List<DeviceRecord> all = p.loadAll();
        assertEquals(1, all.size());
        DeviceRecord got = all.get(0);
        assertEquals("dev-1", got.getId());
        assertEquals("com.ecat:integration-x", got.getCoordinate());
        assertEquals("sn-1", got.getUniqueId());
        assertEquals("ent-1", got.getEntryId());
        assertEquals("n", got.getName());
        assertEquals("v", got.getVendor());
        assertEquals("m", got.getModel());
        assertNotNull(got.getCreateTime());
    }

    @Test
    public void file_laidOutByCoordinate() throws Exception {
        File tmp = Files.createTempDirectory("ecat-device-layout").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRecord r = DeviceRecord.builder()
                .id("abc").coordinate("com.ecat:integration-y").uniqueId("u").build();
        p.save(r);
        File f = new File(tmp, "com.ecat/integration-y/abc.yml");
        assertTrue("应按 coordinate 分目录存放", f.exists());
    }

    @Test
    public void delete_removesFile() throws Exception {
        File tmp = Files.createTempDirectory("ecat-device-del").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRecord r = DeviceRecord.builder()
                .id("rm-1").coordinate("com.ecat:integration-z").uniqueId("u").build();
        p.save(r);
        File f = new File(tmp, "com.ecat/integration-z/rm-1.yml");
        assertTrue(f.exists());
        p.delete("rm-1");
        assertFalse("delete 后文件应移除", f.exists());
    }

    @Test
    public void update_overwrites() throws Exception {
        File tmp = Files.createTempDirectory("ecat-device-upd").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        p.save(DeviceRecord.builder().id("u1").coordinate("com.ecat:c").uniqueId("old").build());
        p.update(DeviceRecord.builder().id("u1").coordinate("com.ecat:c").uniqueId("new").build());
        List<DeviceRecord> all = p.loadAll();
        assertEquals(1, all.size());
        assertEquals("new", all.get(0).getUniqueId());
    }

    /**
     * 并发 save 压力形态（mass-delete 高压窗复刻，2026-10-07 悬空锚缺陷的 TDD 红测）：
     * 多线程共享同一 {@code YmlDevicePersistence}（内部单 snakeyaml {@code Yaml} 实例）
     * 并发 save 不同设备文件，落盘结果必须全部可解析且字段往返一致。
     * <p>
     * 机制：snakeyaml {@code Yaml} 实例非线程安全——{@code BaseRepresenter.representedObjects}
     * 是跨 dump 调用共享的 IdentityHashMap，{@code DumperOptions} 的 AnchorGenerator 锚计数器
     * 也是共享可变状态。无同步并发 dump 时线程间 Node 树交叉复用，Emitter 输出交错的根锚
     * 定义（{@code &idNNN}）与悬空别名（{@code *idNNN}），文件不可解析（unhashable/recursive
     * key）或字段解析成错误形态。record 取逻辑删写出形态（entryId=null、deleted=true）：
     * null/Boolean 常量与 intern 字面量 key 是全 JVM 同实例碰撞面最大的载荷。
     */
    @Test
    public void concurrentSave_allFilesParseAndRoundTrip() throws Exception {
        final int threads = 4;
        final int iterations = 2000;
        File tmp = Files.createTempDirectory("ecat-device-concurrent").toFile();
        try {
            final YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
            final Map<String, DeviceRecord> expected = new ConcurrentHashMap<>();
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch startGate = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    final int tid = t;
                    futures.add(pool.submit(() -> {
                        startGate.await();
                        for (int i = 0; i < iterations; i++) {
                            ZonedDateTime now = DateTimeUtils.now();
                            DeviceRecord r = DeviceRecord.builder()
                                    .id("dev-" + tid + "-" + i)
                                    .coordinate("com.ecat:integration-conc")
                                    .uniqueId("sn-" + tid + "-" + i)
                                    .name("name-" + tid + "-" + i)
                                    .vendor("vendor-" + tid)
                                    .model("model-" + i)
                                    .createTime(now)
                                    .updateTime(now)
                                    .deleted(true)
                                    .disabled(false)
                                    .build();
                            expected.put(r.getId(), r);
                            p.save(r);
                        }
                        return null;
                    }));
                }
                startGate.countDown();
                // 传播 worker 内的失败（含竞态下 dump 抛出的运行时异常），确定性等待、无 sleep
                for (Future<?> f : futures) {
                    f.get(120, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            List<File> files = new ArrayList<>();
            collectYmlFiles(tmp, files);
            assertEquals("落盘文件数应等于并发 save 总数", threads * iterations, files.size());

            // 校验侧用独立 Yaml 实例，与被测持久化的共享实例隔离
            Yaml verifier = new Yaml();
            for (File f : files) {
                String id = f.getName().substring(0, f.getName().length() - ".yml".length());
                String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                assertFalse("文件出现锚定义指纹 &idNNN（并发 dump 交错）: " + f + "\n" + head(content),
                        ANCHOR_FINGERPRINT.matcher(content).find());
                assertFalse("文件出现别名指纹 *idNNN（悬空别名）: " + f + "\n" + head(content),
                        ALIAS_FINGERPRINT.matcher(content).find());
                Map<String, Object> m = verifier.load(content);
                DeviceRecord exp = expected.get(id);
                assertNotNull("应有期望记录: " + id, exp);
                assertEquals(id, m.get("id"));
                assertEquals("com.ecat:integration-conc", m.get("coordinate"));
                assertEquals(exp.getUniqueId(), m.get("uniqueId"));
                assertEquals(exp.getName(), m.get("name"));
                assertEquals(exp.getVendor(), m.get("vendor"));
                assertEquals(exp.getModel(), m.get("model"));
                assertEquals(DateTimeUtils.formatIso(exp.getCreateTime()), m.get("createTime"));
                assertEquals(DateTimeUtils.formatIso(exp.getUpdateTime()), m.get("updateTime"));
                assertNull("entryId 应为 null", m.get("entryId"));
                assertEquals(Boolean.TRUE, m.get("deleted"));
                assertEquals(Boolean.FALSE, m.get("disabled"));
            }
        } finally {
            deleteRecursively(tmp);
        }
    }

    private static final Pattern ANCHOR_FINGERPRINT = Pattern.compile("&id\\d+");
    private static final Pattern ALIAS_FINGERPRINT = Pattern.compile("\\*id\\d+");

    private static String head(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200);
    }

    private static void collectYmlFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            if (c.isDirectory()) {
                collectYmlFiles(c, out);
            } else if (c.getName().endsWith(".yml")) {
                out.add(c);
            }
        }
    }

    private static void deleteRecursively(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) {
                    deleteRecursively(c);
                } else {
                    c.delete();
                }
            }
        }
        dir.delete();
    }
}
