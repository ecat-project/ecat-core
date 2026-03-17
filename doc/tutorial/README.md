# ECAT 集成开发教程

ECAT (EcoAutomation) 是一个用于生态环境监测的工业数据自动化控制系统。采用 Java 开发，基于模块化插件架构，支持多种环境监测设备和数据处理。

**技术栈**：Java 8, Maven 3.x

## 前置知识

阅读本教程前，你需要具备以下基础知识：

- **Java 8** — Lambda 表达式、函数式接口（Function/BiFunction/Supplier）
- **Maven** — 基本的 pom.xml 配置、依赖管理、构建打包
- **JUnit 4** — @Test, @Before, @After 注解
- **Mockito** — 基本的 mock/spy 用法（用于单元测试隔离）

## 推荐阅读顺序

如果你是第一次接触 ECAT 集成开发，建议按以下顺序阅读：

1. **[项目结构与核心文件](project-structure.md)** — 了解项目组织方式和核心概念
2. **[设备开发规范](device-development.md)** — 学习设备类的命名和开发规范
3. **[I18n 国际化系统](i18n.md)** — 理解国际化机制（所有集成都需要）
4. **[开发工作流程](workflow.md)** — 完整的端到端开发流程
5. **[单元测试](testing.md)** — 学习如何编写测试
6. **[Config Flow 配置向导](config-flow.md)** — 需要配置向导时阅读
7. **[Config Schema 配置定义](config-schema.md)** — 配合 Config Flow 使用
8. **[Maven 配置规范](maven-config.md)** — 构建打包时参考

## 教程目录

| # | 文档 | 内容 | 难度 |
|---|------|------|------|
| 1 | [项目结构与核心文件](project-structure.md) | 项目目录结构、设备类/属性类/基类/国际化文件说明 | 入门 |
| 2 | [设备开发规范](device-development.md) | 命名规则、属性创建标准、构造函数规范 | 入门 |
| 3 | [I18n 国际化系统](i18n.md) | Proxy 架构、资源管理、设备分组、I18nKeyPath | 中级 |
| 4 | [开发工作流程](workflow.md) | 项目创建 → 设备开发 → 国际化 → 测试验证 | 入门 |
| 5 | [单元测试](testing.md) | TestTools 反射工具、i18n 测试模式 | 中级 |
| 6 | [Config Flow 配置向导](config-flow.md) | 分步向导框架、入口步骤、条件分支 | 中级 |
| 7 | [Config Schema 配置定义](config-schema.md) | 字段类型、校验规则、嵌套/引用 Schema | 中级 |
| 8 | [Maven 配置规范](maven-config.md) | Assembly Plugin、ecat-config.yml、fatjar.xml | 参考 |
| 9 | [幂等配置加载](idempotent-config-loading.md) | ready 字段、onAllExistEntriesLoaded 钩子、缓存待激活/周期检查模式 | 中级 |

## 相关文档

| 文档 | 路径 |
|------|------|
| 日志系统 | [Log/LOG.md](ecat/core/Log/LOG.md) |
| ConfigFlow 开发 | [Integration/ConfigFlow-development.md](ecat/core/Integration/ConfigFlow-development.md) |
| ConfigFlow 使用 | [Integration/ConfigFlow-usage.md](ecat/core/Integration/ConfigFlow-usage.md) |
