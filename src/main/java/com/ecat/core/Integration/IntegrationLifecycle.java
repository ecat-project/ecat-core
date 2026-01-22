package com.ecat.core.Integration;

import com.ecat.core.EcatCore;

public interface IntegrationLifecycle {

    void onLoad(EcatCore core, IntegrationLoadOption loadOption);

    void onInit();

    void onStart();

    void onPause();

    void onRelease();
}    