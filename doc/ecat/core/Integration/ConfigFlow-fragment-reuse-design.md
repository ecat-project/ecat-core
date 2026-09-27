# ConfigFlow 子 flow 复用设计（registerFlowStep）

> 状态：**P1/P2/P3 已实施**（2026-09-26：P1=core 三件 + 单测、modbus 两件套、rkonfly 试点；P2=serial 两件套 + gassensor 试点，serial 318 / gassensor 44 测试全绿；P3=tcp 两件套 + 常量上提（tcp 篇 §3.1）、试点 com-hj212-sensor 收编——排重迁宿主尾步 null 首屏，tcp 256 / hj212-sensor 14 测试全绿）。决策点已全部裁定，见 §11；五类 io 域结论与各域独立成篇见 §7；本文全部机制假设已对源码逐条核验，证据见 §2/§10。
> 关联现行机制文档：本目录 `ConfigFlow-development.md`、`ConfigFlow-usage.md`；运行时资源归属见 `../CommTrace/io-resource-owner.md`。

## 1. 目标与收益

解决的问题：ConfigFlow 的复用目前只到 schema 级（共享 `ConfigSchema` 类），步骤逻辑（协议选择、按协议分流表单、校验、`comm_settings` 落盘、设备侧解析）在每个设备集成手抄一遍。io 能力一旦扩面（如新增传输模式），全部消费仓要逐仓改 flow 结构。

**唯一验收口径：io 库（modbus/serial/tcp）演进——加协议、加步骤、改步骤、改 stepId——使用它的设备集成仓零修改。**

| 受益者 | 现状成本 | 改造后 |
|---|---|---|
| ModbusRtu 系 20 设备仓 | 三件套 handler ~38 行/仓 + 设备侧解析 ~100 行/仓 | flow 侧 1 行挂载 + 设备侧 2 行 |
| 纯串口 13 仓 | 12~94 行/仓 + 解析 ~40 行/仓 | 同上 |
| 未来 io 能力扩面 | 每仓改 flow 结构 + 设备 + pom | 改 io 库一处，全部采纳仓 flow 零改 |
| serial 域 TCP 承载演进（未来，§7.1） | 每仓改 flow/设备/pom | serial 库内加通道选择，11 仓零修改——首个真实受益场景 |
| io 库（modbus/serial/tcp） | 只导出 schema，flow 逻辑散落 37 仓 | 步骤逻辑收归库内，与 schema 同源演进 |

可验证收益（P1 验收口径）：试点仓 flow 源文件行数下降、`ecat-device-test` 六阶段全绿、存量 entry reconfigure 行为不变。

## 2. 现状与证据

### 2.1 复用现状

- **框架无 flow 组合机制**：步骤路由硬编码在各 handler 返回值（`showForm("next_step_id", ...)`）；`coordinate↔flow` 严格一对一（`ConfigFlowRegistry.registerFlow` 单值 put）；`registerStep`/`showForm`/`createEntry` 全为 protected，仅 flow 子类可用；`entryData` 是全局扁平 Map，无命名空间。
- **schema 级复用已是现实**：`SerialCommConfigSchema`（serial 库）、`ModbusCommTypeSchema`/`ModbusRtuCommConfigSchema`/`ModbusTcpCommConfigSchema`（modbus 库）被 40+ 仓以 `provided` pom + `ecat-config.yml` 双声明消费。
- **步骤逻辑零复用**：`stepProtocolSelect + stepCommConfig + createCommConfigSchema` 三件套在 20 仓各持一份手抄拷贝（rkonfly 为 epever 逐行 clone）。
- **设备侧解析同样重复**：`comm_settings` 逐 key 解析 ~100 行 × 21 份拷贝分布在 20 仓。
- **方言漂移的历史代价**：sailhero 设备侧带 3 套字段名兼容垫片（`data_bits`/`databits`/`numdatabit`）；zhaorong、tianhong 各持一套自造 key 方言；usr 用 `io_comm_settings` 特例 key；wmade 偿还 `tcp_comm_settings`→`comm_settings` 旧形状债。
- **RTU-over-TCP 能力已存在**：`ModbusTcpCommConfigSchema` 已有 `tcp_protocol` 字段（TCP/RTU_OVER_TCP），`ModbusMasterFactory.createTcpMaster` 走 `setEncapsulated(true)` 同一入口。缺的不是通讯通道，是 flow 层复用机制。
- **爆炸半径实测**：共享串口 schema 引用并集 40 仓（37 设备仓）；纯串口仓 13 + 手写方言仓 2 是未来扩面的真实改造目标；ModbusRtu 系 20 仓因 TCP 分支已含 RTU_OVER_TCP 近零成本。

### 2.2 本设计依赖的框架机械事实（已逐条对源码核验）

