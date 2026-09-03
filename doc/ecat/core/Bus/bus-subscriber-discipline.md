# ECAT 总线订阅者纪律与扩展路径

> 状态：v1.0（2026-09-02；含 logicdevice 慢订阅者治理落地记录与两项已知扩展路径）

## 概述

总线（`BusRegistry`）是**同步扇出**：`publish` 在发布线程（通常为设备轮询线程）内逐个同步调用订阅者的 `handleEvent`，单个订阅者超过 100ms 触发慢订阅者告警（`ecat.bus.slowSubscriberMs` 可调）。本契约的有意取舍：管道笨、端点聪明——总线不建中心异步层（历史上"2 线程池 + 无界队列"形态已被有意退回，那会引入无界积压与顺序黑洞），**重活订阅者必须自带独占消费线程**。

## 订阅纪律（硬规则）

### 热点 topic：`device.data.update`

该 topic 是设备数据主通道（一轮 `publicAttrsState()` 按属性逐条发布，且挂全系统最多订阅者）。订阅它必须为**入队形态**：

- 继承 `AbstractBusConsumer`（逐条消费）或 `AbstractBatchBusConsumer`（攒批消费），两者共享 `BusConsumerBase` 地基：有界队列 + 独占单消费线程 + **满则丢最旧保新**（时间序列"最新态胜出"语义）+ 慢计时/自观测
- `handleEvent` 内只做轻过滤（payload 类型判别、`instanceof` 设备类型、map 查询）+ `worker.onEvent(...)` 非阻塞入队，µs 级返回
- 集成停机（`onPause`/`onReleaseImpl`）调用 `shutdown()` 停 worker（参考 env-alarm 双停惯例；重走 init 时消费者重建自带新 worker）

### 低频 topic：`integration.lifecycle` / `integrations.all.loaded` / `device.lifecycle` / `notification` 等

轻同步处理（内存索引维护、计数、单行日志）可接受；但**批量建设备/持久态恢复类重活仍应异步或移出总线回调**（一次性阻塞同样会卡住发布线程，只是频次低）。

### 顺序语义（入队形态的关键不变量）

- 每个消费者 = 单队列 + 单消费线程 → **同一发布线程发出的事件端到端 FIFO 保序**（跨多级消费者级联亦然：每跳单队列）
- 因此下游可以安全依赖"同属性到达序 = 因果序"（env-alarm 状态机、env-material 差值计算均依赖此不变量）
- 跨设备/跨线程事件本来就交错（多设备各自轮询线程并发 publish），入队化不引入新的乱序类别；下游一律按**事件自带时间戳**（`AttrState.updateTime`）或**全量重扫当前状态**工作，不依赖到达序
- 队列满丢最旧 = 数据缺口而非错值：聚合类属性下一事件自愈（全量重扫为纯函数），透传类到下一轮询周期补齐

## 案例存档：2026-09-02 logicdevice 慢订阅者治理

**症状**：`device.data.update` 慢订阅者告警（>100ms），发布线程（设备轮询线程）被 `LogicBindConsumer`/`LogicDeviceConsumer`（匿名 $1）长时间占用。

**根因**：两者在发布线程同步执行"聚合重算 + 嵌套 `publicState` 再扇出"。重算本身是 O(n) 内存扫描（快）；墙钟大头是 `updateBindAttrValue` 末尾的内嵌 `publicState()` 在 `handleEvent` 内**再次 publish 整条总线**（N 属性 × 每属性 ≥2 次嵌套 publish × 全部订阅者同步轮询的乘法效应）。注：聚合属性的"第二次 publicState"（外层调用方补的）是 `isValueUpdated=false` 幂等空转，**不构成双重发布**——提交契约见 `ILogicAttribute#updateBindAttrValue` javadoc。

**修复**：两订阅者改为 `AbstractBusConsumer` 入队形态（worker `logic-phy`/`logic-bind`，容量 2000），发布线程只做过滤+入队。下游乱序适应性已逐仓核实（全部按事件时间戳或全量重扫），无阻塞项。

## 已知扩展路径（当前规模不动，触发即升级）

### 观察项 A：per-device 聚合合并重算（O(N²) → O(N)）

一轮 `publicAttrsState()`（N 属性）触发聚合属性（alarm_status/running_status）重扫 N 次、每次全扫 N 属性。入队化后不占发布线程，但 worker 侧 CPU 浪费仍在。

- **触发条件**：system_health 中 `logic-phy`/`logic-bind` worker 队列深度持续积压或处理计数贴近容量
- **升级形态**：worker 消费时 drain 队列，将**同设备**待处理事件合并——值传播仍逐条（保 SSE/持久化语义），聚合属性只对末态做一次重扫

### 扩展路径 B：per-device 分区并行（跨设备解耦）

现状每个消费者 = 单队列单线程：设备 A 的慢事件串行阻塞排在后面的设备 B 事件（跨设备单飞）。几十台设备、毫秒级事件下无感。

- **触发条件**：设备规模或单事件处理时长使单队列 FIFO 可观测地拖慢无关设备（队列深度告警 + 单设备事件簇耗时分析）
- **升级形态**：按 `deviceId` 哈希到 N 个 worker（Kafka partition-by-key 同构），同设备保序、跨设备并行；`drop-oldest` 反压按分区独立实现
- **顺序**：先做观察项 A——合并重算消除的不必要负载可能直接消除瓶颈

## 防呆（建议，未实施）

热 topic 订阅纪律目前靠自觉（上述案例的成因）。建议机器执法：ArchUnit 规则或总线收口 API——`device.data.update` 的订阅者必须为 `BusConsumerBase` 系。规则仅圈定该热点 topic；热点 topic 上唯一非入队存量订阅 `ecat-core-bus-recorder`（µs 级内存环形缓冲 + fast-path，诊断用途）白名单豁免。

## 关联

- `BusConsumerBase`/`AbstractBusConsumer`/`AbstractBatchBusConsumer`（`com.ecat.core.Bus.consumer`）——入队形态地基与 MDC 因果链续传
- 入队形态参考实现：env-alarm `DataEventConsumer`、logicdevice `LogicBindConsumer`/`LogicDeviceConsumer`
- 基准对照：`arch-review-20260815/13-bus-hotpath-bench.md`（临时工作目录，历史基准数据）
