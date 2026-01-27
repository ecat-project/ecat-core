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

package com.ecat.core.Integration;

import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

/**
 * 系统重启管理器
 *
 * <p>负责优雅地重启 ECAT Core 系统。
 * 重启机制通过调用 {@code System.exit(0)} 实现，
 * 启动脚本（start-ecat.sh / start-ecat.bat）会检测并自动重启进程。
 *
 * <h3>跨平台支持</h3>
 * <ul>
 *   <li><b>Linux</b>: start-ecat.sh 使用 while true 循环自动重启</li>
 *   <li><b>Windows</b>: start-ecat.bat 使用类似机制</li>
 * </ul>
 *
 * @author coffee
 */
public class SystemRestarter {

    private final Log log;

    /**
     * 默认重启延迟（秒）
     */
    private static final int DEFAULT_DELAY_SECONDS = 5;

    /**
     * 最小重启延迟（秒）
     */
    private static final int MIN_DELAY_SECONDS = 1;

    /**
     * 最大重启延迟（秒）
     */
    private static final int MAX_DELAY_SECONDS = 300;

    public SystemRestarter() {
        this.log = LogFactory.getLogger(getClass());
    }

    /**
     * 调度系统重启
     *
     * <p>在指定的延迟时间后执行系统重启。
     * 重启通过调用 {@code System.exit(0)} 实现，启动脚本会检测并自动重启进程。
     *
     * @param delaySeconds 延迟秒数（1-300）
     * @throws IllegalArgumentException 如果延迟时间超出范围
     */
    public void scheduleRestart(int delaySeconds) {
        if (delaySeconds < MIN_DELAY_SECONDS || delaySeconds > MAX_DELAY_SECONDS) {
            throw new IllegalArgumentException(
                "重启延迟必须在 " + MIN_DELAY_SECONDS + " 到 " + MAX_DELAY_SECONDS + " 秒之间");
        }

        log.info("系统重启已安排，将在 {} 秒后执行", delaySeconds);
        log.warn("系统即将重启，所有未保存的数据可能会丢失");

        Thread restartThread = new Thread(() -> {
            try {
                for (int i = delaySeconds; i > 0; i--) {
                    if (i <= 10 || i % 10 == 0) {
                        log.warn("系统将在 {} 秒后重启...", i);
                    }
                    Thread.sleep(1000);
                }
                performRestart();
            } catch (InterruptedException e) {
                log.warn("重启调度被中断");
                Thread.currentThread().interrupt();
            }
        }, "SystemRestarter-Thread");

        // 设置为非守护线程，确保重启能够执行
        restartThread.setDaemon(false);
        restartThread.start();
    }

    /**
     * 使用默认延迟（5秒）调度系统重启
     */
    public void scheduleRestart() {
        scheduleRestart(DEFAULT_DELAY_SECONDS);
    }

    /**
     * 立即执行系统重启
     *
     * <p>通过调用 {@code System.exit(0)} 终止当前进程。
     * 启动脚本会检测退出码 0 并自动重启进程。
     */
    public void performRestart() {
        log.info("正在执行系统重启...");
        log.warn("System.exit(0) 被调用，启动脚本应自动重启进程");

        // 使用 exit code 0 表示正常退出，触发脚本重启
        System.exit(0);
    }

    /**
     * 获取默认重启延迟秒数
     *
     * @return 默认延迟秒数
     */
    public static int getDefaultDelaySeconds() {
        return DEFAULT_DELAY_SECONDS;
    }

    /**
     * 获取最小重启延迟秒数
     *
     * @return 最小延迟秒数
     */
    public static int getMinDelaySeconds() {
        return MIN_DELAY_SECONDS;
    }

    /**
     * 获取最大重启延迟秒数
     *
     * @return 最大延迟秒数
     */
    public static int getMaxDelaySeconds() {
        return MAX_DELAY_SECONDS;
    }
}
