package com.ecat.core.Version;

import java.util.function.Supplier;

/**
 * 测试专用桥:为跨包测试提供包级缝的受控注入点,生产禁用。
 *
 * <p>缝本体保持包级可见(生产面零扩),但 boot 真实 EcatCore 的既有单测分布在其他包,
 * init 的版本日志在无 manifest 环境会 fail-fast——经本类为这些用例供合成版本值。
 * 用法=@Before 经 {@link #currentSource()} 存现值并注入合成值,@After 再注入回存值
 * (JUnit 保证用例异常路径也走 @After,存/注严格成对)。
 */
public final class CoreVersionsTestAccess {

    private CoreVersionsTestAccess() {
    }

    /** 读缝现值(供 @Before 保存)。 */
    public static Supplier<String> currentSource() {
        return CoreVersions.versionSource;
    }

    /** 写缝(供 @Before 注入合成版本、@After 还原现值)。 */
    public static void injectSource(Supplier<String> source) {
        CoreVersions.versionSource = source;
    }
}
