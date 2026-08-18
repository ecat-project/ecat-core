# Win2003 32 位部署 · 实机门禁与操作说明

> 依据：Win2003 32 位冒烟门禁评审档案（workspace `arch-review-20260815/a0/win2003-gate.md`，
> 本文为部署侧提炼；该档案含全部证据链与本地 PE 审计方法）。
> 本机（开发侧）为 Linux 无 Win2003 实机——**三条门禁全过之前，「Win2003 兼容」不成立**；
> 任一主线门禁过不了且备选也过不了，按约定上报重估，不硬凑。

## 0 · 版本锁定（勿换）

| 项 | 锁定版本 | 为什么锁这个 |
|---|---|---|
| JRE | **Zulu 8.0.242 win_i686**（`zulu8.44.0.13-ca-fx-jre8.0.242-win_i686.zip`，https://cdn.azul.com/zulu/bin/ 下载） | 8u251 起 win32 引入 Vista+ 依赖（社区多源交叉 + PE 审计）；8u242 是 i686 线 8u252 前最后版本；解包即用、自带 msvcr100.dll，零 redist 依赖 |
| 服务化 | **NSSM 2.24 win32**（https://nssm.cc/release/nssm-2.24.zip 取 `win32\nssm.exe`） | 官方声明支持 Windows 2000+；PE 审计无 Vista+ 导入、静态 CRT；含 Ctrl+C 控制台停机机制（与 core 的 shutdown hook 语义匹配） |
| 串口库 | **jSerialComm 2.9.3**（已随 serial/modbus 落地，无需现场动作） | 旧 2.6.2 的 win-x86 native 静态导入 `SetupDiGetDevicePropertyW`（Vista+ API），2003 加载必败；2.9.3 起移除 |

备选（仅主线失败时按序尝试，详见评审档案 §4）：JRE 备1 = Oracle JRE 8u251 i586 + 用 8u241 的 `sunmscapi.dll` 覆盖；备2 = Oracle 8u152 安装器直装。服务化备选 = commons-daemon 1.1.0 prunsrv（获取后须先做同款 PE 审计）。

## 1 · 服务化安装（门禁 T3 过后才是"可部署"）

以管理员身份运行同目录 `nssm-install.bat`（CRLF 行结束）。要点：

- **AppDirectory = 部署根**：`.ecat-data` 是相对路径常量，core 按 JVM 工作目录解析——设错则 `.ecat-data` 建到 `system32` 下（表象：服务起来了但 1 entry / 0 集成）。
- JVM 参数 = Edge+ADM 32 位配方（`-Xms768m -Xmx768m -XX:+UseG1GC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=96m -XX:MaxDirectMemorySize=64m -Xss512k`），与 `deploy/start-edge-adm.sh` 同源，改动须两边同步。
- 停机链：`AppStopMethodConsole 110000` → NSSM 发 Ctrl+C → JVM shutdown hook → 状态持久化（等效 Linux `kill -15`）；110s = core 停机编排总预算 100s（`CoreShutdown.TOTAL_BUDGET_MS`）+ ~10s JVM 退出余量，低于此值会在病态停机时截断编排（丢尾批）；超时才走强制终止。
- `AppExit Default Restart`：异常退出自动拉起；`Start SERVICE_AUTO_START` 开机自启（2003 无"延迟自启"，那是 Vista 起才有）。

日常操作：`nssm status ECATCore` / `net stop ECATCore`（优雅停）/ `net start ECATCore` / `nssm restart ECATCore` / 卸载 `nssm remove ECATCore confirm`。

## 2 · 三条实机门禁 checklist

环境前置（一次性记录）：系统属性确认 Windows Server 2003 / 内核 5.2 / SP 级别；物理内存 ≥2.5GB；设备管理器确认串口硬件（选 2003 有驱动的 USB-RS232，如 FTDI）。

### T1 · JDK 启动门禁（Zulu 8u242）

| # | 步骤 | 通过标准 |
|---|---|---|
| T1.1 | zip 解包到 `C:\jre`（不走安装器）→ `C:\jre\bin\java.exe -version` | 打印 `openjdk version "1.8.0_242"`；不出现「不是有效的 Win32 应用程序」/「找不到程序输入点」 |
| T1.2 | `java -XX:+UseG1GC -version` | 正常打印（32 位 client VM 接受 G1——该 JRE 只有 client/ 无 server/，此项必须实证） |
| T1.3 | `java -Xms768m -Xmx768m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=96m -XX:MaxDirectMemorySize=64m -Xss512k -version` | 完整配方被接受，无 Unrecognized option |
| T1.4 | 跑 5 分钟小程序（100 线程 + ServerSocket listen 9999 + 定时 GC），任务管理器观察 | 进程稳定、线程数不降、无崩溃 |

