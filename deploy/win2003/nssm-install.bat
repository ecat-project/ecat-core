@echo off
rem =====================================================================
rem ECAT Core - Windows Server 2003 (32位) 服务化安装脚本
rem 形态：Edge+ADM 采集单机（JVM 配方与 deploy/start-edge-adm.sh 完全一致）
rem
rem 版本锁定（依据 Win2003 实机门禁评审档案 arch-review-20260815/a0/win2003-gate.md
rem 的本地实证结论；三条实机门禁 T1/T2/T3 全过之前，不得认定"Win2003 兼容"成立）：
rem   JRE     = Zulu 8.0.242 win_i686（zulu8.44.0.13-ca-fx-jre8.0.242-win_i686.zip，
rem             zip 解包即用、自带 msvcr100.dll；8u251 起 win32 产物含 Vista+ 依赖，
rem             最新 Zulu 8u502 已判死——不可"拿新版试试"）
rem   服务化  = NSSM 2.24 win32（官方声明支持 Windows 2000+；PE 审计无 Vista+ 导入）
rem   串口库  = jSerialComm 2.9.3（已随 serial/modbus 落地；旧 2.6.2 的 win-x86
rem             native 静态导入 SetupDiGetDevicePropertyW，Win2003 加载必败）
rem
rem 工作目录硬约束：.ecat-data 为相对路径，core 按 JVM 工作目录解析——
rem NSSM 的 AppDirectory 必须设为部署根，否则 .ecat-data 会建到 system32 下。
rem
rem 用法：按机器实际改下面的路径变量，管理员身份运行本脚本。
rem 提醒：JVM 参数与路径含空格时须自行核对引号；本脚本假定部署根无空格。
rem =====================================================================
setlocal

rem 服务名（net stop / nssm status 均用此名）
set SERVICE_NAME=ECATCore

rem NSSM 2.24 win32 可执行文件（nssm-2.24.zip 解包，取 win32\nssm.exe——勿用 win64 目录）
set NSSM_EXE=C:\ecat\tools\nssm-2.24\win32\nssm.exe

rem Zulu 8u242 win_i686 解包位置（zip 解包即用，无需安装器/注册表）
set JAVA_EXE=C:\jre\bin\java.exe

rem 部署根：默认取本脚本所在目录的上两级（deploy\win2003\ 之上），
rem 即部署根布局 = ecat-core-3.0.0.jar + deploy\ + logs\ + .ecat-data\
for %%I in ("%~dp0..\..") do set DEPLOY_ROOT=%%~fI

set ECAT_JAR=%DEPLOY_ROOT%\ecat-core-3.0.0.jar
set LOG_DIR=%DEPLOY_ROOT%\logs

rem ---- JVM 配方：Edge+ADM 32 位（与 start-edge-adm.sh 同源，改动须两边同步）----
rem 768m 定堆 = OS250+PG350+Redis80+堆768+非堆430 约 1.88GB 预算的堆份额；
rem -Dfile.encoding=UTF-8 = 日志文件统一 UTF-8（2003 控制台 GBK 乱码仅是外观问题）
set JVM_OPTS=-Xms768m -Xmx768m -XX:+UseG1GC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=96m -XX:MaxDirectMemorySize=64m -Xss512k -Dfile.encoding=UTF-8

if not exist "%NSSM_EXE%" goto nssm_missing
if not exist "%JAVA_EXE%" goto jre_missing
if not exist "%ECAT_JAR%" goto jar_missing

echo [1/6] 安装服务 %SERVICE_NAME% ...
"%NSSM_EXE%" install %SERVICE_NAME% "%JAVA_EXE%" "%JVM_OPTS% -DLOG_DIR=%LOG_DIR% -jar %ECAT_JAR%"
if errorlevel 1 goto fail

echo [2/6] 工作目录 = 部署根（.ecat-data 相对路径约束）...
"%NSSM_EXE%" set %SERVICE_NAME% AppDirectory %DEPLOY_ROOT%

echo [3/6] 崩溃自动重启 + 开机自启 ...
rem AppExit Default Restart = 任意退出码默认重启；手工 net stop 不受影响
"%NSSM_EXE%" set %SERVICE_NAME% AppExit Default Restart
rem 2003 无"延迟自启"（Vista 起才有），用普通自动启动
"%NSSM_EXE%" set %SERVICE_NAME% Start SERVICE_AUTO_START

echo [4/6] 停机方式：先发控制台 Ctrl+C 信号等 110 秒 ...
rem NSSM 经 GenerateConsoleCtrlEvent 发 Ctrl+C，JVM shutdown hook 触发状态持久化，
rem 超时才走窗口/线程/终止的后续路径（等效 Linux 侧 kill -15 语义，勿省）。
rem 宽限窗 110000ms = core 停机编排总预算 100s（CoreShutdown TOTAL_BUDGET_MS）+ ~10s
rem JVM 退出余量——低于此值会在病态停机（多阶段同时卡死）时截断编排（丢尾批），改动须同步
"%NSSM_EXE%" set %SERVICE_NAME% AppStopMethodConsole 110000

echo [5/6] 输出重定向 + 轮转 ...
rem core-api.log 承接 logback 初始化前的启动输出与 OOM 栈（五通道之外兜底通道）
"%NSSM_EXE%" set %SERVICE_NAME% AppStdout %LOG_DIR%\core-api.log
"%NSSM_EXE%" set %SERVICE_NAME% AppStderr %LOG_DIR%\core-stderr.log
"%NSSM_EXE%" set %SERVICE_NAME% AppRotateFiles 1
"%NSSM_EXE%" set %SERVICE_NAME% AppRotateOnline 1
"%NSSM_EXE%" set %SERVICE_NAME% AppRotateBytes 10485760

echo [6/6] 启动服务 ...
"%NSSM_EXE%" start %SERVICE_NAME%
"%NSSM_EXE%" status %SERVICE_NAME%

echo.
echo 安装完成。验收要点（详见 deploy\win2003\README.md 门禁 T3）：
echo   1. %DEPLOY_ROOT%\.ecat-data\ 存在（不是 system32 下新建）
echo   2. nssm status %SERVICE_NAME% = SERVICE_RUNNING
echo   3. net stop %SERVICE_NAME% 后 core-api.log 出现 "EcatCore is shutting down..."
goto end

:nssm_missing
echo 错误：找不到 NSSM：%NSSM_EXE%
echo 下载 https://nssm.cc/release/nssm-2.24.zip（锁定 2.24，勿用其他版本）
exit /b 1

:jre_missing
echo 错误：找不到 JRE：%JAVA_EXE%
echo 需 Zulu 8.0.242 win_i686（zulu8.44.0.13-ca-fx-jre8.0.242-win_i686.zip）解包到该位置
exit /b 1

:jar_missing
echo 错误：找不到 jar：%ECAT_JAR%（部署根=%DEPLOY_ROOT%，可用 DEPLOY_ROOT 变量调整）
exit /b 1

:fail
echo 安装失败：检查上方 nssm 输出（路径/权限/是否已存在同名服务；重装先 nssm remove %SERVICE_NAME% confirm）
exit /b 1

:end
endlocal
