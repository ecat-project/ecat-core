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
 *       强制结束对方 flow 而非抛 {@code DuplicateUniqueIdException}。
 *       <b>仅用于</b>"新 provision 应取代被弃/陈旧 pending flow"的业务驱动 flow（如 env-air-device-manager：
 *       用户 abandon 后以同 SN 重试，新 flow 取代泄漏的旧 flow）。
 *       <b>默认 false</b>：通用 flow（自发现 / 交互式 SPA）遇 uniqueId 冲突即停（抛异常），防止无限创建 flow 占资源。</li>
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

    /** last-writer-win：遇 active-flow 同 uniqueId 冲突时强制结束对方（业务驱动 flow 用）。默认 false。 */
    @Builder.Default
    private final boolean lastWriterWins = false;

    /** 全默认配置（通用 flow 用，等价 {@code builder().build()}）。 */
    public static FlowContextConfig defaults() {
        return FlowContextConfig.builder().build();
    }
}
