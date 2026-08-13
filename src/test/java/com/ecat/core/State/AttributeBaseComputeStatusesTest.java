package com.ecat.core.State;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 验证物理 AttributeBase 的多状态默认行为：computeStatuses 钩子默认返回 {当前 status}，
 * 且 buildState 把它注入 AttrState.statuses 字段发布（物理 attr 零行为变化——仍只有一个 status）。
 *
 * <p>本测试是 ADM 监控 M1 Phase 2 的落点验证（Task 2.1）。logic 子类覆写 computeStatuses
 * 返回登记簿全量的能力在 integration-logicdevice 模块单测覆盖，此处只锁物理 attr 默认分支。
 */
public class AttributeBaseComputeStatusesTest {

    /** 最小 AttributeBase 子类，只为验证 computeStatuses 默认分支 + buildState 注入。 */
    static class StubAttr extends AttributeBase<Double> {
        public StubAttr(String id) {
            super(id, AttributeClass.VALUE, null, null, 0, false, false);
        }

        @Override public String getDisplayValue(UnitInfo toUnit) { return null; }
        @Override protected Double convertFromUnitImp(Double v, UnitInfo u) { return v; }
        @Override public Double convertValueToUnit(Double v, UnitInfo f, UnitInfo t) { return v; }
        @Override public ConfigDefinition getValueDefinition() { return null; }
        @Override protected I18nKeyPath getI18nPrefixPath() { return new I18nKeyPath("test.", "test"); }
        @Override public AttributeType getAttributeType() { return AttributeType.NUMERIC; }
    }

    /** 造一个最小可用的 DeviceBase（用于满足 updateValue 内 device!=null && id!=null 才建 midState 的条件）。
     *  用 ConfigEntry.builder() 保证 data 是非空 Map（initDevice 不会 NPE）；override 接口空方法。 */
    private DeviceBase newStubDevice() {
        ConfigEntry entry = new ConfigEntry.Builder().build();
        return new DeviceBase(entry) {
            @Override public void load(com.ecat.core.EcatCore core) { }
            @Override public void init() { }
            @Override public void start() { }
            @Override public void stop() { }
            @Override public void release() { }
        };
    }

    /** computeStatuses 默认实现：物理属性只有单一 status，返回单元素集合 {status}。 */
    @Test
    public void computeStatuses_defaultIsSingletonOfCurrentStatus() {
        StubAttr attr = new StubAttr("stub");
        attr.setDevice(newStubDevice());
        attr.updateValue(15.0, AttributeStatus.NORMAL);

        Set<AttributeStatus> expected = Collections.singleton(AttributeStatus.NORMAL);
        assertEquals("物理 attr computeStatuses 默认应是 {当前 status} 单元素集合",
                expected, attr.computeStatuses());
    }

    /** buildState 通过 computeStatuses 把 statuses 注入 AttrState：物理 attr 的 getStatuses() 返回 {status}。 */
    @Test
    public void buildState_publishesStatusesField_forPhysicalAttr() {
        StubAttr attr = new StubAttr("stub");
        attr.setDevice(newStubDevice());
        attr.updateValue(200.0, AttributeStatus.OVER_UPPER_LIMIT);

        // updateValue 已构建 midState（device 非空且 id 非空），getState 取 midState——无需走 publicState 总线发布
        assertNotNull("updateValue 后应已构建 midState", attr.getState());
        assertEquals("主 status 应为 OVER_UPPER_LIMIT",
                AttributeStatus.OVER_UPPER_LIMIT, attr.getState().getStatus());
        assertEquals("buildState 经 computeStatuses 注入 statuses，物理 attr 应为 {status} 单元素集合",
                Collections.singleton(AttributeStatus.OVER_UPPER_LIMIT),
                attr.getState().getStatuses());
    }
}
