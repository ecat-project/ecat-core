/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import lombok.Value;

/**
 * 复验失败条目:分类码+人话明细。
 *
 * <p>B1 PREREQ_FAILED 原因与升级队列页面渲染的共用载荷——分类码供机器分派
 * (修复指引/统计),明细供人读(含坐标/文件名/期望 vs 实际)。全量失败清单的
 * 元素形态:复验器不首错即断,一次报全,人工一次看清。</p>
 *
 * @author coffee
 */
@Value
public class VerifyFailure {

    /** 分类码,词表见 {@link UpgradeManifestVerifier} 类注释(MANIFEST_MALFORMED 等) */
    String code;

    /** 人话明细:含坐标/文件名/期望 vs 实际,直接进 state.yml error 与页面渲染 */
    String detail;
}
