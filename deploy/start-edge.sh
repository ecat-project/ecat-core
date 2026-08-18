#!/usr/bin/env bash
#
# 纯采集 Edge 形态启动脚本（目标整机 1GB / 2 核；JVM 配方按 32 位地址空间制定，64 位机同样适用）。
#
# 形态组成：core + 通信（serial/modbus/tcp/http…）+ 设备集成 + logicdevice + ecat-core-api。
# 与 Edge+ADM 形态（start-edge-adm.sh）的安装子集差异——以下不装：
#   ruoyi（连带本地数据库/Redis 不需要）、env-air-device-manager(ADM)、media(JavaCPP/FFmpeg
#   native 重)、其余 env-* 业务集成。安装集由 <部署根>/.ecat-data/core/integrations.yml 决定
#   （每集成一条 groupId:artifactId 记录，enabled 开关），详见 deploy/README.md。
# UI 展示走分机方案：任意规格 UI 机经 9999 core-api/SSE 访问本机，架构零改动。
#
# JVM 配方为什么比 Edge+ADM 全线下调（1GB 整机 + 32 位 2GB 地址空间双重约束）：
#   -Xms512m -Xmx512m       定堆：1GB 整机里 OS + JVM 非堆之外只够 512m 堆；定堆免扩缩暂停。
#   MaxMetaspaceSize=96m     无 ruoyi/Spring 全家桶后类元数据需求小，配额下调。
#   ReservedCodeCacheSize=64m JIT 缓存同步下调。
#   MaxDirectMemorySize=32m  堆外配额同步下调。
#   -Xss512k                每线程栈减半（Edge 线程目标 ≤60，栈 reserved 吃地址空间）。
#   -XX:+UseG1GC            小堆可预测停顿（Win2003 实机门禁项见 deploy/win2003/README.md T1.2）。
#
# 工作目录硬约束：.ecat-data 是相对路径常量（core 按 JVM cwd 解析），JVM 必须以部署根为 cwd，
# 否则 .ecat-data/ 会建到错误位置（表象：Started 成功但 1 entry / 0 集成）。本脚本 cd 到部署根
# （脚本位于 <部署根>/deploy/ 下，部署根 = 本脚本所在目录的上一级）。
#
# 停机约定：kill -15（SIGTERM）→ JVM shutdown hook → 状态持久化 + 任务收尾。
# 编排层（systemd 等）直接对本进程发 SIGTERM 即可；PID 文件供人工/脚本停机与防双启。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ---- 可配置项（环境变量覆盖）----
JAVA_BIN="${JAVA_BIN:-java}"                              # 默认取 PATH；可指向随包 JRE（如解包的 Zulu 8）
ECAT_JAR="${ECAT_JAR:-${DEPLOY_ROOT}/ecat-core-3.0.0.jar}" # 部署根下的 fat jar
LOG_DIR="${LOG_DIR:-${DEPLOY_ROOT}/logs}"                 # logback 经 -DLOG_DIR 识别（不传则相对 cwd 的 logs/）
PID_FILE="${PID_FILE:-${DEPLOY_ROOT}/ecat-core.pid}"

cd "${DEPLOY_ROOT}"

# ---- 前置检查 ----
command -v "${JAVA_BIN}" >/dev/null 2>&1 || { echo "错误：找不到 java（JAVA_BIN=${JAVA_BIN}）" >&2; exit 1; }
[[ -f "${ECAT_JAR}" ]] || { echo "错误：jar 不存在：${ECAT_JAR}（可用 ECAT_JAR 覆盖）" >&2; exit 1; }

# 幂等：已有活实例则拒绝二次启动；残留 PID 文件（上次异常退出）清掉继续
if [[ -f "${PID_FILE}" ]]; then
    old_pid="$(cat "${PID_FILE}")"
    if [[ "${old_pid}" =~ ^[0-9]+$ ]] && kill -0 "${old_pid}" 2>/dev/null; then
        echo "错误：已有实例在运行（PID ${old_pid}，PID 文件 ${PID_FILE}）。先优雅停机：kill -15 ${old_pid}" >&2
        exit 1
    fi
    rm -f "${PID_FILE}"
fi

mkdir -p "${LOG_DIR}"

# ---- JVM 配方（改任何一个数之前先重算 1GB 整机预算，见文件头）----
JVM_OPTS=(
    -Xms512m -Xmx512m
    -XX:+UseG1GC
    -XX:MaxMetaspaceSize=96m
    -XX:ReservedCodeCacheSize=64m
    -XX:MaxDirectMemorySize=32m
    -Xss512k
    -Dfile.encoding=UTF-8
    -DLOG_DIR="${LOG_DIR}"
)

# stdout/stderr 追加重定向 core-api.log：承接 logback 初始化前的启动输出、System.out 与
# OutOfMemoryError 栈（logback 五通道文件见不到的兜底通道；append 保重启不丢上次启动尾迹）
nohup "${JAVA_BIN}" "${JVM_OPTS[@]}" -jar "${ECAT_JAR}" \
    >> "${LOG_DIR}/core-api.log" 2>&1 &

pid=$!
echo "${pid}" > "${PID_FILE}"

echo "已启动：PID ${pid}"
echo "  工作目录（.ecat-data 所在，须为部署根）：${DEPLOY_ROOT}"
echo "  日志目录：${LOG_DIR}（lifecycle/comm-health/error/app + core-api.log；DEBUG 默认不落盘）"
echo "  优雅停机：kill -15 ${pid}   （SIGTERM → shutdown hook → 状态持久化，勿用 kill -9）"
