# PeriodicRunner — 传输 SDK 周期链库工具

> 29 号设计 v2（transport-owned execution endgame）库侧落地：「core=业务+库」中的**库**。
> 来源：httpserver SDK S0 穿刺已验证的自持实现抽取（S1 滚动起点），http 回填实证 172 测试零断言改动全绿。

## 定位（是什么 / 不是什么）

**是**：纯代码库类——「MDC 单发 + 完成点重排周期链 + 句柄竞态收口」三原语，供 serial/modbus/tcp/mqtt/http 各传输 SDK 消费，替代每域自抄 ~115 行机制样板（循环骨架 ~50 + 句柄竞态 ~23 + 逐轮 MDC ~25 + 过期即弃判定 ~15）。

**不是**：引擎。本包**零中心运行时**——不注册任何治理、不感知时间、无共享排队点：执行资源要么由消费方 SDK 传入并拥有（`PeriodicRunner` 系），要么挂宿主生命周期自管（`HostedExecutors` 系，池随宿主拆卸），core 不持有任何进程级池账本。「人人需要定时」≠「core 应运行定时」——共性需求给库，领域执行知识（留隙/分块/相位/超时语义）归域。

> 历史：曾授权 `KeySerialExecutor`（键串行共享池）作为本包唯一中心运行时例外；该类已随引擎退役潮删除，全部消费方迁宿主自有道（`HostedExecutors`）或域池视图，例外不复存在——零中心运行时对本包所有类无条件成立。

## API 面

```java
// 纯库类，零中心运行时：不建线程池、不注册治理，执行器由消费 SDK 传入并拥有
PeriodicRunner.on(ScheduledExecutorService owner)   // SDK 自持 STPE 直入
PeriodicRunner.on(ShotScheduler seam)               // 窄单发缝（测试替身/自定义缝直入）

runner.fireAfter(Runnable, long delayMillis)        // MDC 单发（提交捕获/到拍恢复/补 traceId）

PeriodicChain chain = runner.periodic(name, round, schedule);  // 完成点重排周期链
chain.start()                                       //   按策略 firstDelayMillis 排首发；停机 REE 上抛
chain.cancel() / isCancelled() / isRunning()        // trackPending 竞态收口在链内
chain.fireAfter(Runnable, long)                     // 链上中段单发（重试退避）：MDC + track 收口

interface RoundSchedule {                           // 周期策略（域侧实现）
    long firstDelayMillis();                        //   起链：锚定首拍
    FireDecision onFire();                          //   到拍：过期即弃判定（drop(delay) / run()）
    long onSettleRearmMillis();                     //   结算：推进锚点 + 返回延迟
}
```

六个类：`PeriodicRunner`（工厂+单发+建链）、`PeriodicChain`（链体+句柄）、`RoundSchedule`（策略契约）、`FireDecision`（到拍决策值对象）、`ShotScheduler`（单发缝）、`HostedExecutors`（宿主有界执行器工厂）。

另含 `HostedExecutors`（宿主生命周期绑定的有界执行器工厂，应用场景：集成/设备需要自有工作道——调度互斥、阻塞 IO 卸载、per-设备命令背压）：

```java
ExecutorService lane = HostedExecutors.bounded(threads, host);  // 宿主自有池（1=串行单飞道）
lane.execute(body) / lane.submit(callable)                      // 排队界 64，满拒同步抛 REE
// 收尾零接触：工厂内部 host.onRemove(executor::shutdownNow)，宿主拆卸 sweep 统一执行
```

- **宿主绑定**：池挂设备/集成/端点宿主（`bounded(n, this)`），线程数由调用方按自身阻塞/互斥需求定义；共享池必须回答「键怎么输入、键冲突谁守卫、线程数对谁合适」三个中心治理问题，挂回宿主后治理问题消失。
- **线程名派生** `宿主类名@实例哈希-序号`（谁的池/哪个实例零输入可辨），daemon 线程。
- **注册面开放、扫除面收口**：`onRemove` / `bounded` 是注册面；sweep 永远由框架在生命周期 chokepoint 调用，使用者零接触收尾，忘了关池在签名层面不可能。
- **逐任务守卫**：MDC 提交时捕获/执行时恢复；`execute` 路径异常记 warn 续跑不杀 worker，`submit` 路径异常经 future 原样透传。
- **拆卸=shutdownNow（终态过期即弃）**：宿主停止是终态，排队任务即过期工作；与进程停机的优雅排空是有意差异。
- **满拒回执=同步抛 REE**（JDK 有界执行器饱和契约 + serial 域池同型先例 + 调用面已有 REE 捕获分支；布尔回执会把拒绝弱化为可静默忽略的常态返回）。

