/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

/**
 * 升级编排器状态机故障:队列状态文件损坏/转移乱序/前置标记缺失的明确异常。
 *
 * <p>非受检:boot 主线程顶层接——runPreLoadPhase/runPostLoadPhase 捕获后转保守回滚或
 * 零变更段 FAILED,不上穿杀 boot:状态机自身故障不得比升级失败更具破坏性
 * (分级=T-3-3 哲学:编排器故障≠全局配置损坏,与环依赖杀 boot 的全局态分界)。
 * 触发面=保守回滚(快照在)或计划作废(零变更段),绝不静默续推。</p>
 *
 * @author coffee
 */
public class UpgradeStateException extends RuntimeException {

    public UpgradeStateException(String message) {
        super(message);
    }

    public UpgradeStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
