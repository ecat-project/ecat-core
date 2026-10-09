/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 升级清单 manifest 值对象:重启窗复验的唯一 schema 载体,JSON 往返无损。
 *
 * <p>manifest 一经写入不可变语义(B3 提交只读它;重试=重写新文件后原子替换,
 * 内容恒同因输入恒同)。字段名/嵌套形态与云端 resolve wire 契约逐字段同构——
 * 新增字段须同步云端契约(跨片字段演进同步义务)。</p>
 *
 * <p>序列化契约:fastjson2 落盘/读回({@code JSON.toJSONString}/{@code JSON.parseObject}),
 * snake_case 键位经 {@code @JSONField} 逐字段显式映射(与 CloudRepositoryClient 内部
 * DTO 族同先例)。db_conventions 字段已随 db: 块退役:本类不再声明该字段,旧清单里
 * 残留的 db_conventions 键由 fastjson2 未知键忽略语义自然容忍(过渡期旧清单仍在盘,
 * 解析与复验都不得因其报错)。</p>
 *
 * @author coffee
 */
@Data
@NoArgsConstructor
public class UpgradeManifest {

    /** planId:非空串,=落盘目录名(客户端生成,T-1-3 规则) */
    private String planId;

    /**
     * 应用类型:恒 UPGRADE(T-1-3 共用一型裁定:安装降级与升级在重启窗的应用语义
     * 一致)——壳默认携带,writer 侧单侧闭合 QueuePlan 读面 type 硬门接缝,云端产物
     * 与本地壳键位同形,两产源零差异化处理。
     */
    private String type = "UPGRADE";

    /** 清单铸时刻,ISO-8601 */
    private String createdAt;

    /** 上报时的当前 core 版本原值(CoreVersions.current()) */
    private String coreVersion;

    /** 产源:resolve=云端 resolve 产源;local=T-1-3 壳写侧本地产源(安装降级/本地升级);余值=复验拒绝 */
    private String source;

    /** 升级项,恒非空(空计划不入队:resolve 空计划不产生 planId/manifest/队列任务) */
    private List<UpgradeItem> items = new ArrayList<>();

    /** 依赖闭包全域终态(云端 resolve 原值;本地产源形态无此面,复验门按字段在位性分派) */
    @JSONField(name = "resolution_map")
    private List<ResolutionEntry> resolutionMap = new ArrayList<>();

    /**
     * 单升级项:云端 resolve upgrades[] 条目的 manifest 形态。
     * installed_version=null=新装坐标(D21 语义分路:新装/升级)。
     */
    @Data
    @NoArgsConstructor
    public static class UpgradeItem {

        @JSONField(name = "group_id")
        private String groupId;

        @JSONField(name = "artifact_id")
        private String artifactId;

        /** 当前已装版本;null=新装坐标(写入侧以 WriteMapNullValue 保键位) */
        @JSONField(name = "installed_version")
        private String installedVersion;

        @JSONField(name = "target_version")
        private String targetVersion;

        /** 目标构建声明的 core 约束(客户端复验门消费;缺失=拒绝) */
        @JSONField(name = "requires_core")
        private String requiresCore;

        /** 发布产物文件清单,每条必带 sha256 */
        private List<FileEntry> files = new ArrayList<>();

        /** 目标构建直接依赖的终态解析(依赖齐全复验消费面) */
        @JSONField(name = "resolved_dependencies")
        private List<ResolvedDependency> resolvedDependencies = new ArrayList<>();
    }

    /** 发布产物文件条目:kind=jar|pom,sha256=64 位十六进制(与云端构建产物一致) */
    @Data
    @NoArgsConstructor
    public static class FileEntry {

        private String kind;

        @JSONField(name = "group_id")
        private String groupId;

        @JSONField(name = "artifact_id")
        private String artifactId;

        private String version;

        private String filename;

        private String sha256;
    }

    /** 目标构建直接依赖的终态解析条目 */
    @Data
    @NoArgsConstructor
    public static class ResolvedDependency {

        @JSONField(name = "group_id")
        private String groupId;

        @JSONField(name = "artifact_id")
        private String artifactId;

        /** 终态解析版本 */
        private String version;

        /** 声明约束原串 */
        private String constraint;
    }

    /** 依赖闭包全域终态条目:source=installed(保持已装)|resolved(闭包解析目标) */
    @Data
    @NoArgsConstructor
    public static class ResolutionEntry {

        @JSONField(name = "group_id")
        private String groupId;

        @JSONField(name = "artifact_id")
        private String artifactId;

        private String version;

        private String source;
    }
}
