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

/**
 * 测试 TextAttribute 类的功能
 * 
 * @author coffee
 */
public class TextAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;
    @Mock
    private UnitInfo mockNativeUnit;
    @Mock
    private UnitInfo mockDisplayUnit;
    @Mock
    private Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> mockCallback;

    private TextAttribute attr;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("文本属性");
        when(mockNativeUnit.getDisplayName()).thenReturn("无");
        when(mockDisplayUnit.getDisplayName()).thenReturn("无");
        attr = new TextAttribute(
                "text1",
                mockAttrClass,
                mockNativeUnit,
                mockDisplayUnit,
                true,
                mockCallback
        );
    }

    @Test
    public void testConstructorAndGetters() {
        assertEquals("text1", attr.getAttributeID());
        // 测试时直接验证I18n路径，不依赖具体的资源文件
        assertEquals("state.text_attr.text1", attr.getI18nPrefixPath().withLastSegment("text1").getI18nPath());
        assertEquals(mockAttrClass, attr.attrClass);
        assertEquals(mockNativeUnit, attr.nativeUnit);
        assertEquals(mockDisplayUnit, attr.displayUnit);
        assertTrue(attr.canValueChange());
        assertFalse(attr.canUnitChange());
    }

    @Test
    public void testUpdateValue() {
        assertTrue(attr.updateValue("hello"));
        assertEquals("hello", attr.getValue());
    }

    @Test
    public void testGetDisplayValue() {
        attr.value = "world";
        assertEquals("world", attr.getDisplayValue(null));
        assertEquals("world", attr.getDisplayValue(mockDisplayUnit));
    }

    @Test
    public void testConvertFromUnitImp() {
        assertEquals("abc", attr.convertFromUnitImp("abc", mockNativeUnit));
    }

    @Test
    public void testSetDisplayValueImp_Success() throws Exception {
        when(mockCallback.apply(any())).thenReturn(CompletableFuture.completedFuture(true));
        attr.onChangedCallback = mockCallback;
        CompletableFuture<Boolean> future = attr.setDisplayValueImp("newValue", attr.getDisplayUnit());
        assertTrue(future.get());
        assertEquals("newValue", attr.getValue());
    }

    @Test
    public void testSetDisplayValueImp_ValueChangeableFalse() throws Exception {
        TextAttribute attr2 = new TextAttribute(
                "text2", mockAttrClass, mockNativeUnit, mockDisplayUnit, false, mockCallback);
        CompletableFuture<Boolean> future = attr2.setDisplayValueImp("shouldFail", attr2.getDisplayUnit());
        assertFalse(future.get());
    }

    @Test
    public void testDeprecatedConstructorCompatibility() {
        // 测试已弃用的构造函数兼容性
        TextAttribute deprecatedAttr = new TextAttribute(
                "deprecatedText",
                "文本属性",
                mockAttrClass,
                mockNativeUnit,
                mockDisplayUnit,
                true,
                mockCallback
        );

        // 验证基本功能仍然正常工作
        assertEquals("deprecatedText", deprecatedAttr.getAttributeID());
        assertEquals(mockAttrClass, deprecatedAttr.attrClass);
        assertEquals(mockNativeUnit, deprecatedAttr.nativeUnit);
        assertEquals(mockDisplayUnit, deprecatedAttr.displayUnit);
        assertTrue(deprecatedAttr.canValueChange());
        assertFalse(deprecatedAttr.canUnitChange());

        // 验证I18n路径正确生成
        assertEquals("state.text_attr.deprecatedtext", deprecatedAttr.getI18nPrefixPath().withLastSegment("deprecatedtext").getI18nPath());

        // 验证displayName仍然可访问（已弃用）
        assertEquals("文本属性", deprecatedAttr.getDisplayName());
    }
}
