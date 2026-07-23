package com.ecat.core.Device;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Bus.event.DeviceLifecycleEvent;
import com.ecat.core.ConfigEntry.ConfigEntry;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * 00-core：DeviceRegistry load/matchIndex/getOrCreate + Phase 4 正交化 API（replace/disable/remove/purge）。
 * <p>commit 已收为 private（原 register(DeviceBase,Action)），create/enable 走 getOrCreate、reconfigure 走 replace、
 * 禁用走 disable、逻辑删走 remove、硬删走 purge。
 */
public class DeviceRegistryGetOrCreateTest {

    private DeviceBase newDevice(String entryId, String uniqueId, String coordinate) {
        ConfigEntry e = new ConfigEntry();
        e.setEntryId(entryId);
        e.setUniqueId(uniqueId);
        e.setCoordinate(coordinate);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "n-" + entryId);
        e.setData(data);
        return new DeviceBase(e) {
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
        };
    }

    @Test
    public void getOrCreate_firstMints_thenReusesSameId() throws Exception {
        File tmp = Files.createTempDirectory("ecat-reg-goc").toFile();
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(new YmlDevicePersistence(tmp.getAbsolutePath()));
        DeviceBase d1 = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d1, DeviceLifecycleEvent.Action.CREATE);
        String id1 = d1.getId();
        DeviceBase d2 = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d2, DeviceLifecycleEvent.Action.CREATE);
        assertEquals("同 (coordinate,uniqueId) 应复用同一持久化 id", id1, d2.getId());
    }

    @Test
    public void load_repopulatesMatchIndex_acrossRestart() throws Exception {
        File tmp = Files.createTempDirectory("ecat-reg-load").toFile();
        DeviceRegistry reg1 = new DeviceRegistry();
        reg1.setPersistence(new YmlDevicePersistence(tmp.getAbsolutePath()));
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg1.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);
        String persistedId = d.getId();

        // 模拟重启：新 registry 从同一 yml 目录 load
        DeviceRegistry reg2 = new DeviceRegistry();
        reg2.setPersistence(new YmlDevicePersistence(tmp.getAbsolutePath()));
        reg2.load();
        DeviceBase d2 = newDevice("e1", "u1", "com.ecat:c");
        reg2.getOrCreate(d2, DeviceLifecycleEvent.Action.CREATE);
        assertEquals("重启后 getOrCreate 应复原同一持久化 id", persistedId, d2.getId());
    }

    @Test
    public void getOrCreate_publishesCreate_disablePublishesRemove() {
        DeviceRegistry reg = new DeviceRegistry();
        BusRegistry bus = new BusRegistry();
        reg.setBusRegistry(bus);
        final AtomicReference<DeviceLifecycleEvent> seen = new AtomicReference<>();
        bus.subscribe(BusTopic.DEVICE_LIFECYCLE.getTopicName(), event -> {
            if (event.getPayload() instanceof DeviceLifecycleEvent) {
                seen.set((DeviceLifecycleEvent) event.getPayload());
            }
        });
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);
        assertNotNull(seen.get());
        assertEquals(DeviceLifecycleEvent.Action.CREATE, seen.get().getAction());
        assertEquals(d.getId(), seen.get().getDeviceId());

        seen.set(null);
        reg.disable(d);
        assertNotNull("disable 也应发 REMOVE", seen.get());
        assertEquals(DeviceLifecycleEvent.Action.REMOVE, seen.get().getAction());
    }

    @Test
    public void disable_keepsRecordAndMatchIndex_revivesSameId() throws Exception {
        File tmp = Files.createTempDirectory("ecat-reg-disable").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(p);
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);
        File yml = new File(tmp, "com.ecat/c/" + d.getId() + ".yml");

        reg.disable(d);
        assertNull("disable 后应从 active map 移除", reg.getDeviceByID(d.getId()));
        assertTrue("disable 应保留 record yml（供 enable 复原）", yml.exists());
        // matchIndex 保留：同 (coordinate,uniqueId) 再 getOrCreate 复原同 id
        DeviceBase d2 = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d2, DeviceLifecycleEvent.Action.CREATE);
        assertEquals("disable 保留 matchIndex，getOrCreate 复原同 id", d.getId(), d2.getId());
    }

    @Test
    public void purge_deletesRecordAndMatchIndex_mintsNewId() throws Exception {
        File tmp = Files.createTempDirectory("ecat-reg-purge").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(p);
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);
        File yml = new File(tmp, "com.ecat/c/" + d.getId() + ".yml");
        assertTrue(yml.exists());

        reg.purge(d);
        assertNull("purge 后应从 active map 移除", reg.getDeviceByID(d.getId()));
        assertFalse("purge 应删除 record yml", yml.exists());
        // matchIndex 删除：再 getOrCreate 铸新 id（不复原）
        DeviceBase d2 = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d2, DeviceLifecycleEvent.Action.CREATE);
        assertNotEquals("purge 删 matchIndex 后 getOrCreate 铸新 id", d.getId(), d2.getId());
    }

    @Test
    public void remove_marksDeleted_nullsEntryId_keepsMatchIndex_revivesSameId() throws Exception {
        File tmp = Files.createTempDirectory("ecat-reg-remove").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(p);
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);
        String id = d.getId();

        reg.remove(d);
        assertNull("remove 后应从 active map 移除", reg.getDeviceByID(id));

        // 重新发现：getOrCreate 命中保留的 matchIndex 复原同 id，commit 重写记录翻回 deleted=false
        DeviceBase d2 = newDevice("e2", "u1", "com.ecat:c"); // 新 entryId，同 uniqueId
        reg.getOrCreate(d2, DeviceLifecycleEvent.Action.CREATE);
        assertEquals("remove 保留 matchIndex，重新发现复原同 deviceId", id, d2.getId());
        // 读回记录确认复活后 deleted=false（loadAll 读 yml）
        DeviceRecord revived = p.loadAll().stream()
            .filter(r -> id.equals(r.getId())).findFirst()
            .orElseThrow(() -> new AssertionError("record 应存在"));
        assertFalse("重新发现后记录应翻回 deleted=false", revived.isDeleted());
    }


    @Test
    public void remove_persistsDeletedFlag_toYml_reloadReadsTrue() throws Exception {
        // 守护：remove 标的 deleted=true 必须真正落盘到 yml，重启 loadAll 读回仍为 true。
        // 防回归：YmlDevicePersistence 两个 convert 方法若漏处理 deleted 字段，
        // yml 不存该字段 → 读回恒为默认 false → 逻辑删记录无法与活跃记录区分（审计失效）。
        File tmp = Files.createTempDirectory("ecat-reg-del-persist").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(p);
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);
        String id = d.getId();

        reg.remove(d);

        DeviceRecord persisted = p.loadAll().stream()
            .filter(r -> id.equals(r.getId())).findFirst()
            .orElseThrow(() -> new AssertionError("逻辑删记录应保留在 yml"));
        assertTrue("remove 的 deleted=true 必须持久化到 yml 并能读回", persisted.isDeleted());
        assertNull("逻辑删记录 entryId 应持久化为 null", persisted.getEntryId());
    }

    @Test
    public void replace_overwritesSameKey_newInheritsOldId_singleReconfigureEvent() throws Exception {
        File tmp = Files.createTempDirectory("ecat-reg-replace").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(p);
        BusRegistry bus = new BusRegistry();
        reg.setBusRegistry(bus);
        List<DeviceLifecycleEvent> events = new ArrayList<>();
        bus.subscribe(BusTopic.DEVICE_LIFECYCLE.getTopicName(),
            e -> { if (e.getPayload() instanceof DeviceLifecycleEvent) events.add((DeviceLifecycleEvent) e.getPayload()); });

        DeviceBase oldD = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(oldD, DeviceLifecycleEvent.Action.CREATE);
        String oldId = oldD.getId();
        events.clear();

        DeviceBase newD = newDevice("e1", "u1", "com.ecat:c"); // 同 coordinate+uniqueId
        reg.replace(oldD, newD);

        assertEquals("new 继承 old 的稳定 id", oldId, newD.getId());
        assertSame("map 中同 key 已被 new 覆盖", newD, reg.getDeviceByID(oldId));
        assertEquals("replace 只发 1 个 RECONFIGURE（不双发 REMOVE→CREATE）", 1, events.size());
        assertEquals(DeviceLifecycleEvent.Action.RECONFIGURE, events.get(0).getAction());
    }

    @Test(expected = IllegalStateException.class)
    public void replace_rejectsMismatchedOldNew() {
        DeviceRegistry reg = new DeviceRegistry();
        DeviceBase oldD = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(oldD, DeviceLifecycleEvent.Action.CREATE);
        DeviceBase newD = newDevice("e2", "u2", "com.ecat:c"); // 不同 uniqueId → 断言护栏应抛
        reg.replace(oldD, newD);
    }

    @Test
    public void findDevicesByEntryId_returnsAllChildren() {
        DeviceRegistry reg = new DeviceRegistry();
        DeviceBase c1 = newDevice("gw-1", "child-1", "com.ecat:c");
        DeviceBase c2 = newDevice("gw-1", "child-2", "com.ecat:c");
        DeviceBase other = newDevice("ent-other", "u-other", "com.ecat:c");
        reg.getOrCreate(c1, DeviceLifecycleEvent.Action.CREATE);
        reg.getOrCreate(c2, DeviceLifecycleEvent.Action.CREATE);
        reg.getOrCreate(other, DeviceLifecycleEvent.Action.CREATE);
        assertEquals(2, reg.findDevicesByEntryId("gw-1").size());
        assertEquals(1, reg.findDevicesByEntryId("ent-other").size());
    }
}
