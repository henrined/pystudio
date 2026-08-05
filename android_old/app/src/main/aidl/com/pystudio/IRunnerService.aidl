package com.pystudio;

import com.pystudio.IRunnerCallback;
import android.os.Bundle;

interface IRunnerService {
    void executeScript(String scriptPath, String envId, in Bundle options);
    void stopExecution(String sessionId);
    void registerCallback(IRunnerCallback callback);
}
