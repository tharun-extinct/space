package com.parallelverse.controller.runtime;

import android.os.Bundle;

/** Internal, typed IPC. Only the host application's non-exported service may bind it. */
interface IRuntimeService {
    String createInstance(String packageName, String displayName);
    List<Bundle> listInstances();
    void startInstance(String instanceId);
    void stopInstance(String instanceId);
    String getInstanceState(String instanceId);
}