## 内置语义（消费方免写）

| 语义 | 行为 |
|---|---|
| 逐轮 MDC | 提交时捕获 coordinate、到拍恢复、**每轮新 traceId**（复用 core `TraceContext`）；结算续段恢复**本轮**上下文后取下一拍（完成线程可为任意线程，凭轮内快照还原） |
| 永不注销 | 轮体 begin 同步抛 / 返回 null / CF 异常完成，一律按异常轮结算后照常重排（调度三原则） |
| 单飞 | 在飞期间不排下一拍（结构保证，非锁） |
| 停机（REE） | 续段排拍遇 `RejectedExecutionException` 显式收口（log warn + 链终止，不静默死在 CF 回调里）；`start()` 首发不收口直接上抛（在已停执行器上起链=调用方错误，严格模式） |
| cancel 竞态 | 置停链标志→撤销当前待发单发（`cancel(false)` 不中断在飞）；`trackPending` 与 `cancel` 交错由「先登记后查标志」收口；取消后的到拍/结算一律空操作 |
| 延迟钳位 | `Math.max(0, delay)`（S0 验证实现原语义） |
| start 严格性 | 链不可二次 start（ISE）；`periodic()` 三参数 null 拒绝 |

## 消费方法（SDK 接入）

```java
// 1. SDK 自持执行器（daemon 命名线程，域内定尺寸；参考 HttpSdkTimers 池论证）
ScheduledExecutorService pool = new ScheduledThreadPoolExecutor(2,
        new NamedThreadFactory("ecat-<域>-sched", true));
PeriodicRunner runner = PeriodicRunner.on(pool);

// 2. 轮体：一轮做什么（域语义），返回轮事务 CF
Supplier<CompletableFuture<?>> round = this::pollRound;

// 3. 周期策略：下一拍在哪（域差异），见下节
RoundSchedule schedule = MyGridSchedule.fixedDelay(30, TimeUnit.SECONDS);

// 4. 起链 + 生命周期绑定（RemovalHost sweep 自动停链）
PeriodicChain chain = runner.periodic("dev-1", round, schedule).start();
host.onRemove(chain::cancel);
```

**轮体契约**：返回轮事务 CF，**结算点=下一拍计时起点**；Boolean=业务成功 / 异常完成=传输错误 / begin 同步抛与返回 null 都按异常轮结算——任何终态链都续排。域内记账、熔断、结果回调写在轮体或 CF 续段里（参考 `HttpPolling.beginRound`：单轮超时执法 + RoundOutcome 回调）。

**两种单发不要混**：
- `runner.fireAfter` —— 域内超时执法等**独立**单发（不随链 cancel 撤销，调用方自管 cancel）；
- `chain.fireAfter` —— 链上中段单发（重试退避）：MDC 包装 + **纳入链 cancel 收口**（参考 `HttpPush.onAttemptFailure` 的退避重排）。

## 周期策略（RoundSchedule，域差异参数化）

**时钟与网格锚点封在策略里**，runner 全程不感知时间——域侧自由选时钟域：轮询网格喂单调钟（`System::nanoTime`）、相位族喂墙钟（`System::currentTimeMillis`），测试各自注入假钟（`withNanoClock`/`withWallClock` 形态）。

三事件契约（单飞链上串行调用，无跨线程竞态）：

1. `firstDelayMillis()`：锚定首拍（轮询域惯例 0=首发即发；相位族=到下一未来网格点）；
2. `onFire()`：过期即弃判定——滞后超阈值时把锚点推进到首个未来网格点并返回 `FireDecision.drop(delay)`（网格族公式：`skips = lag / period + 1`），未过期返回 `run()`；
3. `onSettleRearmMillis()`：推进锚点到下一拍并返回延迟（fixedDelay=结算点+period；fixedRate=网格+period 且**跳过在飞跨拍不补跑**——补跑=同任务重入，正是要消灭的形态）。

