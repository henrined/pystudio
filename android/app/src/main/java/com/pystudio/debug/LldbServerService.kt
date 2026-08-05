package com.pystudio.debug

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class LldbServerService : Service() {
    private var process: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val executable = intent?.getStringExtra("executable") ?: "lldb-server"
        val socketPath = intent?.getStringExtra("socketPath") ?: "/data/local/tmp/lldb-server.sock"
        
        Thread {
            try {
                Log.i("LldbServerService", "Starting $executable listening on unix://$socketPath")
                val pb = ProcessBuilder(executable, "platform", "--listen", "unix-abstract://$socketPath")
                pb.redirectErrorStream(true)
                process = pb.start()
                process?.inputStream?.bufferedReader()?.useLines { lines ->
                    lines.forEach { Log.d("LldbServerService", it) }
                }
            } catch (e: Exception) {
                Log.e("LldbServerService", "Error running lldb-server", e)
            }
        }.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        process?.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
