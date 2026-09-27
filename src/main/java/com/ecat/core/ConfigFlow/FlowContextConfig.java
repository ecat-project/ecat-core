package com.ecat.core.ConfigFlow;

import lombok.Builder;
import lombok.Value;

/**
 * FlowContext 创建期行为配置（经 {@link ConfigFlowService#startFlow(String, FlowContextConfig)} 注入）。
 *
 * <p>用配置对象而非给 startFlow 叠加多个布尔参数，便于后期扩展——新增选项只需在此类加字段，不改方法签名。
 *
 * <p>当前选项：
 * <ul>
 *   <li>{@link #lastWriterWins} — last-writer-win 模式：{@code setEntryUniqueId} 遇其他 active flow 占同 uniqueId 时，
 *       强制结束对方 flow 而非抛 {@code DuplicateUniqueIdException}（用户 abandon 向导后同 SN 重试 → 新向导顶替旧向导）。
 *       <b>默认开启的入口</b>：USER 源向导——{@code ConfigFlowService#startFlow(String)} 无 config 重载
 *       默认注入 {@code lastWriterWins=true}；显式传 config 的调用方（含显式关闭）尊重传入、不被覆盖。
 *       <b>不开启的入口</b>：discovery 源（startDiscoveryFlow，uniqueId 冲突不顶替对方——
 *       抛 {@code DuplicateUniqueIdException} 经 drive 收口为 clean ABORT）、
 *       RECONFIGURE（结构性跳过唯一性校验），以及直接以 {@link #defaults()} 构造的 flow
 *       （defaults 保持保守 false：不经 startFlow 的自定义构造遇冲突即停，防止无限创建 flow 占资源）。</li>
 * </ul>
 *
 * <p>注：{@code lastWriterWins} 仅影响 <b>active-flow（未提交）冲突</b>；
 * <b>持久化 entry 冲突</b>一律由框架在保存时收口（抛异常），不受此 flag 影响——避免静默覆盖已提交设备。
 *
 * @author coffee
 */
@Value
@Builder
public class FlowContextConfig {

    /** last-writer-win：遇 active-flow 同 uniqueId 冲突时强制结束对方。字段默认 false（保守层）；USER 源 startFlow 入口默认注入 true。 */
    @Builder.Default
    private final boolean lastWriterWins = false;

    /** 全默认配置（等价 {@code builder().build()}，lastWriterWins=false 保守层——显式传入 startFlow 即显式关闭 LWW）。 */
    public static FlowContextConfig defaults() {
        return FlowContextConfig.builder().build();
    }
}
