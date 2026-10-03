/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.Version;

import java.util.function.Supplier;

import com.ecat.core.EcatCore;

/**
 * core 自身版本的唯一权威出口。
 *
 * <p>值来自 fat jar manifest 的 Implementation-Version 条目——打包期由 pom
 * {@code project.version} 写入,与 pom 单源零漂移:升版本只改 pom,本类零改动。
 * 以 {@link EcatCore} 为 manifest 载体类取包元数据:它是 Main-Class、只在 core
 * 主 jar 出现一次,取包无重复类加载歧义。
 *
 * <p>返回值契约:要么是合法版本串,要么抛 {@link IllegalStateException},永不为
 * null、也不落 "0.0.0" 类默认值——版本不明时静默放行比明确失败更危险。
 *
 * <p>值形态 MAJOR.MINOR.PATCH 三段,满足 {@link Version#parse} 的输入契约,消费方
 * (兼容门/版本比对)可直接解析;本类不做格式执法,格式执法归消费方既有解析逻辑。
 */
public final class CoreVersions {

    /**
     * 版本值源缝:包级可见 static volatile,本类唯一可变状态。
     *
     * <p>仅供同包单测注值(单测/IDE 从 classes/ 目录加载,无 manifest 可读,用例须经此缝
     * 供值),生产代码禁写。缝形态先例=Utils.YamlAtomicFileWriter#moveAtomicallyWithRetry
     * (刻意测试缝,javadoc 同款声明风格)。勿改 private(同包测试不可达)、勿加 public
     * setter(字段已包级可见,setter 无增益反扩暴露面)。
     */
    static volatile Supplier<String> versionSource = () -> {
        Package pkg = EcatCore.class.getPackage();
        return pkg != null ? pkg.getImplementationVersion() : null;
    };

    private CoreVersions() {
    }

    /**
     * 获取当前 core 版本(唯一权威出口)。
     *
     * @return 版本串(MAJOR.MINOR.PATCH,来自 fat jar manifest 的 Implementation-Version,
     *         构建期由 pom project.version 写入,单源零漂移)
     * @throws IllegalStateException manifest 缺失/不可读(单测从 classes/ 加载、异常装配形态)。
     *         版本不明=兼容门失明,失明的门比没有更危险——明确失败,禁返 "0.0.0" 类兜底
     */
    public static String current() {
        String v = versionSource.get();
        if (v == null || v.isEmpty()) {
            throw new IllegalStateException("core 版本不可得:manifest 无 Implementation-Version"
                + "(生产=fat jar 打包异常;单测=请经 CoreVersions 包级测试缝注值)");
        }
        return v;
    }
}
