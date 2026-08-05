package com.pystudio.runner

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.pystudio.IRunnerCallback
import com.pystudio.IRunnerService

class RunnerClient(private val context: Context) {

    interface Listener {
        fun onStdout(text: String)
        fun onStderr(text: String)
        fun onCrash(error: String)
        fun onConnected()
        fun onDisconnected()
    }

    private var runnerService: IRunnerService? = null
    private var listener: Listener? = null
    private var isBound = false

    private val runnerCallback = object : IRunnerCallback.Stub() {
        override fun onStdout(sessionId: String, text: String) {
            listener?.onStdout(text)
        }

        override fun onStderr(sessionId: String, text: String) {
            listener?.onStderr(text)
        }

        override fun onExited(sessionId: String, exitCode: Int) {
            listener?.onCrash("Exited with code $exitCode")
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("RunnerClient", "Service connected")
            runnerService = IRunnerService.Stub.asInterface(service)
            isBound = true
            listener?.onConnected()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("RunnerClient", "Service disconnected")
            runnerService = null
            isBound = false
            listener?.onDisconnected()
        }
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun connect() {
        val intent = Intent(context, RunnerService::class.java)
        // Bind to the service in the isolated process
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun disconnect() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            runnerService = null
        }
    }

    fun executeScript(scriptPath: String, envId: String, options: android.os.Bundle): Boolean {
        return try {
            runnerService?.executeScript(scriptPath, envId, options)
            true
        } catch (e: Exception) {
            Log.e("RunnerClient", "Failed to execute script: ${e.message}")
            false
        }
    }

    fun stopExecution(sessionId: String) {
        try {
            runnerService?.stopExecution(sessionId)
        } catch (e: Exception) {
            Log.e("RunnerClient", "Failed to stop execution: ${e.message}")
        }
    }

    fun registerCallback(): Boolean {
        return try {
            runnerService?.registerCallback(runnerCallback)
            true
        } catch (e: Exception) {
            Log.e("RunnerClient", "Failed to register callback: ${e.message}")
            false
        }
    }
}
