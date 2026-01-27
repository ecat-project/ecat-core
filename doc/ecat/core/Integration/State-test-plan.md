ECAT 集成管理功能测试方案（V2.0 适配最新状态定义）

1. 概述

1.1 测试目标

验证 ECAT 微内核系统的集成管理功能，包括新增、启用、停用、升级、卸载等全生命周期操作的正确性；验证各集成状态（运行中、已停止、新增待重启、升级待重启、卸载待重启、已删除）转换的合规性；验证配置文件与运行状态的同步一致性；验证PENDING_*状态锁定约束及重启后状态处理逻辑的有效性。

1.2 依赖关系

测试涉及两条核心依赖链路：

- 链路1：环境告警管理器（env-alarm-manager）→ 核心若依组件（ecat-core-ruoyi）→ 公共组件（ecat-common）→ 核心组件（ecat-core）

- 链路2：帆英雄（sailhero）→ 串口组件（serial）→ 公共组件（ecat-common）→ 核心组件（ecat-core）

1.3 核心规则

- 状态约束规则：运行中（RUNNING）/已停止（STOPPED）可执行合规操作；新增待重启（PENDING_ADDED）、升级待重启（PENDING_UPGRADE）、卸载待重启（PENDING_REMOVED）为锁定状态，禁止任何操作，需重启系统解除。

- 停用/卸载规则：被其他集成依赖的集成，禁止执行停用、卸载操作；仅无依赖者可执行停用（→已停止）、卸载（→卸载待重启）。

- 新增规则：依赖满足且无需调整类加载器层级→热加载至运行中；需调整类加载器层级→标记为新增待重启，重启后生效。

- 升级规则：无论当前状态为运行中/已停止，升级后均标记为升级待重启，旧版本继续运行，重启后加载新版本。

- AB测试规则：验证卸载前后功能状态差异（卸载前有效，卸载后调用onRelease且功能不可用）；验证升级前后版本运行状态差异（升级后旧版本运行，重启后新版本生效）。

2. 测试用例设计

2.1 集成停用功能测试

TC-001: 停用不被依赖的集成（sailhero）→ 已停止

项目

内容

测试目的

验证无依赖的 sailhero 集成可成功停用，状态转为已停止，配置同步更新

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、serial、sailhero 均处于运行中（RUNNING）状态；sailhero 无依赖者

测试步骤

1. 登录ECAT系统管理后台，进入集成管理模块2. 找到 sailhero 集成项，点击“停用”按钮3. 查看该集成的状态展示区域，确认状态标识4. 验证系统是否调用 onPause 接口（通过日志查看）5. 进入设备管理模块，查看 sailhero 关联的所有设备状态6. 找到 sailhero 集成的配置文件（integrations.yml），打开后检查 enabled、state 参数

预期结果

- sailhero 集成状态显示为“已停止（STOPPED）”- 日志记录 onPause 接口调用成功- 所有 sailhero 关联设备状态均为“已停止”- 配置文件中 enabled: false，state: "STOPPED"，无 _deleted 标记- serial、ecat-common、ecat-core 仍处于运行中，无异常

验证方法

查看集成管理界面状态、设备管理界面设备状态、手动核对配置文件参数、查看系统运行日志（接口调用+无报错）

TC-002: 停用不被依赖的集成（env-alarm-manager）→ 已停止

项目

内容

测试目的

验证无依赖的 env-alarm-manager 集成可成功停用，状态转为已停止，配置同步更新

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、ecat-core-ruoyi、env-alarm-manager 均处于运行中（RUNNING）状态；env-alarm-manager 无依赖者

测试步骤

1. 登录ECAT系统管理后台，进入集成管理模块2. 找到 env-alarm-manager 集成项，点击“停用”按钮3. 查看该集成的状态展示区域，确认状态标识4. 验证系统是否调用 onPause 接口（通过日志查看）5. 打开浏览器，访问告警管理页面6. 找到 env-alarm-manager 集成的配置文件，检查 enabled、state 参数

预期结果

