/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.CommTrace;

import java.util.List;

import com.ecat.core.Device.RemovalHost;

/**
 * 5 个 IO 库账本的统一查询契约（core 只放接口——接口是契约不是注册中心，账本实现留各库；
 * 各库 registry implements，漏实现编译期暴露）。
 *
 * <p><b>精准语义</b>（全库强制）：只读族按指定身份一对一返回该身份<b>自己占用</b>的资源
 * 注册 Info（串口参数、连接地址、句柄标识……账本持有的是活配置——RECONFIGURE 原位替换），
 * 未注册如实 null，同一身份名下多笔资源属异常形态、明确异常不猜；不做"某集成旗下全部设备"
 * 的罗列查询（消费方是指定设备的精准复用，无罗列需求）。
 *
 * <p><b>命名规范</b>：只读族 get 前缀（对齐 DeviceRegistry.getByUniqueId 纯读先例）；动作
 * 方法 register 动词——register/unregister 是 5 库既有习语，有副作用勿用 get 假只读承诺；
 * 勿用 acquire（撞事务锁术语）。
 *
 * <p><b>身份与生命周期正交</b>：身份 = 字段构造 owner（非宿主派生），生命周期 = 宿主锚点
 * host（onRemove 摘账 + 终态守卫，与 host 收口一视同仁）——{@link #registerForDevice} 的
 * 两个维度参数即此正交的签名化。
 *
 * @param <I> 各库资源信息类型（SerialInfo / ModbusInfo / tcp 连接配置…）
 * @param <S> 各库 source 视图类型（SerialSource / ModbusSource / …）
 * @author coffee
 */
public interface ResourceQuery<I, S> {

    /**
     * 集成本体自持资源信息（如 env-push 推送连接）；不含旗下设备。
     *
     * @return 注册 Info；未注册 null
     */
    I getIntegrationInfo(String coordinate);

    /**
     * entry 自持资源信息。
     *
     * @return 注册 Info；未注册 null
     */
    I getEntryInfo(String coordinate, String entryId);

    /**
     * 设备的通道信息（含 ADAPTER 条目——RTU 设备在 serial 账本亦可得其串口参数）。
     *
     * @return 注册 Info；未注册 null；一名多资源明确异常
     */
    I getDeviceInfo(String coordinate, String entryId, String deviceId);

    /**
     * 消费方一步到位：为设备注册——库内原子完成<b>解析 + 追加登记 + 宿主绑定 + 返回 source
     * 视图</b>，与既有 register(Info, host) 同形同返回，只是解析键从 Info 换成设备身份。
     * 身份（owner 字段构造）与生命周期（host 宿主锚点：onRemove 摘账 + 终态守卫）两维度正交。
     *
     * @return source 视图（消费方直接复用设备 IO 通道发事务，与设备轮询共享同一事务锁）；
     *         设备未注册如实 null，不凭 Info 另开资源（杜绝查注间隙设备已摘的半死挂靠）
     */
    S registerForDevice(String coordinate, String entryId, String deviceId, ResourceOwner owner,
            RemovalHost host);

    /** 资源→使用者全量（ADAPTER 逐设备一条）。 */
    List<ResourceOwner> getDeviceOwners(ResourceRef ref);

    /** 资源→使用者（device 折叠到 entry）。 */
    List<ResourceOwner> getEntryOwners(ResourceRef ref);

    /** 资源→使用者（折叠到集成去重）。 */
    List<ResourceOwner> getIntegrationOwners(ResourceRef ref);
}
