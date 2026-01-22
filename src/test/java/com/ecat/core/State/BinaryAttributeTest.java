package com.ecat.core.State;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ecat.core.Device.DeviceBase;

/**
 * 测试 BinaryAttribute 类的功能
 * 
 * @author coffee
 */
public class BinaryAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;
    @Mock
    private Function<AttrChangedCallbackParams<Boolean>, CompletableFuture<Boolean>> mockCallback;
    @Mock
    private DeviceBase mockDevice;

    private BinaryAttribute attr;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("开关");
        when(mockDevice.getId()).thenReturn("testDeviceId");
        // mockCallback 需要模拟 apply 返回，否则 asyncTurnOn/asyncTurnOff 会 NPE
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attr = new BinaryAttribute(
                "switch1",
                mockAttrClass,
                true,
                mockCallback
        );
        attr.setDevice(mockDevice);
    }

    @Test
    public void testConstructorAndGetters() {
        // 测试构造函数和getter方法，确保属性初始化正确
        assertEquals("switch1", attr.getAttributeID());
        // 现在使用I18n系统
        assertNotNull(attr.getDisplayName());
        assertEquals("state.binary_attr.switch1", attr.getDisplayName());
        assertEquals("state.binary_attr.switch1", attr.getI18nDisplayName());
        assertEquals(mockAttrClass, attr.attrClass);
        assertTrue(attr.canValueChange());
        assertFalse(attr.canUnitChange());

        // 验证I18n的On/Off正确
        assertEquals("state.binary_attr.switch1_options.on", attr.getOnDisplayText());
        assertEquals("state.binary_attr.switch1_options.off", attr.getOffDisplayText());
    }

    @Test
    public void testGetDisplayValue() {
        // 测试getDisplayValue方法，现在使用I18n系统
        attr.value = true;
        String onText = attr.getDisplayValue(null);
        assertNotNull(onText);
        assertEquals("state.binary_attr.switch1_options.on", onText);
        // assertTrue(onText.contains("ON") || onText.contains("on"));

        attr.value = false;
        String offText = attr.getDisplayValue(null);
        assertNotNull(offText);
        assertEquals("state.binary_attr.switch1_options.off", offText);
        // assertTrue(offText.contains("OFF") || offText.contains("off"));

        attr.value = null;
        assertNull(attr.getDisplayValue(null));

        // 测试没有绑定device
        BinaryAttribute unboundAttr = new BinaryAttribute("unboundSwitch", mockAttrClass, true, mockCallback);
        unboundAttr.value = true;
        String unboundOnText = unboundAttr.getDisplayValue(null);
        assertNotNull(unboundOnText);
        assertEquals("state.binary_attr.unboundswitch_options.on", unboundOnText);
        // assertTrue(unboundOnText.contains("ON") || unboundOnText.contains("on"));
    }

    @Test
    public void testConvertFromUnitImp() {
        // 测试convertFromUnitImp方法，直接返回原值
        assertTrue(attr.convertFromUnitImp(true, null));
        assertFalse(attr.convertFromUnitImp(false, null));
    }

    @Test
    // @Test(expected = UnsupportedOperationException.class)
    public void testSetDisplayValueImpOK() throws Exception {
        // 测试setDisplayValueImp顺利执行设置
        CompletableFuture<Boolean> future = attr.setDisplayValueImp(true, attr.getDisplayUnit());
        assertTrue(future.get());
        assertTrue(attr.getValue());
    }

    @Test
    public void testSetDisplayValue_Success() throws Exception {
        // 测试通过字符串设置显示值并成功回调
        CompletableFuture<Boolean> future = attr.setDisplayValue("on");
        assertTrue(future.get());
        assertTrue(attr.getValue());

        future = attr.setDisplayValue("off");
        assertTrue(future.get());
        assertFalse(attr.getValue());
    }

    @Test
    public void testTurnOnAndOff() {
        // 测试turnOn和turnOff方法
        assertTrue(attr.turnOn());
        assertTrue(attr.value);
        assertTrue(attr.turnOff());
        assertFalse(attr.value);
    }

    @Test
    public void testAsyncTurnOnAndOff() throws Exception {
        // 测试异步asyncTurnOn和asyncTurnOff方法
        CompletableFuture<Boolean> onFuture = attr.asyncTurnOn();
        assertTrue(onFuture.get());
        assertTrue(attr.value);

        CompletableFuture<Boolean> offFuture = attr.asyncTurnOff();
        assertTrue(offFuture.get());
        assertFalse(attr.value);
    }

    @Test
    public void testAsyncTurnOnImplAndOffImpl() throws Exception {
        // 测试asyncTurnOnImpl和asyncTurnOffImpl方法
        assertTrue(attr.asyncTurnOnImpl().get());
        assertTrue(attr.asyncTurnOffImpl().get());
    }

    @Test
    public void testDeprecatedConstructorCompatibility() {
        // 测试已弃用的构造函数兼容性
        BinaryAttribute deprecatedAttr = new BinaryAttribute(
                "deprecatedSwitch",
                "开关",
                mockAttrClass,
                true,
                mockCallback
        );
        deprecatedAttr.setDevice(mockDevice);

        // 验证基本功能仍然正常工作
        assertEquals("deprecatedSwitch", deprecatedAttr.getAttributeID());
        assertEquals(mockAttrClass, deprecatedAttr.attrClass);
        assertTrue(deprecatedAttr.canValueChange());
        assertFalse(deprecatedAttr.canUnitChange());

        // 验证I18n路径正确生成
        assertEquals("state.binary_attr.deprecatedswitch", deprecatedAttr.getI18nPrefixPath().withLastSegment("deprecatedswitch").getI18nPath());

        // 验证displayName仍然可访问（已弃用）
        assertEquals("开关", deprecatedAttr.getDisplayName());

        // 验证基本操作仍然正常工作
        assertTrue(deprecatedAttr.turnOn());
        assertTrue(deprecatedAttr.getValue());
        assertTrue(deprecatedAttr.turnOff());
        assertFalse(deprecatedAttr.getValue());
    }
}
