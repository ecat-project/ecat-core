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

package com.ecat.core.Upgrade;

/**
 * 备份钩子:升级窗程序的广播面——平台只广播时机,数据/路径/方式全归实现者。
 *
 * <p>应用场景:版本升级在硬停机窗内进行,窗程序在数据迁移未跑之前广播
 * {@link #backup()},让每个实现者保存自己认定的必须保有的数据(宿主全库、
 * 自管集成的写句柄介质等);窗口失败回滚时广播 {@link #restore()},各实现者
 * 把自己恢复到升级前状态。core 不持有任何数据库知识:备份什么、备到哪、
 * 用什么方式,全部由实现者自决——不实现本接口=明示不需要备份(缺席语义合法)。</p>
 *
 * <p>失败语义:两方法都是严格失败面——{@link #backup()} 抛出=升级窗口中止
 * (未备份就迁移,失败后无从恢复,宁可不开窗);{@link #restore()} 抛出=
 * 显式上报恢复失败,不得吞异常伪装成功。静默与解冻(介质关写句柄、宿主断
 * 连接池等冻结措施)由实现内部 try/finally 自理,失败处理局部化,不外溢到窗程序。</p>
 *
 * <p>单槽语义:每实现者一个备份位,零 ID 零协商——新窗口的备份覆盖旧槽;
 * 恢复仅对刚结束且备份成功过的窗口有效(窗程序账本驱动派发,只回调
 * {@link #backup()} 成功过的参与者,防止未备份者从空槽恢复=数据事故);
 * 窗口成功走完=旧备份自然作废。两个实现者的恢复范围重叠=部署错误,机制不仲裁。</p>
 *
 * @author coffee
 */
public interface BackupHook {

    /**
     * 备份:升级窗口已开始(写路径全静)、数据迁移未跑——保存你自认必须保的。
     * 失败抛出=窗口中止,平台不迁移、走回滚分支。
     */
    void backup();

    /**
     * 恢复:窗口回滚——恢复自己到升级前状态。失败抛出=显式上报,禁止吞异常伪装成功。
     */
    void restore();
}