| # | 事实 | 证据（file:line） |
|---|---|---|
| F1 | `stepDefinitions` 是 `protected final Map<String, StepDefinition>`（LinkedHashMap，插入序=注册序） | `AbstractConfigFlow.java:98` |
| F2 | `StepDefinition` 是 AbstractConfigFlow 的 `@Data @AllArgsConstructor protected static class`，仅含 `handler`/`stepInfo` 两个 final 字段——同类内可 `new StepDefinition(fn, info)` 构造、可 `getHandler()`/`getStepInfo()` | `AbstractConfigFlow.java:900-912` |
| F3 | Java 同类实例可互访 private/protected 字段——宿主方法内可直接读写 `sub.stepDefinitions`/`sub.context` | Java 语言规范 6.6.1 |
| F4 | `setContext`/`getContext` 是 public | `AbstractConfigFlow.java:561-572` |
| F5 | `handleStep` 是 public；`userInput` 为 null/空时跳过 `saveStepData`；未知 stepId 返回 abort 结果 | `AbstractConfigFlow.java:665-700` |
| F6 | `showForm` protected 实例方法在**调用时**读 `this.context` 传给 public static 工厂——子 flow 实例被注入宿主 context 后，其 showForm 即落宿主 context | `AbstractConfigFlow.java:600-605`、`ConfigFlowResult.java:118-121` |
| F7 | 每次 startFlow/reconfigure `createFlow` 都 `getDeclaredConstructor().newInstance()` **新建 flow 实例**——宿主构造器（含子 flow 挂载）每会话重跑；context 会话内同一对象，service 只原地 `setCoordinate/setConfig/setStepInputs`，从不 swap | `ConfigFlowRegistry.java:150-162`、`ConfigFlowService.java:290-301,438-449` |
| F8 | core 启动 flow 与 goPrevious 的原生习语就是 `handleStep(stepId, null)`（null 输入=显示该步，靠 handler 首分支从 context 回显） | `ConfigFlowService.java:304`、`ConfigFlowRegistry.java:399-406` |
| F9 | `applyResult` 只认 CREATE_ENTRY/REMOVE_ENTRY/ABORT 三类终态，其余（SHOW_FORM 等）no-op 留 active | `ConfigFlowService.java:226-240` |
| F10 | reconfigure 恢复：`context.setStepInputs(copy(entry.getStepInputs()))` 按 stepId 对齐——stepId 不变则漫游不变 | `ConfigFlowService.java:443-445` |
| F11 | `ConfigFlowResult` 字段全 final（结果不可变），工厂全 public static | `ConfigFlowResult.java:61-107` |
| F12 | handleStep 的 SHOW_FORM 后处理：displayStepId≠currentStep 时更新 currentStep/context/stepHistory；相等则 no-op | `AbstractConfigFlow.java:686-696` |
| F13 | `registerStepUser`/`registerStepReconfigure`（:236/:257）与 `createEntry()`（:616）均 protected 非 final、可覆写——结构禁令可用覆写实现 | `AbstractConfigFlow.java:236,257,616` |
| F14 | `sourceType` 是 flow 实例字段（:105，默认 USER，service 只对宿主 setSourceType）——子 flow 需包装器逐调用同步；`flowId` 在 FlowContext 上（final，getFlowId 委托 :184-185）——随 context 同步自动正确 | `AbstractConfigFlow.java:105,184-185`、`FlowContext.java:41` |

## 3. 机制总览：flow 组合 flow

```
┌─ ecat-core（+1 子类 +宿主 2 方法 +1 枚举值，见 §4）────────────────┐
│ AbstractSubConfigFlow —— 子 flow 基类（入口声明/出口/结构禁令）       │
│ AbstractConfigFlow.registerFlowStep(sub, tailStepId) → entryStepId  │
│   把 sub 的全部步骤搬进宿主步骤表（逐个包一层翻译器）                │
│   出口信号 SUBFLOW_COMPLETE → 宿主 handleStep(tailStepId, null)     │
└────────────────────────────────────────────────────┬──────────────┘
                                                     │ 依赖（既有 pom provided + ecat-config.yml）
┌─ io 库（modbus / serial / tcp）────────────────────▼──────────────┐
│ 导出两件套：                                                       │
│  ① XxxCommFlow extends AbstractSubConfigFlow —— 子 flow（写端）     │
│     入口声明/出口信号/结构禁令收在子 flow 基类（§4.2）              │
│  ② XxxCommSettings.parse(entryData) → Info —— 类型化读端           │
│ 与既有 ConfigSchema 同仓同源演进                                    │
└────────────────────────────────────────────────────┬──────────────┘
                                                     │ 宿主 1 行挂载 + 1 行进入 + 2 行读端
┌─ 设备集成（37 仓，增量采纳）────────────────────────▼──────────────┐
│ 宿主 flow 只保留设备特有步骤（型号/量程/确认）；                     │
│ 通讯步骤整体向 io 库借，库内演进宿主零感知                           │
└────────────────────────────────────────────────────────────────────┘
```

核心抽象（用户两轮裁定演化，2026-09-21 定形）：**flow 与 sub flow 都是 flow 家族**——子 flow 仍是 `AbstractConfigFlow` 的子类（共用同一套步骤注册/showForm/handleStep 机制，不降低到「片段」档次）；组合差异收口在新基类 `AbstractSubConfigFlow`：入口由子 flow 显式声明（`registerStepEntry`，不靠注册顺序隐式约定）、出口 `subFlowComplete()`、结构禁令（user/reconfigure/createEntry 属宿主）。宿主侧主类只加 `registerFlowStep` 挂载方法——两侧逻辑互不污染。

三个组合语义（与既有机制逐一同构，无新概念）：