失败退路：备1 Oracle 8u251+sunmscapi 换 8u241 版重跑 → 备2 Oracle 8u152 直装重跑（记录行为差异）→ 仍败 = 上报重估。

### T2 · COM 回环收发门禁（jSerialComm 2.9.3）

准备：`jSerialComm-2.9.3.jar`（Maven Central）+ 下述测试类上机；USB-RS232 的 TX-RX 短接（回环）。

```java
// ComLoopTest.java —— 编译：javac -cp jSerialComm-2.9.3.jar ComLoopTest.java
import com.fazecast.jSerialComm.*;
public class ComLoopTest {
    public static void main(String[] a) throws Exception {
        SerialPort[] ports = SerialPort.getCommPorts();               // 枚举（内部走 SetupAPI）
        System.out.println("PORTS=" + ports.length);
        if (ports.length == 0) { System.out.println("NO_PORTS"); return; }
        SerialPort p = (a.length > 0) ? SerialPort.getCommPort(a[0]) : ports[0];
        p.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        p.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 2000, 0);
        System.out.println("OPEN=" + p.openPort());
        byte[] out = "ECAT-SMOKE\r\n".getBytes("US-ASCII");
        p.writeBytes(out, out.length);
        byte[] buf = new byte[64];
        int n = p.readBytes(buf, buf.length);
        System.out.println("WROTE=" + out.length + " READ=" + n
                + (n > 0 ? " ECHO=" + new String(buf, 0, n) : ""));
        p.closePort();
    }
}
```

| # | 步骤 | 通过标准 |
|---|---|---|
| T2.1 | `java -cp .;jSerialComm-2.9.3.jar ComLoopTest COM3`（端口按实际） | `PORTS>0` 且列表含目标 COM；无 UnsatisfiedLinkError（native 加载成功） |
| T2.2 | 回环读写 | `OPEN=true`、`WROTE=12 READ=12`、`ECHO=ECAT-SMOKE`（收发层过） |
| T2.3 | 对照组：同法跑 2.6.2 jar | 预期 UnsatisfiedLinkError「找不到程序输入点 SetupDiGetDevicePropertyW」——记录报错文本坐实依据 |

### T3 · 服务化开机自启门禁（NSSM 2.24）

| # | 步骤 | 通过标准 |
|---|---|---|
| T3.1 | 按第 1 节安装 ECATCore 服务 | `nssm status ECATCore` = SERVICE_RUNNING |
| T3.2 | 检查部署根 | `.ecat-data\` 出现在部署根（非 system32）；core-api 9999 有响应 |
| T3.3 | `net stop ECATCore` | 日志出现 "EcatCore is shutting down..."（优雅停机钩子完整跑了）；进程退出无残留 |
| T3.4 | `nssm restart` + 整机重启两次 | 两次开机服务均自动恢复 RUNNING |

**判定**：T1+T2+T3 主线全过 → 门禁三绿，「Win2003 兼容」成立；任一主线败且备选也败 → 上报重估（形态选项：换机 / 升 OS / UI 分机方案重排）。

## 3 · TLS 1.2 注意事项

- Win2003 默认无 TLS 1.2（需 KB + 注册表，是否有官方 KB 无可靠一手来源——不写为事实）。
- 影响面已核实：全部集成源码的出站 HTTPS 唯一点 = 集成 jar 云仓库下载（`mvn.ecat.bellyking.top`）。
- **Edge/Win2003 部署约定：集成 jar 全量预装本地，云仓库路径保持禁用**——则 TLS 1.2 缺失无实际影响。
- 若现场确需云仓库下载：先做 T1.7（IE 访问 `https://mvn.ecat.bellyking.top`，记录握手是否失败）再评估；2003 schannel 能否完成 TLS 1.2 握手属〔待实测〕。

## 4 · 已知确定性限制（接受并记录，不当作 bug）

- 8u242 补丁基线 2020-01，停更 6 年——攻击面评估：内网采集机、无浏览器插件路径、出站 HTTPS 仅云仓库（未启用），接受。
- 该 JRE 32 位仅 client VM（bin/ 无 server/）——2 核性能影响在压测覆盖；若成瓶颈走备选 JRE 重估。
- fx 捆绑包内的 JavaFX 原生库（glib-lite/jfxwebkit）含 Vista+ 导入——**不装任何 JavaFX 组件**、不触碰 javafx.* 类即无害。
- 中文 2003 控制台 GBK：日志文件 UTF-8 不受影响，控制台乱码仅外观问题。
