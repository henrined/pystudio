package com.pystudio.bridge

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.pystudio.runner.RunnerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class PyStudioRuntimeBridgeModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    data class RunnerSession(
        val client: RunnerClient,
        val envId: String,
        var pid: Int,
        val startTime: Long,
        var state: String
    )
    
    private val sessions = mutableMapOf<String, RunnerSession>()

    override fun getName(): String = "PyStudioRuntimeBridge"

    private fun sendEvent(eventName: String, params: WritableMap) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, params)
    }

    @ReactMethod
    fun run(scriptPath: String, options: ReadableMap?, promise: Promise) {
        val envId = options?.getString("envId") ?: "default"
        val pythonVersion = options?.getString("pythonVersion") ?: "3.11"
        val sessionId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        scope.launch {
            try {
                val intent = Intent(reactContext, com.pystudio.runner.RunnerService::class.java).apply {
                    putExtra("scriptPath", scriptPath)
                    putExtra("envId", envId)
                    putExtra("pythonVersion", pythonVersion)
                }
                reactContext.startService(intent)

                val client = RunnerClient(reactContext)
                val session = RunnerSession(client, envId, -1, startTime, "starting")
                sessions[sessionId] = session
                
                client.setListener(object : RunnerClient.Listener {
                    override fun onStdout(text: String) {
                        val params = Arguments.createMap().apply {
                            putString("sessionId", sessionId)
                            putString("text", text)
                        }
                        sendEvent("runtimeStdout", params)
                    }

                    override fun onStderr(text: String) {
                        val params = Arguments.createMap().apply {
                            putString("sessionId", sessionId)
                            putString("text", text)
                        }
                        sendEvent("runtimeStderr", params)
                    }

                    override fun onCrash(error: String) {
                        val params = Arguments.createMap().apply {
                            putString("sessionId", sessionId)
                            putInt("exitCode", -1)
                            putString("error", error)
                        }
                        sendEvent("runtimeExited", params)
                        client.disconnect()
                        sessions.remove(sessionId)
                    }

                    override fun onConnected() {
                        client.registerCallback()
                        client.executeScript(scriptPath, envId, android.os.Bundle())
                        val pid = -1 // Not implemented in RunnerClient yet
                        session.pid = pid
                        session.state = "running"
                        
                        val result = Arguments.createMap().apply {
                            putString("sessionId", sessionId)
                            putInt("pid", pid)
                        }
                        promise.resolve(result)
                    }

                    override fun onDisconnected() {
                        val params = Arguments.createMap().apply {
                            putString("sessionId", sessionId)
                            putInt("exitCode", 0)
                        }
                        sendEvent("runtimeExited", params)
                        sessions.remove(sessionId)
                    }
                })
                client.connect()
            } catch (e: Exception) {
                promise.reject("RUN_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun stopExecution(sessionId: String, promise: Promise) {
        scope.launch {
            try {
                val session = sessions[sessionId]
                if (session != null) {
                    session.client.stopExecution(sessionId)
                    session.client.disconnect()
                    sessions.remove(sessionId)
                    promise.resolve(true)
                } else {
                    promise.reject("STOP_ERROR", "Session not found")
                }
            } catch (e: Exception) {
                promise.reject("STOP_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun poolStatus(promise: Promise) {
        scope.launch {
            try {
                val am = reactContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                var warmProcesses = 0
                var memoryUsageMB = 0.0

                val pids = sessions.values.map { it.pid }.filter { it > 0 }.toIntArray()
                if (pids.isNotEmpty()) {
                    warmProcesses = pids.size
                    val memInfos = am.getProcessMemoryInfo(pids)
                    for (info in memInfos) {
                        memoryUsageMB += info.totalPss / 1024.0
                    }
                }

                val result = Arguments.createMap().apply {
                    putInt("warmProcesses", warmProcesses)
                    putInt("targetSize", 5)
                    putDouble("memoryUsageMB", memoryUsageMB)
                }
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("POOL_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun forceGcCollect(envId: String, promise: Promise) {
        scope.launch {
            try {
                val session = sessions.values.find { it.envId == envId }
                if (session != null) {
                    val result = Arguments.createMap().apply {
                        putInt("collected", 0)
                        putInt("uncollectable", 0)
                    }
                    promise.resolve(result)
                } else {
                    promise.reject("GC_ERROR", "Env not found")
                }
            } catch (e: Exception) {
                promise.reject("GC_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getRunningProcesses(promise: Promise) {
        scope.launch {
            try {
                val array = Arguments.createArray()
                val currentTime = System.currentTimeMillis()
                for ((sid, info) in sessions) {
                    val map = Arguments.createMap().apply {
                        putString("sessionId", sid)
                        putInt("pid", info.pid)
                        putString("state", info.state)
                        putDouble("durationMs", (currentTime - info.startTime).toDouble())
                    }
                    array.pushMap(map)
                }
                promise.resolve(array)
            } catch (e: Exception) {
                promise.reject("PROCESS_ERROR", e.message, e)
            }
        }
    }
}
