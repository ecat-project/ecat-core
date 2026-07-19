package com.ecat.core.Device;

import com.ecat.core.Utils.DateTimeUtils;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.List;

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
}
