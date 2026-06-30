package com.ecat.core.Bus.event;

import java.util.UUID;

/**
 * 事件/状态变更的溯源上下文——贯穿 publish→event→state 链路。
 *
 * <p>只提供溯源信息（触发来源、因果链、操作主体），让开发者和消费者自行判断并实现防循环逻辑；
 * 本身不做自动循环抑制、不设深度阈值（对齐 Home Assistant context 设计）。
 *
 * <p>source 标变更来源，便于审计与权限追溯；parentUuid 形成因果链，消费者重发布前可据此判断
 * "是否由自身触发"以避免循环（配合既有的逻辑绑定 DAG 无环结构性保证）。
 */
public final class EventContext {

    /** 变更来源——区分设备轮询、用户操作、逻辑重发布、跨集成直写、系统生命周期，供审计与去重使用。 */
    public enum Source {
        /** 设备轮询上报触发 */
        DEVICE_POLL,
        /** 用户经 API 操作触发 */
        USER_ACTION,
        /** 逻辑属性绑定重发布触发 */
        LOGIC_REPUBLISH,
        /** 跨集成直写（告警联动断电/开风扇等）触发 */
        CROSS_INTEGRATION,
        /** 系统生命周期事件触发（集成加载、配置条目生命周期、异步任务、发现通知等无具体 actor 的事件） */
        SYSTEM
    }

    private final String uuid;
    private final String parentUuid;
    private final Source source;
    private final String userId;

    private EventContext(String uuid, String parentUuid, Source source, String userId) {
        if (uuid == null || source == null) {
            throw new IllegalArgumentException("uuid and source must not be null");
        }
        this.uuid = uuid;
        this.parentUuid = parentUuid;
        this.source = source;
        this.userId = userId;
    }

    /** 新建无父链的根 context——设备轮询、用户操作等变更起点的溯源。 */
    public static EventContext root(Source source, String userId) {
        return new EventContext(UUID.randomUUID().toString(), null, source, userId);
    }

    /** 以 parent 为父链派生子 context——逻辑重发布时把收到的 context 作为父，形成因果链。 */
    public static EventContext chain(EventContext parent, Source source, String userId) {
        return new EventContext(UUID.randomUUID().toString(),
                parent == null ? null : parent.getUuid(), source, userId);
    }

    public String getUuid() { return uuid; }
    public String getParentUuid() { return parentUuid; }
    public Source getSource() { return source; }
    public String getUserId() { return userId; }
}
