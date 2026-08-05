package com.pystudio.runner

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.pystudio.IRunnerCallback
import com.pystudio.IRunnerService

class RunnerService : Service() {

    companion object {
        init {
            System.loadLibrary("runner_jni")
        }
    }

    private var callback: IRunnerCallback? = null

    private val binder = object : IRunnerService.Stub() {
        override fun executeScript(scriptPath: String, envId: String, options: android.os.Bundle) {
            nativeRunFile(scriptPath)
        }

        override fun stopExecution(sessionId: String) {
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        override fun registerCallback(cb: IRunnerCallback) {
            callback = cb
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    // Called from JNI
    fun onStdout(output: String) {
        callback?.onStdout("default", output)
    }

    // Called from JNI
    fun onStderr(error: String) {
        callback?.onStderr("default", error)
    }

    // Called from JNI
    fun onCrash(error: String) {
        callback?.onExited("default", -1)
    }

    private external fun nativeInitialize(pythonHome: String): Boolean
    private external fun nativeRunString(code: String): Boolean
    private external fun nativeRunFile(filepath: String): Boolean
    private external fun nativeFinalize()
    private external fun nativeForceGcCollect(): IntArray
}
