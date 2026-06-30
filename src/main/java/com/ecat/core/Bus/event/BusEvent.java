package com.ecat.core.Bus.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 总线事件信封——所有 topic 在总线上传递的统一不可变包装（≡ Home Assistant 的 Event）。
 *
 * <p>type 由 topic 承载；payload 为强类型 {@link BusPayload} 载荷（device.data.update 携带
 * {@link DeviceDataChangedEvent}，config/integration lifecycle 携带各自事件，all_loaded 携带
 * {@link AllLoadedEvent} 等）；firedAt 为发布时刻（Instant，跨时区）；context 提供全链路溯源。
 *
 * <p>泛型上界 {@code <T extends BusPayload>}：订阅方拿到 {@code BusEvent<DeviceDataChangedEvent>}
 * 时 {@code getPayload()} 直接是强类型载荷，无需 instanceof/强转、无需 Map 解析；编译期保证
 * producer/consumer 共享同一载荷类定义，杜绝 Map 弱类型导致的数据结构漂移。
 *
 * <p>总线传 BusEvent 而非裸 payload：让事件自带 type/时间/uuid/溯源，消费者从统一信封取，
 * payload 仍引用传递保持零拷贝（对齐 Home Assistant EventBus 传原生不可变 Event 的做法）。
 *
 * @param <T> payload 类型，必须是 {@link BusPayload}
 */
public final class BusEvent<T extends BusPayload> {

    private final String type;
    private final T payload;
    private final Instant firedAt;
    private final String uuid;
    private final EventContext context;

    public BusEvent(String type, T payload, Instant firedAt, String uuid, EventContext context) {
        if (type == null || uuid == null || context == null) {
            throw new IllegalArgumentException("type/uuid/context must not be null");
        }
        this.type = type;
        this.payload = payload;
        this.firedAt = firedAt;
        this.uuid = uuid;
        this.context = context;
    }

    /** 构造新事件：firedAt 取当前时刻、uuid 自动生成。 */
    public static <T extends BusPayload> BusEvent<T> of(String type, T payload, EventContext context) {
        return new BusEvent<T>(type, payload, Instant.now(), UUID.randomUUID().toString(), context);
    }

    public String getType() { return type; }
    public T getPayload() { return payload; }
    public Instant getFiredAt() { return firedAt; }
    public String getUuid() { return uuid; }
    public EventContext getContext() { return context; }
}
