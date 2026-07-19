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

package com.ecat.core.Device;

import java.util.List;

/**
 * 设备基础信息持久化接口，镜像 {@code ConfigEntryPersistence} 的契约。
 * 实现：{@link YmlDevicePersistence}（YAML 文件，按 coordinate 分目录）。
 *
 * @author coffee
 */
public interface DevicePersistence {

    /** 递归读取根目录下全部 DeviceRecord（启动时建匹配索引用）。 */
    List<DeviceRecord> loadAll();

    /** 保存（新建）一条设备记录。 */
    void save(DeviceRecord record);

    /** 更新一条设备记录（与 save 同实现，覆盖写）。 */
    void update(DeviceRecord record);

    /** 按 id 删除一条设备记录（真实删除设备时调用；reconfigure 软移除不调此）。 */
    void delete(String id);
}