| 组合语义 | 机制 | 既有同构 |
|---|---|---|
| 进入子 flow | 宿主上一步 handler `return handleStep(entryStepId, null)`（entryStepId=子 flow registerStepEntry 声明值，挂载返回） | core 启动 flow `handleStep(startStepId(), null)`（F8） |
| 子 flow 内部流转 | 步骤表按 stepId 调度，子 flow 自己 showForm 路由 | 完全不变（步骤已进宿主表，core 视角无差别） |
| 退出子 flow | 尾步 `subFlowComplete()` → 包装器翻译为 `handleStep(tailStepId, null)` 再驱动宿主尾步 | goPrevious 再驱动（F8） |

## 4. core 落地代码（AbstractSubConfigFlow 新类 + AbstractConfigFlow 两方法 + ConfigFlowResult 枚举值）

类职责隔离（用户裁定）：子 flow 侧全部新逻辑（入口声明、出口信号、结构禁令）收进新类 `AbstractSubConfigFlow`；主类 `AbstractConfigFlow` 只加挂载方法 `registerFlowStep` 与私有包装器，**无任何字段/守卫污染**。

mount 方法必须留在宿主侧的硬约束：Java protected 访问规则（JLS 6.6.2）不允许子类代码经超类类型引用读写另一实例的 protected 成员——`sub.mountInto(host)` 里摸 `host.stepDefinitions` 编译不过；反向（宿主方法内摸 `sub.stepDefinitions`，该字段声明在两者的公共超类 AbstractConfigFlow）合法。

### 4.1 ConfigFlowResult：+1 枚举值 +1 工厂

```java
public enum ResultType {
    SHOW_FORM,
    CREATE_ENTRY,
    ABORT,
    REMOVE_ENTRY,
    /** 子 flow 完成：仅由 AbstractConfigFlow.registerFlowStep 的包装器翻译为
     *  handleStep(tailStepId, null)，不允许逃逸到驱动层（service/controller 永远看不到它）。 */
    SUBFLOW_COMPLETE
}

/** 子 flow 出口信号。构造后字段全 null——它是纯控制信号，不携带数据。 */
public static ConfigFlowResult subFlowComplete() {
    return new ConfigFlowResult(ResultType.SUBFLOW_COMPLETE, null, null, null, null, null, null);
}
```

 containment 论证：SUBFLOW_COMPLETE 只能被子 flow handler 产生，而子 flow 的每个 handler 都被包装器包过（§4.2 循环搬入**全部**步骤），包装器在返回前完成翻译——结构上不可能逃逸。防御纵深：即使未来某路径泄漏，`applyResult` 对非 CREATE/REMOVE/ABORT 一律 no-op（F9），FlowDriver 等消费方的 while(SHOW_FORM) 循环会以「意外结束」fail-loud，不会误持久化。

### 4.2 AbstractSubConfigFlow（新类，同包 com.ecat.core.ConfigFlow）

```java
package com.ecat.core.ConfigFlow;

/**
 * 子 flow 基类：供设备集成宿主以 {@code registerFlowStep} 挂载的流程步骤组。
 *
 * <p>与普通 flow 的全部差异都在本类收口（主类 AbstractConfigFlow 只保留挂载方法，互不污染）：
 * <ul>
 *   <li>入口步显式声明：构造器内调用 {@link #registerStepEntry}（且仅一次）。挂载方拿到的是
 *       声明值——不是「注册顺序的第一步」这种隐式约定（注册顺序≠流转顺序，靠顺序定入口是脆弱契约）。</li>
 *   <li>出口信号：尾步 return {@link #subFlowComplete()}。</li>
 *   <li>结构禁令（构造期即抛，早于挂载）：user/reconfigure 入口角色与 createEntry 属宿主——
 *       子 flow 是步骤组，不是独立流程。</li>
 * </ul>
 */
public abstract class AbstractSubConfigFlow extends AbstractConfigFlow {

    /** 入口步 stepId（registerStepEntry 声明；包私有供同包的 registerFlowStep 读取）。 */
    String entryStepId;

    /** 防重复挂载（一个实例只允许挂进一个宿主）。 */
    boolean mounted;

    /**
     * 注册并声明入口步：挂载后宿主从这一步进入子 flow。
     * 与 registerStepUser/registerStepReconfigure 同族——角色在注册时标记。
     */
    protected void registerStepEntry(String stepId, Function<Map<String, Object>, ConfigFlowResult> handler,
                                     String displayName) {
        registerStep(stepId, handler, displayName);
        if (entryStepId != null) {
            throw new IllegalStateException("入口步只能声明一次: " + stepId);
        }
        entryStepId = stepId;
    }

    /** 子 flow 出口：本子 flow 步骤已全部完成，控制权交回宿主尾步（挂载时由宿主指定）。 */
    protected final ConfigFlowResult subFlowComplete() {
        return ConfigFlowResult.subFlowComplete();
    }

    // ========== 结构禁令：以下能力属宿主，子 flow 构造期即拦 ==========

    @Override
    protected void registerStepUser(String stepId, String displayName,
                                    BiFunction<Map<String, Object>, FlowContext, ConfigFlowResult> handler) {
        throw new IllegalStateException("子 flow 不允许声明 user 入口步（入口角色属宿主）");
    }

    @Override
    protected void registerStepReconfigure(String stepId, String displayName,
                                           BiFunction<Map<String, Object>, FlowContext, ConfigFlowResult> handler) {
        throw new IllegalStateException("子 flow 不允许声明 reconfigure 入口步（入口角色属宿主）");
    }

    @Override
    protected ConfigFlowResult createEntry() {
        throw new IllegalStateException("子 flow 不允许创建 entry——出口请 return subFlowComplete()");
    }
}
```

