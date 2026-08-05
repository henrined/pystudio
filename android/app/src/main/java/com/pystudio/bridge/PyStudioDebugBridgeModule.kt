package com.pystudio.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.pystudio.IDebugCallback
import com.pystudio.IDebugService
import com.pystudio.debug.DebugService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class PyStudioDebugBridgeModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var debugService: IDebugService? = null
    private var isBound = false

    private val debugCallback = object : IDebugCallback.Stub() {
        override fun onDapEvent(event: String, jsonPayload: String) {
            try {
                val targetEvent = when (event) {
                    "stopped", "debugStopped" -> "debugStopped"
                    "exited", "debugExited" -> "debugExited"
                    "output", "debugOutput" -> "debugOutput"
                    "breakpoint", "breakpointHit", "debugBreakpointHit" -> "debugBreakpointHit"
                    else -> event
                }

                val eventMap = Arguments.createMap()
                eventMap.putString("event", targetEvent)

                if (jsonPayload.isNotEmpty() && jsonPayload != "{}") {
                    try {
                        val trimmed = jsonPayload.trim()
                        if (trimmed.startsWith("{")) {
                            eventMap.putMap("body", jsonToWritableMap(JSONObject(trimmed)))
                        } else if (trimmed.startsWith("[")) {
                            eventMap.putArray("body", jsonToWritableArray(JSONArray(trimmed)))
                        } else {
                            eventMap.putString("body", jsonPayload)
                        }
                    } catch (e: Exception) {
                        eventMap.putString("body", jsonPayload)
                    }
                } else {
                    eventMap.putMap("body", Arguments.createMap())
                }

                reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(targetEvent, eventMap)
            } catch (e: Exception) {
                Log.e("PyStudioDebugBridge", "Failed to emit DAP event: $event", e)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            debugService = IDebugService.Stub.asInterface(service)
            isBound = true
            try {
                debugService?.initialize(debugCallback)
            } catch (e: Exception) {
                Log.e("PyStudioDebugBridge", "Error initializing DebugService callback", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            debugService = null
            isBound = false
        }
    }

    override fun getName(): String = "PyStudioDebugBridge"

    override fun initialize() {
        super.initialize()
        bindDebugService()
    }

    override fun onCatalystInstanceDestroy() {
        if (isBound) {
            try {
                reactContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e("PyStudioDebugBridge", "Failed to unbind DebugService", e)
            }
            isBound = false
        }
        super.onCatalystInstanceDestroy()
    }

    private fun bindDebugService() {
        if (isBound && debugService != null) return
        try {
            val intent = Intent(reactContext, DebugService::class.java)
            reactContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("PyStudioDebugBridge", "Failed to bind DebugService", e)
        }
    }

    private fun getServiceOrReject(promise: Promise): IDebugService? {
        val service = debugService
        if (service == null) {
            bindDebugService()
            promise.reject("SERVICE_NOT_CONNECTED", "DebugService is not connected")
            return null
        }
        return service
    }

    @ReactMethod
    fun launch(config: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val programPath = if (config.hasKey("programPath")) {
                    config.getString("programPath") ?: ""
                } else if (config.hasKey("program")) {
                    config.getString("program") ?: ""
                } else if (config.hasKey("path")) {
                    config.getString("path") ?: ""
                } else {
                    ""
                }

                if (programPath.isEmpty()) {
                    promise.reject("INVALID_ARGUMENT", "Missing program or programPath in launch config")
                    return@launch
                }

                val argsList = mutableListOf<String>()
                if (config.hasKey("args") && !config.isNull("args")) {
                    val argsArray = config.getArray("args")
                    if (argsArray != null) {
                        for (i in 0 until argsArray.size()) {
                            argsArray.getString(i)?.let { argsList.add(it) }
                        }
                    }
                }

                val success = service.launchProgram(programPath, argsList.toTypedArray())
                if (success) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    res.putString("programPath", programPath)
                    promise.resolve(res)
                } else {
                    promise.reject("LAUNCH_FAILED", "DebugService failed to launch program: $programPath")
                }
            } catch (e: Exception) {
                promise.reject("LAUNCH_ERROR", e.message ?: "Error launching debug program", e)
            }
        }
    }

    @ReactMethod
    fun attach(config: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val pid = if (config.hasKey("pid")) {
                    config.getInt("pid")
                } else if (config.hasKey("processId")) {
                    config.getInt("processId")
                } else {
                    -1
                }

                if (pid <= 0) {
                    promise.reject("INVALID_ARGUMENT", "Invalid or missing pid/processId in attach config")
                    return@launch
                }

                val success = service.attachToProcess(pid)
                if (success) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    res.putInt("pid", pid)
                    promise.resolve(res)
                } else {
                    promise.reject("ATTACH_FAILED", "DebugService failed to attach to process PID $pid")
                }
            } catch (e: Exception) {
                promise.reject("ATTACH_ERROR", e.message ?: "Error attaching to process", e)
            }
        }
    }

    @ReactMethod
    fun setBreakpoints(params: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val file = if (params.hasKey("file")) {
                    params.getString("file") ?: ""
                } else if (params.hasKey("path")) {
                    params.getString("path") ?: ""
                } else if (params.hasKey("source") && params.getMap("source")?.hasKey("path") == true) {
                    params.getMap("source")?.getString("path") ?: ""
                } else {
                    ""
                }

                if (file.isEmpty()) {
                    promise.reject("INVALID_ARGUMENT", "Missing file or path in params")
                    return@launch
                }

                val lineList = mutableListOf<Int>()
                if (params.hasKey("lines") && !params.isNull("lines")) {
                    val linesArr = params.getArray("lines")
                    if (linesArr != null) {
                        for (i in 0 until linesArr.size()) {
                            lineList.add(linesArr.getInt(i))
                        }
                    }
                } else if (params.hasKey("breakpoints") && !params.isNull("breakpoints")) {
                    val bpsArr = params.getArray("breakpoints")
                    if (bpsArr != null) {
                        for (i in 0 until bpsArr.size()) {
                            val bpMap = bpsArr.getMap(i)
                            if (bpMap != null && bpMap.hasKey("line")) {
                                lineList.add(bpMap.getInt("line"))
                            }
                        }
                    }
                }

                val jsonResult = service.setBreakpoints(file, lineList.toIntArray())
                if (jsonResult.isNotEmpty() && jsonResult != "[]") {
                    val trimmed = jsonResult.trim()
                    val parsed = if (trimmed.startsWith("[")) {
                        jsonToWritableArray(JSONArray(trimmed))
                    } else if (trimmed.startsWith("{")) {
                        val arr = Arguments.createArray()
                        arr.pushMap(jsonToWritableMap(JSONObject(trimmed)))
                        arr
                    } else {
                        promise.reject("INVALID_DATA", "Invalid JSON format for breakpoints")
                        return@launch
                    }
                    promise.resolve(parsed)
                } else {
                    promise.reject("EMPTY_RESPONSE", "DebugService returned empty breakpoints list")
                }
            } catch (e: Exception) {
                promise.reject("SET_BREAKPOINTS_ERROR", e.message ?: "Error setting breakpoints", e)
            }
        }
    }

    @ReactMethod
    fun continue(threadId: Int, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val success = service.continueExecution()
                if (success) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    res.putInt("threadId", threadId)
                    promise.resolve(res)
                } else {
                    promise.reject("CONTINUE_FAILED", "Failed to continue execution on thread $threadId")
                }
            } catch (e: Exception) {
                promise.reject("CONTINUE_ERROR", e.message ?: "Error continuing execution", e)
            }
        }
    }

    @ReactMethod
    fun stepOver(threadId: Int, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val success = service.stepOver()
                if (success) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    res.putInt("threadId", threadId)
                    promise.resolve(res)
                } else {
                    promise.reject("STEPOVER_FAILED", "Failed to step over on thread $threadId")
                }
            } catch (e: Exception) {
                promise.reject("STEPOVER_ERROR", e.message ?: "Error stepping over", e)
            }
        }
    }

    @ReactMethod
    fun stepInto(threadId: Int, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val success = service.stepInto()
                if (success) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    res.putInt("threadId", threadId)
                    promise.resolve(res)
                } else {
                    promise.reject("STEPINTO_FAILED", "Failed to step into on thread $threadId")
                }
            } catch (e: Exception) {
                promise.reject("STEPINTO_ERROR", e.message ?: "Error stepping into", e)
            }
        }
    }

    @ReactMethod
    fun stepOut(threadId: Int, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val success = service.stepOut()
                if (success) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    res.putInt("threadId", threadId)
                    promise.resolve(res)
                } else {
                    promise.reject("STEPOUT_FAILED", "Failed to step out on thread $threadId")
                }
            } catch (e: Exception) {
                promise.reject("STEPOUT_ERROR", e.message ?: "Error stepping out", e)
            }
        }
    }

    @ReactMethod
    fun pause(threadId: Int, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val success = service.pauseExecution()
                if (success) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    res.putInt("threadId", threadId)
                    promise.resolve(res)
                } else {
                    promise.reject("PAUSE_FAILED", "Failed to pause execution on thread $threadId")
                }
            } catch (e: Exception) {
                promise.reject("PAUSE_ERROR", e.message ?: "Error pausing execution", e)
            }
        }
    }

    @ReactMethod
    fun disconnect(promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val success = service.disconnect()
                if (success) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    promise.resolve(res)
                } else {
                    promise.reject("DISCONNECT_FAILED", "Failed to disconnect debug session")
                }
            } catch (e: Exception) {
                promise.reject("DISCONNECT_ERROR", e.message ?: "Error disconnecting debug session", e)
            }
        }
    }

    @ReactMethod
    fun getStackTrace(threadId: Int, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val jsonResult = service.getStackTrace(threadId)
                val trimmed = jsonResult.trim()
                val parsedArray = if (trimmed.startsWith("[")) {
                    jsonToWritableArray(JSONArray(trimmed))
                } else if (trimmed.startsWith("{")) {
                    val arr = Arguments.createArray()
                    arr.pushMap(jsonToWritableMap(JSONObject(trimmed)))
                    arr
                } else {
                    promise.reject("INVALID_DATA", "Invalid JSON format for stack trace")
                    return@launch
                }
                promise.resolve(parsedArray)
            } catch (e: Exception) {
                promise.reject("GET_STACKTRACE_ERROR", e.message ?: "Error getting stack trace", e)
            }
        }
    }

    @ReactMethod
    fun getVariables(variablesReference: Int, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val jsonResult = service.getVariables(variablesReference)
                val trimmed = jsonResult.trim()
                val parsedArray = if (trimmed.startsWith("[")) {
                    jsonToWritableArray(JSONArray(trimmed))
                } else if (trimmed.startsWith("{")) {
                    val arr = Arguments.createArray()
                    arr.pushMap(jsonToWritableMap(JSONObject(trimmed)))
                    arr
                } else {
                    promise.reject("INVALID_DATA", "Invalid JSON format for variables")
                    return@launch
                }
                promise.resolve(parsedArray)
            } catch (e: Exception) {
                promise.reject("GET_VARIABLES_ERROR", e.message ?: "Error getting variables", e)
            }
        }
    }

    @ReactMethod
    fun getScopes(frameId: Int, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val jsonResult = service.getScopes(frameId)
                val trimmed = jsonResult.trim()
                val parsedArray = if (trimmed.startsWith("[")) {
                    jsonToWritableArray(JSONArray(trimmed))
                } else if (trimmed.startsWith("{")) {
                    val arr = Arguments.createArray()
                    arr.pushMap(jsonToWritableMap(JSONObject(trimmed)))
                    arr
                } else {
                    promise.reject("INVALID_DATA", "Invalid JSON format for scopes")
                    return@launch
                }
                promise.resolve(parsedArray)
            } catch (e: Exception) {
                promise.reject("GET_SCOPES_ERROR", e.message ?: "Error getting scopes", e)
            }
        }
    }

    @ReactMethod
    fun evaluate(expression: String, frameId: Int, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val jsonResult = service.evaluate(expression, frameId)
                val trimmed = jsonResult.trim()
                val parsedMap = if (trimmed.startsWith("{")) {
                    jsonToWritableMap(JSONObject(trimmed))
                } else {
                    val map = Arguments.createMap()
                    map.putString("result", jsonResult)
                    map
                }
                promise.resolve(parsedMap)
            } catch (e: Exception) {
                promise.reject("EVALUATE_ERROR", e.message ?: "Error evaluating expression", e)
            }
        }
    }

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for RN DeviceEventManagerModule
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for RN DeviceEventManagerModule
    }

    private fun jsonToWritableMap(jsonObj: JSONObject): WritableMap {
        val map = Arguments.createMap()
        val keys = jsonObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = jsonObj.get(key)) {
                is JSONObject -> map.putMap(key, jsonToWritableMap(value))
                is JSONArray -> map.putArray(key, jsonToWritableArray(value))
                is Boolean -> map.putBoolean(key, value)
                is Int -> map.putInt(key, value)
                is Long -> map.putDouble(key, value.toDouble())
                is Double -> map.putDouble(key, value)
                is String -> map.putString(key, value)
                JSONObject.NULL -> map.putNull(key)
                else -> map.putString(key, value.toString())
            }
        }
        return map
    }

    private fun jsonToWritableArray(jsonArray: JSONArray): WritableArray {
        val array = Arguments.createArray()
        for (i in 0 until jsonArray.length()) {
            when (val value = jsonArray.get(i)) {
                is JSONObject -> array.pushMap(jsonToWritableMap(value))
                is JSONArray -> array.pushArray(jsonToWritableArray(value))
                is Boolean -> array.pushBoolean(value)
                is Int -> array.pushInt(value)
                is Long -> array.pushDouble(value.toDouble())
                is Double -> array.pushDouble(value)
                is String -> array.pushString(value)
                JSONObject.NULL -> array.pushNull()
                else -> array.pushString(value.toString())
            }
        }
        return array
    }
}
