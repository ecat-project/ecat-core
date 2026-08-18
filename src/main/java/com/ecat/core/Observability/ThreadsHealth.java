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

package com.ecat.core.Observability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * system_health 线程节：线程普查（按名字前缀分组）+ 无名线程点名（14 号架构 §5.4-2）。
 *
 * <p>线程预算是 2c/1G Edge 形态的硬约束（现状 476 根 → 目标 ≤60）——普查按前缀分组后每类
 * 池的规模一眼可见；「Thread-N」默认名意味着某处 new Thread 忘了命名（现状 29%），
 * 点名计数暴露这个卫生问题。churn 率（创建速率）由 {@link SystemHealthService} 差分注入。
 */
final class ThreadsHealth {

    /** JVM 默认线程名（未命名线程的指纹）。 */
    private static final Pattern DEFAULT_NAMED = Pattern.compile("Thread-\\d+");

    private ThreadsHealth() {
    }

    /** 线程普查：总数、按前缀分组计数（降序）、无名线程数。 */
    static Map<String, Object> census() {
        Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
        Map<String, Integer> groups = new LinkedHashMap<>();
        int unnamed = 0;
        for (Thread thread : stacks.keySet()) {
            String name = thread.getName();
            if (DEFAULT_NAMED.matcher(name).matches()) {
                unnamed++;
            }
            groups.merge(groupPrefix(name), 1, Integer::sum);
        }
        List<Map<String, Object>> groupList = new ArrayList<>(groups.size());
        groups.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(e -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("prefix", e.getKey());
                    g.put("count", e.getValue());
                    groupList.add(g);
                });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", stacks.size());
        out.put("groups", groupList);
        out.put("unnamedDefault", unnamed);
        return out;
    }

    /**
     * 完整线程清单 + 栈首帧（诊断用，仅 /system/health/threads 端点）。
     * 栈首帧 = 栈顶元素（当前正在执行什么），全栈不在快照里展开（体积考虑）。
     */
    static Map<String, Object> threadList() {
        Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
        List<Map<String, Object>> list = new ArrayList<>(stacks.size());
        for (Map.Entry<Thread, StackTraceElement[]> e : stacks.entrySet()) {
            Thread t = e.getKey();
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("name", t.getName());
            tm.put("state", t.getState().name());
            tm.put("daemon", t.isDaemon());
            tm.put("priority", t.getPriority());
            StackTraceElement[] frames = e.getValue();
            tm.put("firstFrame", frames != null && frames.length > 0 ? frames[0].toString() : null);
            list.add(tm);
        }
        list.sort(Comparator.comparing(tm -> String.valueOf(tm.get("name"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", list.size());
        out.put("list", list);
        return out;
    }

    /**
     * 名字前缀：剥掉末尾的「-数字」序号（ecat-sched-worker-3 → ecat-sched-worker，
     * pool-1-thread-2 → pool-1-thread）；无数字尾缀的名字原样返回。
     */
    static String groupPrefix(String name) {
        int dash = name.lastIndexOf('-');
        if (dash > 0 && dash < name.length() - 1) {
            String tail = name.substring(dash + 1);
            boolean allDigits = !tail.isEmpty();
            for (int i = 0; i < tail.length() && allDigits; i++) {
                allDigits = Character.isDigit(tail.charAt(i));
            }
            if (allDigits) {
                return name.substring(0, dash);
            }
        }
        return name;
    }
}
