package com.ecat.core.Device;

import com.ecat.core.EcatCore;

public interface DeviceControl {

    void load(EcatCore core);

    void init();

    void start();

    void stop();

    void release();
}