禁令依据（已核验可覆写）：`registerStepUser`/`registerStepReconfigure`（`AbstractConfigFlow.java:236/:257`，protected 非 final，签名 `(String, String, BiFunction<Map,FlowContext,ConfigFlowResult>)`）、`createEntry()`（:616，protected 非 final 无参）。`registerStepDiscovery` 是 protected final 无法覆写——子 flow 上注册 discovery 步会成为死代码（registry 只驱动宿主），无危害不另设禁令。

守卫边界（诚实声明）：`context.setEntryUniqueId(...)` 是 FlowContext 的 public 方法，类型禁令拦不住（identity 步骤属宿主是纪律约定，非机械强制；子 flow 没有身份语义，正常写法碰不到它）。

### 4.3 AbstractConfigFlow 主类：只加两个方法（无字段、不改既有方法）

```java
/**
 * 把一个子 flow 的全部步骤挂载进本 flow（宿主）。
 *
 * <p>组合契约：
 * <ul>
 *   <li>sub 须为 AbstractSubConfigFlow 子类——入口已显式声明（registerStepEntry）、
 *       结构禁令已在构造期生效，本方法不再重复校验。</li>
 *   <li>子 flow 步骤的落盘直接写进宿主 context（挂载时注入 + 包装器每次调用前同步）。</li>
 *   <li>子 flow 尾步 return subFlowComplete() → 自动翻译为 handleStep(tailStepId, null)，
 *       即再驱动宿主尾步（tail 步须能以 null 输入回显——与 goPrevious 同一惯例）。</li>
 * </ul>
 *
 * @param sub        子 flow 实例（每个实例只允许挂载一次；不允许挂载自身）
 * @param tailStepId 宿主侧尾步 stepId——子 flow 完成后落到哪一步（须已注册）
 * @return 子 flow 入口步 stepId（sub 构造器 registerStepEntry 的声明值），宿主用它进入子 flow
 * @throws IllegalStateException 校验失败（见各分支消息）
 */
public String registerFlowStep(AbstractSubConfigFlow sub, String tailStepId) {
    if (sub == null || sub == this) {
        throw new IllegalArgumentException("sub flow 不能为 null 或自身");
    }
    if (sub.mounted) {
        throw new IllegalStateException("该子 flow 已被挂载（一个实例只允许挂进一个宿主）: "
                + sub.getClass().getSimpleName());
    }
    if (sub.entryStepId == null) {
        throw new IllegalStateException("子 flow 未声明入口步——构造器内须调用 registerStepEntry: "
                + sub.getClass().getSimpleName());
    }
    if (tailStepId == null || !stepDefinitions.containsKey(tailStepId)) {
        throw new IllegalStateException("尾步尚未注册——请先注册宿主自己的步骤，再挂载子 flow: " + tailStepId);
    }
    for (Map.Entry<String, StepDefinition> e : sub.stepDefinitions.entrySet()) {
        if (stepDefinitions.containsKey(e.getKey())) {
            throw new IllegalStateException("stepId 与宿主已有步骤冲突: " + e.getKey());
        }
        stepDefinitions.put(e.getKey(), wrapSubStep(sub, e.getValue(), tailStepId));
    }
    sub.mounted = true;
    sub.setContext(context);   // 注入宿主 context（包装器每次调用前还会同步一次，双保险）
    return sub.entryStepId;
}

/**
 * 子 flow 步骤包装器：调用前同步 context；返回时翻译出口信号。
 * SUBFLOW_COMPLETE → handleStep(tailStepId, null)（再驱动宿主尾步，与 goPrevious 同习语）。
 */
private StepDefinition wrapSubStep(AbstractSubConfigFlow sub, StepDefinition def, String tailStepId) {
    return new StepDefinition(input -> {
        sub.setContext(context);           // 逐调用同步：即使未来出现 context 交换也保持正确
        sub.setSourceType(getSourceType()); // 同步会话模式：service 的 setSourceType 只打在宿主实例上，
                                            // 不同步则子 flow 的 getSourceType() 在 reconfigure 下错误返回 USER
        ConfigFlowResult result = def.getHandler().apply(input);
        if (result.getType() == ConfigFlowResult.ResultType.SUBFLOW_COMPLETE) {
            return handleStep(tailStepId, null);
        }
        return result;
    }, def.getStepInfo());
}
```

子 flow handler 的合法依赖面（同步面声明）：`context` 全部内容（entryData/stepInputs/entryUniqueId/entryTitle/flowId——flowId 存在 FlowContext 上，随 context 同步自动正确）、`getSourceType()`。`currentStep`/`stepHistory` 是宿主私有调度状态，子 flow handler 不得依赖（读到的也是子实例的陈旧值）。

