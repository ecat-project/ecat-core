package com.ecat.core.State;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.ecat.core.Bus.event.EventContext;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

/**
 * AttributeBase 不可变快照单测——验证 updateValue 原子构建 AttrState、context 溯源、
 * lastChanged 仅值变化推进、未绑定设备不抛异常。
 */
public class AttributeBaseSnapshotTest {

    /** 最小可测子类：Integer 属性，getDisplayValue 返回值字符串。 */
    static class MinimalIntAttribute extends AttributeBase<Integer> {
        MinimalIntAttribute(String id) {
            super(id, null, null, null, 0, false, false, null);
        }
        @Override public String getDisplayValue(UnitInfo toUnit) {
            return value == null ? "" : String.valueOf(value);
        }
        @Override protected Integer convertFromUnitImp(Integer v, UnitInfo u) { return v; }
        @Override public ConfigDefinition getValueDefinition() { return null; }
        @Override public I18nKeyPath getI18nPrefixPath() { return new I18nKeyPath("state.test_attr.", ""); }
        @Override public AttributeType getAttributeType() { return AttributeType.UNKNOWN; }
        @Override public Double convertValueToUnit(Double v, UnitInfo f, UnitInfo t) {
            throw new UnsupportedOperationException();
        }
    }

    @Mock private DeviceBase mockDevice;
    private MinimalIntAttribute attr;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockDevice.getId()).thenReturn("dev1");
        attr = new MinimalIntAttribute("attr1");
        attr.setDevice(mockDevice);
    }

    @Test
    public void snapshotNullBeforeAnyUpdate() {
        // 未 updateValue 前，快照为 null
        assertNull(attr.getState());
    }

    @Test
    public void updateValueBuildsSnapshotWithFieldsAndContext() {
        // updateValue 后快照非空，携带 value/deviceId/status，context 默认设备轮询
        attr.setStatus(AttributeStatus.NORMAL);
        attr.updateValue(42);

        AttrState<?> snap = attr.getState();
        assertNotNull(snap);
        assertEquals("dev1", snap.getDeviceId());
        assertEquals("attr1", snap.getAttrId());
        assertEquals(42, snap.getValue());
        assertEquals(AttributeStatus.NORMAL, snap.getStatus());
        assertEquals("42", snap.getDisplayValue());
        assertNotNull(snap.getContext());
        assertEquals(EventContext.Source.DEVICE_POLL, snap.getContext().getSource());
    }

    @Test
    public void lastChangedAdvancesOnlyOnRealChange() throws Exception {
        // 值变化时 lastChanged 推进；重复刷新相同值不推进
        attr.updateValue(5);
        long c1 = attr.getLastChanged().toEpochMilli();
        assertTrue("首次更新应推进 lastChanged", c1 > 0);

        Thread.sleep(2);
        attr.updateValue(5); // 相同值，不变化
        long c2 = attr.getLastChanged().toEpochMilli();
        assertEquals("相同值刷新不应推进 lastChanged", c1, c2);

        Thread.sleep(2);
        attr.updateValue(6); // 变化
        long c3 = attr.getLastChanged().toEpochMilli();
        assertTrue("值变化应推进 lastChanged", c3 > c2);
    }

    @Test
    public void setEventContextOverridesDefaultInNextSnapshot() {
        // 用户/逻辑入口在 updateValue 前设 context，覆盖默认 DEVICE_POLL
        attr.setEventContext(EventContext.root(EventContext.Source.USER_ACTION, "u1"));
        attr.updateValue(7);
        assertEquals(EventContext.Source.USER_ACTION, attr.getState().getContext().getSource());
        assertEquals("u1", attr.getState().getContext().getUserId());
    }

    @Test
    public void setEventContextNullThrows() {
        // 严格模式：null context 是明确错误，抛异常而非静默兜底
        try {
            attr.setEventContext(null);
            fail("setEventContext(null) 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException ok) { }
    }

    @Test
    public void noDeviceUpdateValueDoesNotThrowAndNoSnapshot() {
        // 未绑定设备（单测场景）updateValue 不应抛异常；快照保持 null（deviceId 是 AttrState 必填）
        MinimalIntAttribute a = new MinimalIntAttribute("solo");
        assertTrue(a.updateValue(99));
        assertNull(a.getState());
    }
}
