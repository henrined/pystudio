package com.pystudio.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.pystudio.ILspCallback
import com.pystudio.ILspService
import com.pystudio.lsp.LspProtocol
import com.pystudio.lsp.LspService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PyStudioLSPBridgeModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lspService: ILspService? = null
    private var isBound = false
    private val seq = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, Promise>()

    private val lspCallback = object : ILspCallback.Stub() {
        override fun onMessage(jsonRpcMessage: String) {
            scope.launch {
                try {
                    val json = JSONObject(jsonRpcMessage)
                    if (json.has("id") && !json.isNull("id")) {
                        val id = json.getInt("id")
                        val pendingPromise = pendingRequests.remove(id)
                        if (pendingPromise != null) {
                            if (json.has("error") && !json.isNull("error")) {
                                val errObj = json.getJSONObject("error")
                                val message = errObj.optString("message", "LSP Request Failed")
                                val code = errObj.optInt("code", -1)
                                pendingPromise.reject("LSP_ERROR_$code", message)
                            } else if (json.has("result")) {
                                when (val result = json.get("result")) {
                                    is JSONObject -> pendingPromise.resolve(jsonToWritableMap(result))
                                    is JSONArray -> pendingPromise.resolve(jsonToWritableArray(result))
                                    is Boolean -> pendingPromise.resolve(result)
                                    is Int -> pendingPromise.resolve(result)
                                    is Double -> pendingPromise.resolve(result)
                                    is String -> pendingPromise.resolve(result)
                                    JSONObject.NULL -> pendingPromise.resolve(Arguments.createMap())
                                    else -> pendingPromise.resolve(result.toString())
                                }
                            } else {
                                pendingPromise.resolve(Arguments.createMap())
                            }
                        }
                    } else if (json.has("method")) {
                        val method = json.getString("method")
                        val params = json.opt("params")
                        when (method) {
                            "textDocument/publishDiagnostics" -> {
                                val map = Arguments.createMap()
                                if (params is JSONObject) {
                                    map.putMap("params", jsonToWritableMap(params))
                                } else {
                                    map.putMap("params", Arguments.createMap())
                                }
                                reactContext
                                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                                    .emit("lspDiagnostics", map)
                            }
                            "window/logMessage" -> {
                                val map = Arguments.createMap()
                                if (params is JSONObject) {
                                    map.putMap("params", jsonToWritableMap(params))
                                } else {
                                    map.putMap("params", Arguments.createMap())
                                }
                                reactContext
                                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                                    .emit("lspLogMessage", map)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PyStudioLSPBridge", "Error parsing incoming JSON-RPC message", e)
                }
            }
        }

        override fun onError(errorMessage: String?) {
            Log.e("PyStudioLSPBridge", "LSP Service Error: $errorMessage")
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            lspService = ILspService.Stub.asInterface(service)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            lspService = null
            isBound = false
        }
    }

    override fun getName(): String = "PyStudioLSPBridge"

    override fun initialize() {
        super.initialize()
        bindLspService()
    }

    override fun onCatalystInstanceDestroy() {
        if (isBound) {
            try {
                reactContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e("PyStudioLSPBridge", "Failed to unbind LspService", e)
            }
            isBound = false
        }
        super.onCatalystInstanceDestroy()
    }

    private fun bindLspService() {
        if (isBound && lspService != null) return
        try {
            val intent = Intent(reactContext, LspService::class.java)
            reactContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("PyStudioLSPBridge", "Failed to bind LspService", e)
        }
    }

    private fun getServiceOrReject(promise: Promise): ILspService? {
        val service = lspService
        if (service == null) {
            bindLspService()
            promise.reject("SERVICE_NOT_CONNECTED", "LspService is not bound or connected")
            return null
        }
        return service
    }

    @ReactMethod
    fun initialize(options: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val language = if (options.hasKey("language")) options.getString("language") ?: "python" else "python"
                val serverPath = if (options.hasKey("serverPath")) options.getString("serverPath") ?: "" else ""
                val workspacePath = if (options.hasKey("workspacePath")) options.getString("workspacePath") ?: "" else ""

                val started = service.startServer(language, serverPath, workspacePath, lspCallback)
                if (!started) {
                    promise.reject("LSP_START_FAILED", "Failed to start LSP server for $language at $serverPath")
                    return@launch
                }

                val requestId = seq.getAndIncrement()
                pendingRequests[requestId] = promise

                val initReq = LspProtocol.buildInitializeRequest(requestId, workspacePath)
                val sent = service.sendMessage(initReq)
                if (!sent) {
                    pendingRequests.remove(requestId)
                    promise.reject("LSP_SEND_FAILED", "Failed to send initialize request to LSP server")
                }
            } catch (e: Exception) {
                promise.reject("INITIALIZE_ERROR", e.message ?: "Error initializing LSP bridge", e)
            }
        }
    }

    @ReactMethod
    fun didOpen(params: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val uri = params.getString("uri") ?: ""
                val languageId = params.getString("languageId") ?: "python"
                val version = if (params.hasKey("version")) params.getInt("version") else 1
                val text = params.getString("text") ?: ""

                val notificationJson = LspProtocol.buildDidOpenNotification(uri, languageId, version, text)
                val sent = service.sendMessage(notificationJson)
                if (sent) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    res.putString("uri", uri)
                    promise.resolve(res)
                } else {
                    promise.reject("DID_OPEN_FAILED", "Failed to send didOpen notification for $uri")
                }
            } catch (e: Exception) {
                promise.reject("DID_OPEN_ERROR", e.message ?: "Error sending didOpen notification", e)
            }
        }
    }

    @ReactMethod
    fun didChange(params: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val uri = params.getString("uri") ?: ""
                val version = if (params.hasKey("version")) params.getInt("version") else 1
                val text = params.getString("text") ?: ""

                val req = JSONObject()
                req.put("jsonrpc", "2.0")
                req.put("method", "textDocument/didChange")

                val paramsObj = JSONObject()
                val textDocObj = JSONObject()
                textDocObj.put("uri", uri)
                textDocObj.put("version", version)
                paramsObj.put("textDocument", textDocObj)

                val changesArr = JSONArray()
                val changeObj = JSONObject()
                changeObj.put("text", text)
                changesArr.put(changeObj)
                paramsObj.put("contentChanges", changesArr)

                req.put("params", paramsObj)

                val sent = service.sendMessage(req.toString())
                if (sent) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    res.putString("uri", uri)
                    promise.resolve(res)
                } else {
                    promise.reject("DID_CHANGE_FAILED", "Failed to send didChange notification for $uri")
                }
            } catch (e: Exception) {
                promise.reject("DID_CHANGE_ERROR", e.message ?: "Error sending didChange notification", e)
            }
        }
    }

    @ReactMethod
    fun didClose(params: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val uri = params.getString("uri") ?: ""

                val req = JSONObject()
                req.put("jsonrpc", "2.0")
                req.put("method", "textDocument/didClose")

                val paramsObj = JSONObject()
                val textDocObj = JSONObject()
                textDocObj.put("uri", uri)
                paramsObj.put("textDocument", textDocObj)
                req.put("params", paramsObj)

                val sent = service.sendMessage(req.toString())
                if (sent) {
                    val res = Arguments.createMap()
                    res.putBoolean("success", true)
                    res.putString("uri", uri)
                    promise.resolve(res)
                } else {
                    promise.reject("DID_CLOSE_FAILED", "Failed to send didClose notification for $uri")
                }
            } catch (e: Exception) {
                promise.reject("DID_CLOSE_ERROR", e.message ?: "Error sending didClose notification", e)
            }
        }
    }

    @ReactMethod
    fun completion(params: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val uri = params.getString("uri") ?: ""
                val line = if (params.hasKey("line")) params.getInt("line") else 0
                val character = if (params.hasKey("character")) params.getInt("character") else 0

                val requestId = seq.getAndIncrement()
                pendingRequests[requestId] = promise

                val reqJson = LspProtocol.buildCompletionRequest(requestId, uri, line, character)
                val sent = service.sendMessage(reqJson)
                if (!sent) {
                    pendingRequests.remove(requestId)
                    promise.reject("COMPLETION_FAILED", "Failed to send completion request to LSP server")
                }
            } catch (e: Exception) {
                promise.reject("COMPLETION_ERROR", e.message ?: "Error sending completion request", e)
            }
        }
    }

    @ReactMethod
    fun hover(params: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val uri = params.getString("uri") ?: ""
                val line = if (params.hasKey("line")) params.getInt("line") else 0
                val character = if (params.hasKey("character")) params.getInt("character") else 0

                val requestId = seq.getAndIncrement()
                pendingRequests[requestId] = promise

                val req = JSONObject()
                req.put("jsonrpc", "2.0")
                req.put("id", requestId)
                req.put("method", "textDocument/hover")

                val paramsObj = JSONObject()
                paramsObj.put("textDocument", JSONObject().put("uri", uri))
                paramsObj.put("position", JSONObject().put("line", line).put("character", character))
                req.put("params", paramsObj)

                val sent = service.sendMessage(req.toString())
                if (!sent) {
                    pendingRequests.remove(requestId)
                    promise.reject("HOVER_FAILED", "Failed to send hover request to LSP server")
                }
            } catch (e: Exception) {
                promise.reject("HOVER_ERROR", e.message ?: "Error sending hover request", e)
            }
        }
    }

    @ReactMethod
    fun definition(params: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val uri = params.getString("uri") ?: ""
                val line = if (params.hasKey("line")) params.getInt("line") else 0
                val character = if (params.hasKey("character")) params.getInt("character") else 0

                val requestId = seq.getAndIncrement()
                pendingRequests[requestId] = promise

                val req = JSONObject()
                req.put("jsonrpc", "2.0")
                req.put("id", requestId)
                req.put("method", "textDocument/definition")

                val paramsObj = JSONObject()
                paramsObj.put("textDocument", JSONObject().put("uri", uri))
                paramsObj.put("position", JSONObject().put("line", line).put("character", character))
                req.put("params", paramsObj)

                val sent = service.sendMessage(req.toString())
                if (!sent) {
                    pendingRequests.remove(requestId)
                    promise.reject("DEFINITION_FAILED", "Failed to send definition request to LSP server")
                }
            } catch (e: Exception) {
                promise.reject("DEFINITION_ERROR", e.message ?: "Error sending definition request", e)
            }
        }
    }

    @ReactMethod
    fun references(params: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch
                val uri = params.getString("uri") ?: ""
                val line = if (params.hasKey("line")) params.getInt("line") else 0
                val character = if (params.hasKey("character")) params.getInt("character") else 0

                val requestId = seq.getAndIncrement()
                pendingRequests[requestId] = promise

                val req = JSONObject()
                req.put("jsonrpc", "2.0")
                req.put("id", requestId)
                req.put("method", "textDocument/references")

                val paramsObj = JSONObject()
                paramsObj.put("textDocument", JSONObject().put("uri", uri))
                paramsObj.put("position", JSONObject().put("line", line).put("character", character))
                paramsObj.put("context", JSONObject().put("includeDeclaration", true))
                req.put("params", paramsObj)

                val sent = service.sendMessage(req.toString())
                if (!sent) {
                    pendingRequests.remove(requestId)
                    promise.reject("REFERENCES_FAILED", "Failed to send references request to LSP server")
                }
            } catch (e: Exception) {
                promise.reject("REFERENCES_ERROR", e.message ?: "Error sending references request", e)
            }
        }
    }

    @ReactMethod
    fun shutdown(promise: Promise) {
        scope.launch {
            try {
                val service = getServiceOrReject(promise) ?: return@launch

                val requestId = seq.getAndIncrement()
                val shutdownReq = JSONObject()
                shutdownReq.put("jsonrpc", "2.0")
                shutdownReq.put("id", requestId)
                shutdownReq.put("method", "shutdown")

                service.sendMessage(shutdownReq.toString())

                val exitNotification = JSONObject()
                exitNotification.put("jsonrpc", "2.0")
                exitNotification.put("method", "exit")
                service.sendMessage(exitNotification.toString())

                service.stopServer()

                val res = Arguments.createMap()
                res.putBoolean("success", true)
                promise.resolve(res)
            } catch (e: Exception) {
                promise.reject("SHUTDOWN_ERROR", e.message ?: "Error shutting down LSP server", e)
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
