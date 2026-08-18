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

package com.ecat.core.Task;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.Utils.Mdc.MdcContext;

/**
 * 调度 v2 车道隔离红测试：一个集成车道的 10s 级阻塞任务不得拖停其他车道的周期任务。
 *
 * <p>复现机制（A4 阶段已证，见 serial 仓 SerialTransactionStrategyAsyncContractTest 引用的
 * bug-record-20260619221536「第四次 LIVE 复现」）：旧引擎是全局 2 线程 ScheduledThreadPool，
 * 两个车道 A 阻塞任务即可占满全部线程 → 所有集成（含车道 B）的周期轮询整体停摆。
 *
 * <p>断言口径：车道 B 以 200ms 周期执行，2s 窗口内至少 8 次到位（每次允许至多 2× 周期漂移，
 * 8 次 × 250ms 均摊 = 2s）。旧引擎上车道 B 一次都不会执行（红）；车道引擎上车道 A 只占用
 * 自己车道的一个 worker，车道 B 照常轮询（绿）。
 *
 * <p>同步纪律：全程 CountDownLatch 确定性同步，无 Thread.sleep。
 */
public class SchedulerLaneIsolationTest {

    private static final String LANE_A_COORDINATE = "com.ecat:integration-red-blocker";
    private static final String LANE_B_COORDINATE = "com.ecat:integration-red-healthy";

    private TaskManager taskManager;
    /** 控制车道 A 两个假 IO 任务何时放行（默认不放行 = 模拟 10s 级阻塞 IO）。 */
    private final CountDownLatch releaseBlockers = new CountDownLatch(1);

    @After
    public void tearDown() {
        MDC.remove(MdcContext.INTEGRATION_COORDINATE_KEY);
        releaseBlockers.countDown();
        if (taskManager != null) {
            taskManager.shutdownAll();
            taskManager = null;
        }
    }

    @Test(timeout = 20000)
    public void blockerTasksInLaneADoNotStallLaneBPeriodic() throws Exception {
        taskManager = new TaskManager();
        ScheduledExecutorService scheduler = taskManager.getMdcScheduledExecutorService();

        CountDownLatch bReachedEightExecutions = new CountDownLatch(8);

        // 车道 A：两个阻塞任务（latch 控制的假 IO）——同车道串行，只允许占用本车道容量
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, LANE_A_COORDINATE);
        scheduler.execute(blockingIoTask());
        scheduler.execute(blockingIoTask());
        MDC.remove(MdcContext.INTEGRATION_COORDINATE_KEY);

        // 车道 B：200ms 周期任务，健康设备轮询
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, LANE_B_COORDINATE);
        scheduler.scheduleAtFixedRate(bReachedEightExecutions::countDown, 0, 200, TimeUnit.MILLISECONDS);
        MDC.remove(MdcContext.INTEGRATION_COORDINATE_KEY);

        // 2s 窗口内车道 B 到位 8 次；旧引擎上两个阻塞任务占满 2 线程 → await 超时返回 false（红）
        boolean reached = bReachedEightExecutions.await(2, TimeUnit.SECONDS);
        assertThat("车道 B 周期任务被车道 A 阻塞任务拖停（调度器缺乏车道隔离）", reached, is(true));
    }

    /** 模拟阻塞 IO 的任务：挂在外部 latch 上直到放行或被中断（硬超时/shutdown 路径需要可中断）。 */
    private Runnable blockingIoTask() {
        return () -> {
            try {
                releaseBlockers.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