入口定义权的裁定依据（谁的东西谁命名）：尾步是宿主自己的步骤，宿主命名（不泄漏库内部）；入口是子 flow 自己的步骤，由子 flow 显式声明——宿主代码不出现子 flow 的 stepId 字面量，io 库改名/前插步骤/条件裁剪入口（fixedProtocol 构造选项少注册一步、入口声明自动前移）时宿主零修改。宿主对子 flow 的一切定制（跳过协议选择、schema 默认值预填）都经**子 flow 构造参数**表达（builder 透传，D6/§11），不经点名内部步骤、不摸内部 schema 结构。

### 4.4 重入安全性（包装器嵌套调用 handleStep 的状态收敛）

出口翻译是 `handleStep` 内嵌 `handleStep`。以 rkonfly「提交 comm_config」逐行核对状态（handleStep 体 `AbstractConfigFlow.java:665-700`）：

| 时刻 | 动作 | currentStep | stepHistory | 备注 |
|---|---|---|---|---|
| t1 | service.drive → 宿主 handleStep("comm_config", input) 入口 | comm_config | +comm_config | saveStepData 存 step_inputs |
| t2 | 包装 handler 运行，子 flow 校验+落盘，返回 SUBFLOW_COMPLETE | comm_config | 同上 | |
| t3 | 包装器调 handleStep("final_confirm", null)（内层）入口 | final_confirm | +final_confirm | null 输入跳过 saveStepData（F5） |
| t4 | 宿主 stepFinalConfirm(null) → isEmpty 分支回显 | final_confirm | 同上 | 从 context 回显（漫游数据在） |
| t5 | 内层 SHOW_FORM 后处理：displayStepId==currentStep | final_confirm | 同上 | no-op（F12） |
| t6 | 外层 SHOW_FORM 后处理：displayStepId==currentStep | final_confirm | 同上 | no-op（F12）✓ 收敛 |

与手写宿主（stepCommConfig 直接 `showForm("final_confirm",...)`）终态完全一致：手写路径靠外层后处理把 final_confirm 追加进 history（F12），嵌套路径靠内层入口追加——**同一终态，同一 history**。goPrevious 从 final_confirm 回 comm_config 的行为不变。

异常路径：内层 handleStep 抛出的 RuntimeException 沿外层 `definition.getHandler().apply()` 逐帧上抛至 service.drive 统一异常策略（`ConfigFlowService.java:215-217`），无吞没。

## 5./6. modbus 两件套与 rkonfly 代表仓全景（已迁出独立成篇）

modbus 是首落库域兼模板域：ModbusCommFlow / ModbusCommSettings 两件套全文、D6 Builder 定制形态、rkonfly 六件全景（零改动四件 + 改造两件 + 五处/四处 diff + 九跳 settings 数据流表 + 走读表 + 零修改验证表）、收编行为差异——已整体迁至 workspace `docs/2026-09-22-io-subflow-modbus-design.md`。

serial / tcp / mqtt / http 各域设计与代表仓改造细节同样独立成篇（见 §7 结论表）；各篇代表仓改造逐仓套用 modbus 篇 §2 模板。本文 §8-§12 引用的 §5/§6 证据（rkonfly:120,138 形状比对、六阶段验收口径等）以 modbus 篇为准。

## 7. 五类 io 域落地结论（各域设计已独立成篇）

调研覆盖 modbus/serial/tcp/mqtt/http 五类 io 域，结论三落两缓；各域两件套与代表仓改造细节见 workspace `docs/` 四篇（2026-09-22）：

| 域 | 结论 | 代表仓 | 篇 |
|---|---|---|---|
| modbus | 落库（模板域：协议选择步 + 分流配置步） | rkonfly | `2026-09-22-io-subflow-modbus-design.md` |
| serial | 落库（单步收编；TCP 承载留库内演进，§7.1） | gassensor | `2026-09-22-io-subflow-serial-design.md` |
| tcp client | 落库（单步；parse 返回 TcpConfig，不新造 TcpInfo） | com-hj212-sensor | `2026-09-22-io-subflow-tcp-design.md` |
| tcp server | 暂缓（TcpServerCommConfigSchema 零消费仓；触发=第二个 server 型仓且形状趋同） | — | tcp 篇 §6 |
| mqtt | 不纳入（嵌入式 Broker 单例 + registerClient 零连接参数，无配置收集面） | — | `2026-09-22-io-subflow-mqtt-design.md` |
| http | 不纳入（拉取型 2 仓形状互斥，主流形状不存在；触发=第 2-3 个拉取型仓且形状趋同） | — | `2026-09-22-io-subflow-http-design.md` |

serial/tcp 两件套与 modbus 的机制差异只有一点：单步形态（无协议选择步——serial 域当前单通道、tcp client 无分流）；挂载/出口/parse/D6 builder 契约完全同构（各篇 §2/§3）。同批正交推进的轮询间隔线（物理设备采集间隔可配置）单列 `2026-09-22-polling-interval-config-design.md`，仅代表仓同批落地。

### 7.1 serial 通道承载演进（TCP over serial，2026-09-22 用户裁定方向）

