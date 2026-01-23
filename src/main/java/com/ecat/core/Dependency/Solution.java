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

package com.ecat.core.Dependency;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.ecat.core.Version.Version;

/**
 * 版本求解结果
 * <p>
 * 包含求解出的版本组合和可能的冲突信息。
 * </p>
 *
 * @author coffee
 */
public class Solution {

    private final Map<String, Version> selectedVersions;
    private final boolean hasSolution;
    private final List<Conflict> conflicts;

    /**
     * 创建成功的求解结果
     *
     * @param selectedVersions 选中的版本组合
     */
    public Solution(Map<String, Version> selectedVersions) {
        this(selectedVersions, true, Collections.emptyList());
    }

    /**
     * 创建求解结果
     *
     * @param selectedVersions 选中的版本组合（可能不完整）
     * @param hasSolution 是否有解
     * @param conflicts 冲突列表
     */
    public Solution(
            Map<String, Version> selectedVersions,
            boolean hasSolution,
            List<Conflict> conflicts) {
        this.selectedVersions = selectedVersions;
        this.hasSolution = hasSolution;
        this.conflicts = conflicts;
    }

    /**
     * 获取选中的版本组合
     *
     * @return Map&lt;coordinate, Version&gt;
     */
    public Map<String, Version> getSelectedVersions() {
        return selectedVersions;
    }

    /**
     * 是否有解
     *
     * @return true 表示找到了满足所有约束的版本组合
     */
    public boolean hasSolution() {
        return hasSolution;
    }

    /**
     * 是否有冲突
     *
     * @return true 表示存在无法解决的冲突
     */
    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    /**
     * 获取冲突列表
     *
     * @return 冲突列表，无冲突时返回空列表
     */
    public List<Conflict> getConflicts() {
        return conflicts;
    }

    /**
     * 获取指定包的选中版本
     *
     * @param coordinate 包坐标（groupId:artifactId）
     * @return 选中版本，如果未选中则返回 null
     */
    public Version getVersion(String coordinate) {
        return selectedVersions.get(coordinate);
    }

    @Override
    public String toString() {
        if (hasSolution) {
            return "Solution{" +
                "versions=" + selectedVersions.size() +
                ", " + selectedVersions +
                '}';
        } else {
            return "Solution{no solution, conflicts=" + conflicts + '}';
        }
    }
}