- env-alarm-manager 集成状态显示为“已停止（STOPPED）”- 日志记录 onPause 接口调用成功- 告警管理页面无法访问（提示404或服务不可用）- 配置文件中 enabled: false，state: "STOPPED"，无 _deleted 标记- ecat-core-ruoyi、ecat-common、ecat-core 仍处于运行中，无异常

验证方法

查看集成管理界面状态、浏览器访问页面验证、手动核对配置文件参数、查看系统运行日志（接口调用+无报错）

TC-003: 停用被依赖的集成（serial）- 预期拒绝

项目

内容

测试目的

验证被依赖的 serial 集成无法停用，状态保持运行中

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、serial、sailhero 均处于运行中（RUNNING）状态；sailhero 依赖 serial

测试步骤

1. 登录ECAT系统管理后台，进入集成管理模块2. 找到 serial 集成项，点击“停用”按钮3. 查看系统弹出的提示信息，记录内容4. 再次确认 serial 集成的状态标识5. 核对 serial 配置文件中 enabled、state 参数

预期结果

- 系统弹出错误提示，明确说明“无法停用，以下集成依赖此集成: com.ecat:integration-sailhero”- serial 集成状态仍显示为“运行中（RUNNING）”- 配置文件中 enabled: true，state: "RUNNING"- 无系统报错或异常退出情况

验证方法

查看弹窗提示内容、核对集成管理界面状态、手动核对配置文件参数、查看系统运行日志

TC-004: 停用被依赖的集成（ecat-common）- 预期拒绝

项目

内容

测试目的

验证被多条链路依赖的 ecat-common 集成无法停用，状态保持运行中

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、serial、sailhero、ecat-core-ruoyi、env-alarm-manager 均处于运行中（RUNNING）状态；serial、ecat-core-ruoyi 依赖 ecat-common

测试步骤

1. 登录ECAT系统管理后台，进入集成管理模块2. 找到 ecat-common 集成项，点击“停用”按钮3. 查看系统弹出的提示信息，记录内容4. 再次确认 ecat-common 集成的状态标识5. 核对 ecat-common 配置文件中 enabled、state 参数

预期结果

- 系统弹出错误提示，明确说明“无法停用，以下集成依赖此集成: serial、ecat-core-ruoyi”- ecat-common 集成状态仍显示为“运行中（RUNNING）”- 配置文件中 enabled: true，state: "RUNNING"- 无系统报错或异常退出情况

验证方法

查看弹窗提示内容、核对集成管理界面状态、手动核对配置文件参数、查看系统运行日志

TC-005: 停用被依赖的集成（ecat-core-ruoyi）- 预期拒绝

项目

内容

测试目的

验证被依赖的 ecat-core-ruoyi 集成无法停用，状态保持运行中

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、ecat-core-ruoyi、env-alarm-manager 均处于运行中（RUNNING）状态；env-alarm-manager 依赖 ecat-core-ruoyi

测试步骤

1. 登录ECAT系统管理后台，进入集成管理模块2. 找到 ecat-core-ruoyi 集成项，点击“停用”按钮3. 查看系统弹出的提示信息，记录内容4. 再次确认 ecat-core-ruoyi 集成的状态标识5. 核对 ecat-core-ruoyi 配置文件中 enabled、state 参数

预期结果

- 系统弹出错误提示，明确说明“无法停用，以下集成依赖此集成: env-alarm-manager”- ecat-core-ruoyi 集成状态仍显示为“运行中（RUNNING）”- 配置文件中 enabled: true，state: "RUNNING"- 无系统报错或异常退出情况

验证方法

查看弹窗提示内容、核对集成管理界面状态、手动核对配置文件参数、查看系统运行日志

2.2 集成启用功能测试

TC-006: 启用已停止的集成（sailhero）→ 运行中

项目

内容

测试目的

验证已停止的 sailhero 集成可成功启用，状态转为运行中，配置同步更新

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、serial 处于运行中（RUNNING）状态，sailhero 处于已停止（STOPPED）状态

测试步骤

1. 登录ECAT系统管理后台，进入集成管理模块2. 找到 sailhero 集成项，点击“启用”按钮3. 查看该集成的状态展示区域，确认状态标识4. 验证系统是否调用 onStart 接口（通过日志查看）5. 进入设备管理模块，查看 sailhero 关联的所有设备状态6. 找到 sailhero 集成的配置文件，检查 enabled、state 参数

