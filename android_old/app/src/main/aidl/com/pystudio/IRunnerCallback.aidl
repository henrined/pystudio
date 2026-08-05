package com.pystudio;

interface IRunnerCallback {
    void onStdout(String sessionId, String text);
    void onStderr(String sessionId, String text);
    void onExited(String sessionId, int exitCode);
}
