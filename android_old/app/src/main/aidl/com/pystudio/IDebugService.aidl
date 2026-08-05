package com.pystudio;

import com.pystudio.IDebugCallback;

interface IDebugService {
    boolean initialize(IDebugCallback callback);
    boolean launchProgram(String programPath, in String[] args);
    boolean attachToProcess(int pid);
    String setBreakpoints(String file, in int[] lines);
    boolean continueExecution();
    boolean stepOver();
    boolean stepInto();
    boolean stepOut();
    boolean pauseExecution();
    boolean disconnect();
    String getStackTrace(int threadId);
    String getScopes(int frameId);
    String getVariables(int variablesReference);
    String evaluate(String expression, int frameId);
}