预期结果

- sailhero 集成状态显示为“运行中（RUNNING）”- 日志记录 onStart 接口调用成功- 所有 sailhero 关联设备状态均为“运行中”- 配置文件中 enabled: true，state: "RUNNING"- 无系统报错或异常退出情况

验证方法

查看集成管理界面状态、设备管理界面设备状态、手动核对配置文件参数、查看系统运行日志（接口调用+无报错）

TC-007: 启用已停止的集成（env-alarm-manager）→ 运行中

项目

内容

测试目的

验证已停止的 env-alarm-manager 集成可成功启用，状态转为运行中，配置同步更新

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、ecat-core-ruoyi 处于运行中（RUNNING）状态，env-alarm-manager 处于已停止（STOPPED）状态

测试步骤

1. 登录ECAT系统管理后台，进入集成管理模块2. 找到 env-alarm-manager 集成项，点击“启用”按钮3. 查看该集成的状态展示区域，确认状态标识4. 验证系统是否调用 onStart 接口（通过日志查看）5. 打开浏览器，访问告警管理页面6. 找到 env-alarm-manager 集成的配置文件，检查 enabled、state 参数

预期结果

- env-alarm-manager 集成状态显示为“运行中（RUNNING）”- 日志记录 onStart 接口调用成功- 告警管理页面可正常访问，能显示相关数据- 配置文件中 enabled: true，state: "RUNNING"- 无系统报错或异常退出情况

验证方法

查看集成管理界面状态、浏览器访问页面验证、手动核对配置文件参数、查看系统运行日志（接口调用+无报错）

2.3 集成卸载功能测试（含AB测试）

TC-008: 卸载不被依赖的集成（sailhero）→ 卸载待重启（AB测试）

项目

内容

测试目的

验证卸载 sailhero 集成前后功能状态差异，及卸载后进入锁定状态、配置同步更新的正确性

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、serial、sailhero 均处于运行中（RUNNING）状态；sailhero 设备已配置且正常运行，无依赖者

测试步骤

阶段A - 卸载前功能验证：1. 进入设备管理模块，查看 sailhero 关联的所有设备列表2. 核对所有设备的运行状态3. 触发设备数据采集操作，查看数据返回情况4. 记录所有功能验证结果阶段B - 卸载后功能验证：1. 回到集成管理模块，找到 sailhero 集成项，点击“卸载”按钮2. 查看该集成的状态展示区域，确认状态标识3. 验证系统是否调用 onRelease 接口（通过日志查看）4. 尝试对 sailhero 执行启用停用操作，查看系统反馈5. 再次进入设备管理模块，尝试获取 sailhero 设备列表、触发数据采集6. 找到 sailhero 集成的配置文件，检查 _deleted、state 参数7. 重启系统，等待启动完成后，查看集成列表是否存在 sailhero

预期结果

阶段A（卸载前）：- sailhero 设备列表不为空，所有设备状态为“运行中”- 数据采集操作正常，能返回有效数据- 配置文件中 enabled: true，state: "RUNNING"，无 _deleted 标记阶段B（卸载后）：- 卸载后状态显示为“卸载待重启（PENDING_REMOVED）”- 日志记录 onRelease 接口调用成功- 尝试执行启用停用操作，系统提示“当前集成处于锁定状态，禁止操作，需重启系统”- 设备列表为空或提示“无相关设备”，数据采集操作失败- 配置文件中 _deleted: true，state: "PENDING_REMOVED"，enabled: false- 系统重启后，集成列表中无 sailhero 条目，配置文件中该条目被清理- serial、ecat-common、ecat-core 仍正常运行，无异常

验证方法

查看集成管理界面状态、设备功能操作结果、手动核对配置文件参数、重启后验证清理效果、查看系统运行日志（接口调用+锁定提示）

TC-009: 卸载不被依赖的集成（env-alarm-manager）→ 卸载待重启（AB测试）

项目

内容

测试目的

