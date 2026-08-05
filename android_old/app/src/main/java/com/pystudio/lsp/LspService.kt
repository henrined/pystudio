package com.pystudio.lsp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.pystudio.ILspCallback
import com.pystudio.ILspService
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

class LspService : Service() {
    private var process: Process? = null
    private var callback: ILspCallback? = null
    private var stdin: OutputStream? = null
    private var stdout: InputStream? = null
    private var stderr: InputStream? = null
    private val threadPool = Executors.newCachedThreadPool()

    private val binder = object : ILspService.Stub() {
        override fun startServer(language: String, serverPath: String, workspacePath: String, cb: ILspCallback): Boolean {
            Log.d("LspService", "Starting LSP server for $language: $serverPath")
            callback = cb
            return try {
                val command = mutableListOf<String>()
                if (language == "python") {
                    // S-5.1: pylsp / pyright process
                    command.add(serverPath) // Usually path to embedded python
                    command.add("-m")
                    command.add("pylsp")
                } else if (language == "cpp") {
                    // S-5.2: clangd process with compile_commands.json directory
                    command.add(serverPath) // Path to clangd
                    command.add("--compile-commands-dir=$workspacePath")
                    command.add("--background-index")
                }

                val pb = ProcessBuilder(command)
                pb.directory(java.io.File(workspacePath))
                
                process = pb.start()
                stdin = process?.outputStream
                stdout = process?.inputStream
                stderr = process?.errorStream

                startReadingStdout()
                startReadingStderr()
                true
            } catch (e: Exception) {
                Log.e("LspService", "Error starting LSP server", e)
                callback?.onError(e.message)
                false
            }
        }

        override fun sendMessage(jsonRpcMessage: String): Boolean {
            return try {
                LspProtocol.sendMessage(stdin, jsonRpcMessage)
            } catch (e: Exception) {
                Log.e("LspService", "Error sending message", e)
                false
            }
        }

        override fun stopServer() {
            process?.destroy()
            process = null
            stdin?.close()
            stdout?.close()
            stderr?.close()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        binder.stopServer()
        super.onDestroy()
    }

    private fun startReadingStdout() {
        threadPool.execute {
            val input = stdout ?: return@execute
            LspProtocol.readLoop(
                input = input,
                onMessage = { jsonMessage -> callback?.onMessage(jsonMessage) },
                onError = { errorMsg ->
                    Log.e("LspService", "Error reading stdout: $errorMsg")
                    callback?.onError(errorMsg)
                }
            )
        }
    }

    private fun startReadingStderr() {
        threadPool.execute {
            val err = stderr ?: return@execute
            try {
                val reader = err.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d("LspService", "STDERR: $line")
                }
            } catch (e: Exception) {
                Log.e("LspService", "Error reading stderr", e)
            }
        }
    }
}