serial 域是本设计的原始动机场景。未来 TCP 承载（设备经 DTU/串口服务器远程接入）的演进**全部发生在 serial 库内**：SerialCommFlow 加通道选择步（参考 modbus 协议选择形态）→ 按通道分流配置步；客户端 API 不变——`register(info, this)` 拿统一 `SerialSource`，通道差异由 serial 库内部封装（tcp=连接管理+帧透传，帧协议层不动）；底层可复用 tcp 库 `TcpConnection`（库间依赖，形态同 modbus→serial 的 schema 依赖）。落盘演进=顶层 `connection_type` 缺省 serial（旧 entry 无缝兼容）+ comm_settings 按通道分流，**serial 分支保持现行扁平 7 字段不动**（11 仓零迁移）。**本次只收编单通道现状**（时序裁定），不预造分支——flow 有选项而运行时不支持=半成品；触发条件=真实 TCP 承载设备需求（serial 篇 §5）。

> 早期候选路径「纯串口仓升级 rtu-over-tcp = 换挂 ModbusCommFlow 三处各一行」**已废弃**（2026-09-22 用户否决）：serial 域设备是私有帧协议（NMEA/厂商 ASCII 帧），modbus 运行时（功能码/寄存器语义）不适用；且换挂=逐仓改三处，恰恰违背唯一验收口径。取舍记录见 §12。

## 8. 兼容性矩阵

| 兼容面 | 影响 | 依据 |
|---|---|---|
| 存量 entry.data | 零迁移（子 flow 落盘形状=现行主流形状，modbus 篇 §1.1 逐字承接） | rkonfly:120,138 原文比对 |
| reconfigure step_inputs 漫游 | 无影响（stepId 不变：protocol_select/comm_config 原样进宿主表） | F10 + 匿名挂载 |
| ADM FlowDriver（env-air-device-manager） | 无影响（identityInputs 按 stepId 键控身份步；停步处仍是 comm_config，schema 面不变） | FlowDriver 快进只认 SHOW_FORM/CREATE_ENTRY/ABORT，SUBFLOW_COMPLETE 不出包装器 |
| 前端 / REST 协议 | 无影响（步骤仍以平铺 stepId 呈现在同一 flow 内；进入/退出都在服务端完成，前端无感） | modbus 篇 §2.5 走读 |
| 未采纳仓 | 无影响（纯增量采纳，不强制；core 新增方法无人调用则零行为变化） | §4 全部为新增代码路径 |
| i18n | 自动跟随（schema i18n namespace 随定义方，子 flow 用的就是库内 schema） | 既有机制 |
| ResultType 消费方 | 无感知（SUBFLOW_COMPLETE 结构上不出包装器；泄漏时 fail-loud 而非误持久化） | F9 + §4.1 论证 |

## 9. 落地分期（单战役三里程碑，2026-09-22 裁定）

三代表仓一个战役一次做完，P1/P2/P3 作里程碑**不作停点**（用户明令）；每个里程碑完成即是一个可验收的完整增量。

| 里程碑 | 内容 | 验收 |
|---|---|---|
| P1 | core 三件（§4：AbstractSubConfigFlow 新类 + AbstractConfigFlow 两方法 + ConfigFlowResult 枚举值）+ 单测（重入走读 §4.4 逐断言、出口翻译、结构禁令、挂载校验）；modbus：ModbusCommFlow（含 D6 builder 定制形态，一次落齐）+ ModbusCommSettings；试点 rkonfly（+`poll_interval_sec`，3 设备默认 5s） | core 新增单测绿；rkonfly flow -38 行/设备侧 -100 行；`ecat-device-test` 六阶段全绿；存量 entry reconfigure 不变 |
| P2 | serial：SerialCommFlow + SerialCommSettings；试点 gassensor（+`poll_interval_sec` 默认 5s；行为差异三点见 serial 篇 §4） | 同上 |
| P3 | tcp：TcpClientCommFlow + TcpCommSettings；试点 com-hj212-sensor（排重迁宿主尾步首屏；行为差异两点见 tcp 篇 §5） | 同上 |
| P4（可选，后续批） | 多子 flow 并存挂载（前缀命名空间，saimosen）；方言仓（zhaorong/tianhong/sailhero）收编；teledyne/wmade 等第二批仓 | 见 §10 |

轮询间隔线（`2026-09-22-polling-interval-config-design.md`）与子 flow 线正交，仅代表仓同批落地（P1-P3 内联）；~45 轮询型仓批量推进另批。

P1 的 core 单测清单（重入收敛是最高风险点，先锁死）：
1. 挂载后提交子 flow 末步 → 返回 SHOW_FORM(尾步) 且 currentStep/stepHistory 与手写路由等价（§4.4 表逐项断言）。
2. 子 flow 中途步骤校验失败 → SHOW_FORM(子 flow 步) 带错误，正常回显。
3. 子 flow 步骤落盘进宿主 context.entryData / stepInputs。
4. 挂载校验四连：null/自身、二次挂载、未声明入口步（registerStepEntry 缺失）、尾步未注册、stepId 冲突——各抛 IllegalStateException。
5. 结构禁令（构造期）：AbstractSubConfigFlow 子类调 registerStepUser/registerStepReconfigure/createEntry → 各抛。
6. reconfigure 往返：挂载 flow 的 startReconfigureFlow → 子 flow 步预填回显与改造前基线一致。
7. D6 定制形态：builder 挂载（如 rtu 注入 timeout(2000) 的 serial schema）→ comm_config 表单预填=定制值、提交落盘带定制值；无参挂载 → 预填=库标准。