**参考实现**（httpserver，`com.ecat.integration.HttpServerIntegration.client`）：
- `PollingSchedule` —— 网格族（fixedDelay/fixedRate + 过期即弃公式，名义锚点纳米钟维护）；
- `HttpPush.ChainSchedule` —— 相位族适配（PushPeriod 墙钟四形态：every/每分钟相位/小时 mm:ss/每日 HH:MM:SS，逐轮重对齐、严格未来网格点，无弃拍）。

为什么实现留域侧（而非 core 收编 fixedDelay/fixedRate）：19 号 v2 定稿「定时需求共性≠执行机制共性」，网格知识归域；S1 各域按需自写，出现真实第二/第三实现后再议上收。

## 测试缝三件套（域复制模板）

1. **bind/unbind/reset**：域 SDK 侧持有静态缝（参考 `HttpSdkTimers.bindForTest/unbindForTest/resetForTest`），`PeriodicRunner.on(ShotScheduler)` 可直接喂缝或替身；
2. **捕获 fake**：实现 `ShotScheduler`（单方法 `fireAfter(Runnable,long)`），记录每发（已包装命令/延迟/可取消桩/提交时 MDC 快照），测试线程手动 `fire(i)` 到拍——零后台线程、零真实时钟；
3. **StubFuture**：最小 `ScheduledFuture` 桩，只承载 cancel/isCancelled 语义。

参考：core `PeriodicRunnerTest`（自测）与 http `FakeSdkTimers`（域侧）。测试纪律：禁 `Thread.sleep()` 同步，断言以捕获的调用记录表达。

## 设计取舍记录

- **策略形态 = S0「PushPeriod 形态」的推广**：PushPeriod 是墙钟域无状态延迟式；轮询网格是单调钟域有状态锚点式。两者时钟域不同且后者必须持有锚点，故策略闭包自持时钟+锚点，runner 零时间感知。
- **FireDecision 值对象**：弃拍必须同时给重排延迟（锚点推进后=锚点−now），「布尔判定+二次取延迟」两步缝有推进/读延迟竞态；`drop(delay)` 负值 IAE、`run()` 读延迟 ISE，误用面封死。
- **HttpSdkTimers 保留不撤**：域自持池+测试缝的所有权归域（19 号 v2 铁律），MDC/链骨架委托 core；`runner()` 内 lambda 逐调用解析 seam，bind 替身即时生效。
- **null 轮体按异常轮结算（显式 ISE 点名）**：不得让 NPE 在执行器线程上无声终止链路——永不注销原则优先，错误逐轮可见。

## 落地记录（2026-08-27）

- 抽取来源：httpserver SDK S0 穿刺自持实现（`HttpSdkTimers`/`HttpPolling`/`HttpPush`）五个抽取面（单发+MDC / 循环骨架 / 句柄竞态 / 过期即弃 / 测试缝）。
- http 回填：SDK 内删重复机制 ≈117 行（HttpPolling 385→296、PollingHandle 72→59、PushHandle 85→75；新增域策略 `PollingSchedule` 121 行）；http 公共 API 与行为零变化，**172 测试零断言改动全绿**；hikvision / com-http-anhui-air / com-http-fuyang-air 三消费仓零改编译绿。
- ecat-core 全量 1710 测试绿（含 `PeriodicRunnerTest` 15 用例确定性自测）。
- 后续：S1 滚动 serial（最险：SerialIoPool+同口 FIFO+入站顺序回归）/ modbus / tcp 各域按「消费方法」接入。

## 落地记录（2026-08-28）

- `HostedExecutors` 宿主有界执行器工厂落地：替代原 `KeySerialExecutor` 共享池的「每仓一池=线程爆炸 vs 进程共享池=中心运行时」两难——池挂宿主，core 零进程级池账本。
- `KeySerialExecutor`（359 行 + 测试）与指标注册柜台 `SdkMetrics`（112 行 + 测试）物理删除；原 9 个消费仓的执行道全部迁 `HostedExecutors.bounded`（aticloud/anhui/fuyang/ADM/ASM/hikvision/vision/zeroconf/serial 域池视图），CoreShutdown quiesce 阶段不再有共享池停机动作。
