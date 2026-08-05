package com.pystudio.debug

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.pystudio.IDebugCallback
import com.pystudio.IDebugService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import org.json.JSONObject
import org.json.JSONArray
import kotlin.concurrent.thread

class DebugService : Service() {

    companion object {
        init {
            System.loadLibrary("dbgbridge_jni")
        }
    }

    private var callback: IDebugCallback? = null
    private var isPython = false
    private var pythonSocket: Socket? = null
    private var pythonWriter: OutputStreamWriter? = null
    private var pythonProcess: Process? = null
    private var seq = 1

    private fun sendPythonDap(command: String, args: JSONObject) {
        val req = JSONObject()
        req.put("seq", seq++)
        req.put("type", "request")
        req.put("command", command)
        req.put("arguments", args)
        
        val body = req.toString()
        val header = "Content-Length: ${body.length}\r\n\r\n"
        thread {
            try {
                pythonWriter?.write(header + body)
                pythonWriter?.flush()
            } catch (e: Exception) {
                Log.e("DebugService", "Error sending python DAP", e)
            }
        }
    }

    private fun readPythonDap() {
        thread {
            try {
                val reader = BufferedReader(InputStreamReader(pythonSocket!!.getInputStream()))
                while (true) {
                    var contentLength = 0
                    var line = reader.readLine()
                    while (line != null && line.isNotEmpty()) {
                        if (line.startsWith("Content-Length: ")) {
                            contentLength = line.substring(16).trim().toInt()
                        }
                        line = reader.readLine()
                    }
                    if (line == null) break
                    
                    val bodyChars = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = reader.read(bodyChars, read, contentLength - read)
                        if (n == -1) break
                        read += n
                    }
                    
                    val body = String(bodyChars)
                    val json = JSONObject(body)
                    if (json.optString("type") == "event") {
                        val event = json.optString("event")
                        val payload = json.optJSONObject("body")?.toString() ?: "{}"
                        callback?.onDapEvent(event, payload)
                    } else if (json.optString("type") == "response") {
                        // Forward responses as custom events for simplicity in this bridge
                        val command = json.optString("command")
                        if (command == "variables" || command == "scopes" || command == "stackTrace" || command == "evaluate") {
                            val payload = json.optJSONObject("body")?.toString() ?: "{}"
                            callback?.onDapEvent("response_$command", payload)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DebugService", "Error reading python DAP", e)
            }
        }
    }

    private val binder = object : IDebugService.Stub() {
        override fun initialize(callback: IDebugCallback): Boolean {
            this@DebugService.callback = callback
            return nativeInitialize()
        }

        override fun launchProgram(programPath: String, args: Array<String>): Boolean {
            if (programPath.endsWith(".py")) {
                isPython = true
                thread {
                    try {
                        val port = 5678
                        // Wait for debugpy to start (simulate or real)
                        pythonProcess = ProcessBuilder(
                            "python3", "-m", "debugpy", "--listen", "127.0.0.1:$port", "--wait-for-client", programPath
                        ).redirectErrorStream(true).start()
                        
                        Thread.sleep(1000) // Give it a moment to bind
                        pythonSocket = Socket("127.0.0.1", port)
                        pythonWriter = OutputStreamWriter(pythonSocket!!.getOutputStream())
                        readPythonDap()
                        
                        val launchArgs = JSONObject()
                        launchArgs.put("program", programPath)
                        sendPythonDap("launch", launchArgs)
                    } catch (e: Exception) {
                        Log.e("DebugService", "Failed to launch Python debug", e)
                    }
                }
                return true
            } else {
                isPython = false
                return nativeLaunch(programPath, args)
            }
        }

        override fun attachToProcess(pid: Int): Boolean {
            isPython = false
            return nativeAttach(pid)
        }

        override fun setBreakpoints(file: String, lines: IntArray): String {
            if (isPython) {
                val args = JSONObject()
                val bps = JSONArray()
                lines.forEach { 
                    val bp = JSONObject()
                    bp.put("line", it)
                    bps.put(bp)
                }
                args.put("source", JSONObject().put("path", file))
                args.put("breakpoints", bps)
                sendPythonDap("setBreakpoints", args)
                return "[]" // async response via event
            }
            return nativeSetBreakpoints(file, lines)
        }

        override fun continueExecution(): Boolean {
            if (isPython) {
                sendPythonDap("continue", JSONObject().put("threadId", 1))
                return true
            }
            return nativeContinue()
        }

        override fun stepOver(): Boolean {
            if (isPython) {
                sendPythonDap("next", JSONObject().put("threadId", 1))
                return true
            }
            return nativeStepOver()
        }

        override fun stepInto(): Boolean {
            if (isPython) {
                sendPythonDap("stepIn", JSONObject().put("threadId", 1))
                return true
            }
            return nativeStepInto()
        }

        override fun stepOut(): Boolean {
            if (isPython) {
                sendPythonDap("stepOut", JSONObject().put("threadId", 1))
                return true
            }
            return nativeStepOut()
        }

        override fun pauseExecution(): Boolean {
            if (isPython) {
                sendPythonDap("pause", JSONObject().put("threadId", 1))
                return true
            }
            return nativePause()
        }

        override fun disconnect(): Boolean {
            if (isPython) {
                sendPythonDap("disconnect", JSONObject())
                pythonSocket?.close()
                pythonProcess?.destroy()
                return true
            }
            return nativeDisconnect()
        }

        override fun getStackTrace(threadId: Int): String {
            if (isPython) {
                sendPythonDap("stackTrace", JSONObject().put("threadId", threadId))
                return "[]"
            }
            return nativeGetStackTrace(threadId)
        }

        override fun getScopes(frameId: Int): String {
            if (isPython) {
                sendPythonDap("scopes", JSONObject().put("frameId", frameId))
                return "[]"
            }
            return nativeGetScopes(frameId)
        }

        override fun getVariables(variablesReference: Int): String {
            if (isPython) {
                sendPythonDap("variables", JSONObject().put("variablesReference", variablesReference))
                return "[]"
            }
            return nativeGetVariables(variablesReference)
        }

        override fun evaluate(expression: String, frameId: Int): String {
            if (isPython) {
                sendPythonDap("evaluate", JSONObject().put("expression", expression).put("frameId", frameId))
                return "{}"
            }
            return nativeEvaluate(expression, frameId)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    // Called from JNI
    fun onDapEvent(event: String, payload: String) {
        callback?.onDapEvent(event, payload)
    }

    private external fun nativeInitialize(): Boolean
    private external fun nativeLaunch(programPath: String, args: Array<String>): Boolean
    private external fun nativeAttach(pid: Int): Boolean
    private external fun nativeSetBreakpoints(file: String, lines: IntArray): String
    private external fun nativeContinue(): Boolean
    private external fun nativeStepOver(): Boolean
    private external fun nativeStepInto(): Boolean
    private external fun nativeStepOut(): Boolean
    private external fun nativePause(): Boolean
    private external fun nativeDisconnect(): Boolean
    private external fun nativeGetStackTrace(threadId: Int): String
    private external fun nativeGetScopes(frameId: Int): String
    private external fun nativeGetVariables(variablesReference: Int): String
    private external fun nativeEvaluate(expression: String, frameId: Int): String
}