## 10. 诚实边界

**已核验**（§2.2 表 + §4/§5 全部代码行号证据）：机制可行性、重入收敛、漫游兼容、形状零迁移、真实 API 拼装。
**收编行为差异（各代表仓试点回归的盯点，逐仓清单见各篇）**：rkonfly 两点——① RTU timeout 缺省：现行手抄默认 2000（`RkonflyDeviceBase.java:110`），parse 改为与 serial 库 schema 预填同源的 `Const.READ_TIMEOUT_MS`=500；仅影响缺 timeout 字段的存量 entry（经表单创建的 entry 均带预填值，不受影响），2000 属方言漂移，随收编消亡。② parity/stop_bits 未知值：现行兜底 NONE/ONE_STOP_BIT（`RkonflyDeviceBase.java:132-144`），parse 严格抛 IllegalArgumentException；schema 枚举校验使表单路径不可能产生未知值，仅手工构造/导入 entry 可能触发（fail-loud 优于静默兜底）。gassensor 三点（timeout/flow_control 从沉默丢弃变真实生效、枚举未知值收紧，serial 篇 §4）；com-hj212-sensor 两点（缺字段兜底换轨库标准、排重时点后移一屏，tcp 篇 §5）。
**已核验（2026-09-22 全仓只读排查）**：SUBFLOW_COMPLETE 枚举值对既有代码零破坏面——main 侧全部消费点（ConfigFlowService/AbstractConfigFlow、adm+station 两个 FlowDriver、env-push-manager 两 controller、air/station binding service、zeroconf、test-discovery 驱动）均为等值判断或带 default 的 switch（ecat-core-api 测试桩同）；无穷尽 switch、无 `ResultType.values()` 遍历、无枚举序列化面（REST 出口走 SubmitResult 字符串字面量）。FlowDriver 快进环的「未列举值兜底 throw」恰为泄漏防线（fail-loud，§8 设计意图）。**未核验**（P1 实施时首批确认）：core 三件改动的编译与全 reactor 回归——本文代码是逐行对照源码写的可编译意图，尚未过 mvnd。
**固有成本（架构无法消除）**：serial 域 TCP 承载曾提「换挂 modbus 三处各一行」压缩路径，已废弃（§7.1）——正确架构是 serial 库内通道承载演进、采纳仓零改；设备运行时统一 CommSource 抽象不在本设计范围（独立议题，D4）。
**P4 前缀挂载的已知代价**：多子 flow 并存（saimosen 双库）需 `registerFlowStep(mountId, sub, tail)` 前缀重载——包装器内把子 flow 返回的 SHOW_FORM stepId 重写为 `mountId_stepId`（子 flow id 集合已知，重写封闭）；存量 entry 的 unprefixed step_inputs 漫游漂移需数据适配。saimosen 已手写完双协议、痛感最低，列晚期可选。
**宿主定制与读端兜底的边界（D6 裁定，2026-09-21）**：定制只影响写端表单预填；读端 parse 的缺字段兜底恒为库标准默认。两者只在 entry 缺该字段的非常规路径（手工构造/异构导入）才会分叉——表单路径提交整表状态、预填随提交落盘（FlowDriver 自动快进同样显式提交 schema 默认值），正常 entry 永远带着定制值，读端兜底不参与取值。
**身份纪律**：identity 步骤（sn/uniqueId 排重）属宿主，子 flow 不碰——机械守卫只盖 createEntry（§4.2 诚实声明），setEntryUniqueId 靠纪律。依赖 comm 字段生成 uniqueId 的仓（如 hj212 的 host+mn），收编后 comm 步属子 flow，排重挂**宿主尾步 null 首屏**（子 flow 交回后的宿主侧最早齐备点，早于 createEntry；定式见 tcp 篇 §5）。

## 11. 决策点（已全部裁定，2026-09-21/22）

| # | 决策 | 裁定 |
|---|---|---|
| D1 | core 准入 | 批准：+1 子类（AbstractSubConfigFlow）+宿主 2 方法 +1 枚举值（§4）。依据不是省胶水行数，是**演进封装**：宿主代码出现子 flow stepId 字面量/步骤数的那一刻，io 库演进的爆炸半径就回来了；把组合点收进 core 是「共性机制」准入（37 仓 × 未来每次 io 扩面）。主类零字段污染 |
| D2 | 首落库与试点 | modbus + rkonfly（epever 纯 clone，对照最干净） |
| D3 | 方言仓处理 | 存量 entry 保留方言读端；新装子 flow 迁主流形状，不强制回头改 |
| D4 | 设备运行时统一 CommSource 抽象 | 不纳入本设计（serial 通道承载演进由 serial 库内封装达成，§7.1） |
| D5 | 多子 flow stepId 前缀策略 | P4 前缀重载 + 冲突 fail-loud（挂载时校验已内建）；saimosen 晚期可选迁移 |
| D6 | 宿主自定义 schema 默认值的通道与边界 | 通道=子 flow builder 透传 schema 既有的 builder（不新造参数名）；定制只影响写端预填、读端 parse 缺字段兜底恒为库标准；builder 随 P1 一次落齐 |