验证卸载 env-alarm-manager 集成前后Web功能状态差异，及卸载后进入锁定状态、配置同步更新的正确性

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、ecat-core-ruoyi、env-alarm-manager 均处于运行中（RUNNING）状态；env-alarm-manager 无依赖者

测试步骤

阶段A - 卸载前功能验证：1. 打开浏览器，访问告警管理页面（地址：http://服务器地址/alarm）2. 确认页面正常访问且显示数据3. 测试告警查询功能，查看数据返回情况4. 记录所有功能验证结果阶段B - 卸载后功能验证：1. 回到集成管理模块，找到 env-alarm-manager 集成项，点击“卸载”按钮2. 查看该集成的状态展示区域，确认状态标识3. 验证系统是否调用 onRelease 接口（通过日志查看）4. 尝试对 env-alarm-manager 执行启用停用操作，查看系统反馈5. 再次访问告警管理页面、测试告警查询功能6. 找到 env-alarm-manager 集成的配置文件，检查 _deleted、state 参数7. 重启系统，等待启动完成后，查看集成列表是否存在 env-alarm-manager

预期结果

阶段A（卸载前）：- 告警管理页面正常访问，显示完整数据- 告警查询功能正常，能返回有效数据- 配置文件中 enabled: true，state: "RUNNING"，无 _deleted 标记阶段B（卸载后）：- 卸载后状态显示为“卸载待重启（PENDING_REMOVED）”- 日志记录 onRelease 接口调用成功- 尝试执行启用停用操作，系统提示“当前集成处于锁定状态，禁止操作，需重启系统”- 告警管理页面无法访问（404/服务不可用），告警查询功能失败- 配置文件中 _deleted: true，state: "PENDING_REMOVED"，enabled: false- 系统重启后，集成列表中无 env-alarm-manager 条目，配置文件中该条目被清理- ecat-core-ruoyi、ecat-common、ecat-core 仍正常运行，无异常

验证方法

查看集成管理界面状态、浏览器访问/接口测试结果、手动核对配置文件参数、重启后验证清理效果、查看系统运行日志

TC-010: 卸载被依赖的集成（serial）- 预期拒绝

项目

内容

测试目的

验证被依赖的 serial 集成无法卸载，状态保持运行中

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、serial、sailhero 均处于运行中（RUNNING）状态；sailhero 依赖 serial

测试步骤

1. 登录ECAT系统管理后台，进入集成管理模块2. 找到 serial 集成项，点击“卸载”按钮3. 查看系统弹出的提示信息，记录内容4. 再次确认 serial 集成的状态标识5. 核对 serial 配置文件中 _deleted、state 参数

预期结果

- 系统弹出错误提示，明确说明“无法卸载，以下集成依赖此集成: com.ecat:integration-sailhero”- serial 集成状态仍显示为“运行中（RUNNING）”- 配置文件中无 _deleted 标记，state: "RUNNING"- 无系统报错或异常退出情况

验证方法

查看弹窗提示内容、核对集成管理界面状态、手动核对配置文件参数、查看系统运行日志

TC-011: 卸载被依赖的集成（ecat-common）- 预期拒绝

项目

内容

测试目的

验证被多条链路依赖的 ecat-common 集成无法卸载，状态保持运行中

测试优先级

P0（高）

前置条件

系统正常启动，ecat-core、ecat-common、serial、sailhero、ecat-core-ruoyi、env-alarm-manager 均处于运行中（RUNNING）状态；serial、ecat-core-ruoyi 依赖 ecat-common

测试步骤

1. 登录ECAT系统管理后台，进入集成管理模块2. 找到 ecat-common 集成项，点击“卸载”按钮3. 查看系统弹出的提示信息，记录内容4. 再次确认 ecat-common 集成的状态标识5. 核对 ecat-common 配置文件中 _deleted、state 参数

预期结果

- 系统弹出错误提示，明确说明“无法卸载，以下集成依赖此集成: serial、ecat-core-ruoyi”- ecat-common 集成状态仍显示为“运行中（RUNNING）”- 配置文件中无 _deleted 标记，state: "RUNNING"- 无系统报错或异常退出情况