域级补充裁决（细节录于各篇「决策记录」节）：五类域三落两缓（§7）；serial 本次单通道收编、TCP 承载留库内演进（触发条件出现再实施）；tcp server 暂缓；mqtt/http 不纳入；轮询间隔挂宿主设备业务步、key=`poll_interval_sec`（秒）、走 reconfigure 重建设备生效、标气两仓（saimosen SMS8600V2 / thermofisher 146i）间隔锁定；teledyne 类扩展字段由宿主独立步承接（第二批）；三代表仓单战役、P1/P2/P3 里程碑不作停点（§9）。

## 12. 候选与取舍记录（含本设计自身演进）

- **Fragment 契约（本设计第一版，已废弃）**：库导出「处理器工厂」，宿主手写 registerStep 胶水。废弃原因：stepId 字面量与步骤数泄漏进客户代码，io 库演进爆炸半径原样保留——违背唯一验收口径。废弃的第二版（installFragment 行为交接 onComplete）同理：出口屏 schema 属宿主，行为交接把宿主 lambda 交给库，库反向感知宿主步骤，抽象档次低。
- **SubFlow 新类（两轮裁定，2026-09-21 定形为 AbstractSubConfigFlow）**：第一轮曾裁掉（「直接用 AbstractConfigFlow 行吗」——当时以挂载校验+标志位达成隔离）；看到落地版后反转：标志位/出口方法/createEntry 守卫塞进主类是耦合，还是新建 `AbstractSubConfigFlow` 把子 flow 侧逻辑全部收口，主类只留挂载方法。同轮修正入口定义权：原「返回构造器注册的第一步」是隐式顺序约定（注册顺序≠流转顺序，脆弱）；改为子 flow 以 `registerStepEntry` 显式声明——**不由宿主点名**（宿主点名子 flow 内部 stepId 会让 io 库改名/前插步骤/裁剪入口的爆炸半径回到 37 仓；宿主要跳过首步经子 flow 构造参数表达）。
- **入口由宿主点名（否决）**：宿主 `registerFlowStep(sub, "protocol_select", tail)` 形态。三个失败场景：库改 stepId → 37 仓字面量全改；库前插步骤（如自动探测）→ 宿主永远跳过；库裁剪首步（fixedProtocol）→ 宿主点名成死步。裁定原则：谁的东西谁命名——尾步宿主命名（宿主的步骤），入口子 flow声明（子 flow 的步骤），宿主持不透明句柄。
- **io 配置独立 entry（双 entry 引用型）**：io 能力演进完全不动设备仓，但 37+ 仓存量 entry 大迁移、UX 变两段式、ADM FlowDriver 单 flow 停步模型被破坏、与 io-resource-owner「设备持资源」模型冲突。**否决**。
- **继承 mixin（io 库提供中间基类）**：Java 单继承，saimosen 需 serial+modbus 双步骤组叠不了。**否决**。
- **外部编排器泛化（FlowDriver 式）**：外部驱动只能快进/跳过既有步骤，不能为 flow 增加它没有的步骤。**否决**。
- **定制参数面的两个落选形态（D6，2026-09-21 裁定采用 schema builder 透传）**：① 语义化参数面（`.defaultBaudrate(4800)` 式）——每个可定制点在子 flow 里再立一个镜像选项，schema 加字段它就要跟，双份维护违「如非必要勿增实体」，否决；② 宿主后处理钩子（`(stepId, schema) -> 改字段`）——宿主摸 schema 内部字段 key，io 库改字段的爆炸半径回宿主，与「入口不许宿主点名」同一否决理由，否决。采用形态的依据：三个 schema 的实例级默认值 + builder 是既成事实（SerialCommConfigSchema.java:60-67,198-220、ModbusRtuCommConfigSchema Builder#serialSchema、ModbusTcpCommConfigSchema Builder 节），宿主今天就在直接用这些 builder，定制面零新增耦合（tcp 域实证：hj212 现行即 `TcpClientCommConfigSchema.builder().host(null).port(7030)...` 定制）。
- **serial 域 TCP 承载路径（2026-09-22 用户裁定）**：早期提案「设备仓换挂 ModbusCommFlow（三处各一行）」否决——两层错误：serial 域设备是私有帧协议（NMEA/厂商 ASCII 帧），modbus 运行时（功能码/寄存器语义）不适用；换挂=逐仓改三处，违背「库内演进采纳仓零改」唯一验收口径。裁定架构（§7.1）：参考 modbus 让用户选通道（串口/TCP）→ 后续 flow step 交给 serial 库初始化连接，客户端调用与纯串口一致（`register(info, this)` 统一 SerialSource），TCP 承载由 serial 库内部封装（可复用 tcp 库 TcpConnection）。时序：本次只收编单通道现状，TCP 承载留库内演进——「flow 有选项而运行时不支持=半成品」，不预造。
- **单战役裁定（2026-09-22）**：三代表仓（rkonfly / gassensor / com-hj212-sensor）一个战役一次做完，P1/P2/P3 作里程碑不作停点（§9）。
- **域调研负结论存档（2026-09-21/22）**：mqtt 不纳入（嵌入式 Broker 单例、registerClient 零连接参数，无配置收集面）、http 不纳入（拉取型 2 仓形状互斥，主流形状不存在）、tcp server 暂缓（零消费仓）——触发条件各篇 §4/§5/§6。
