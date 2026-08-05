package com.pystudio.lsp

import android.content.Intent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.pystudio.ILspCallback
import com.pystudio.ILspService
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class LspServiceTest {

    private lateinit var controller: ServiceController<LspService>
    private lateinit var service: LspService
    private lateinit var binder: ILspService
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        controller = Robolectric.buildService(LspService::class.java).create()
        service = controller.get()
        val intent = Intent()
        binder = service.onBind(intent) as ILspService
        tempDir = File(System.getProperty("java.io.tmpdir"), "lsp_test_dir_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        binder.stopServer()
        controller.destroy()
        tempDir.deleteRecursively()
    }

    @Test
    fun testStartServer_withMockServer() {
        val callback = object : ILspCallback.Stub() {
            override fun onMessage(jsonRpcMessage: String?) {}
            override fun onError(errorMsg: String?) {}
        }
        
        // Use "echo" command. LspService adds "-m pylsp" if language is "python"
        val success = binder.startServer("python", "echo", tempDir.absolutePath, callback)
        assertTrue("startServer should return true", success)
    }

    @Test
    fun testSendMessage_formatJsonRpc() {
        // Create a mock script that reads from stdin and writes a log so we can verify
        val script = File(tempDir, "mock_lsp.sh")
        script.writeText("""
            #!/bin/sh
            cat > "${tempDir.absolutePath}/output.log"
        """.trimIndent())
        script.setExecutable(true)

        val callback = object : ILspCallback.Stub() {
            override fun onMessage(jsonRpcMessage: String?) {}
            override fun onError(errorMsg: String?) {}
        }

        // We use "cpp" because for "python" it prepends `-m pylsp` which might break the shell script args
        val success = binder.startServer("cpp", script.absolutePath, tempDir.absolutePath, callback)
        assertTrue(success)

        val msg = """{"jsonrpc":"2.0","method":"shutdown"}"""
        val sendSuccess = binder.sendMessage(msg)
        assertTrue("sendMessage should succeed", sendSuccess)

        // Wait a bit for the script to process
        Thread.sleep(500)
        binder.stopServer()

        val outputLog = File(tempDir, "output.log")
        assertTrue(outputLog.exists())
        val content = outputLog.readText()
        assertTrue("Output should contain Content-Length header", content.contains("Content-Length: ${msg.length}"))
        assertTrue("Output should contain the message body", content.contains(msg))
    }

    @Test
    fun testReadLoop_injectsMessageAndCallsCallback() {
        // Create a mock script that writes a valid JSON-RPC message to stdout
        val script = File(tempDir, "mock_lsp_out.sh")
        val msg = """{"jsonrpc":"2.0","id":1,"result":{}}"""
        val header = "Content-Length: ${msg.length}\r\n\r\n"
        
        script.writeText("""
            #!/bin/sh
            printf "%b" "${header.replace("\r", "\\r").replace("\n", "\\n")}${msg.replace("\"", "\\\"")}"
            sleep 1
        """.trimIndent())
        script.setExecutable(true)

        val latch = CountDownLatch(1)
        var receivedMsg: String? = null
        
        val callback = object : ILspCallback.Stub() {
            override fun onMessage(jsonRpcMessage: String?) {
                receivedMsg = jsonRpcMessage
                latch.countDown()
            }
            override fun onError(errorMsg: String?) {}
        }

        val success = binder.startServer("cpp", script.absolutePath, tempDir.absolutePath, callback)
        assertTrue(success)

        // Wait for the message to be read and callback invoked
        latch.await(3, TimeUnit.SECONDS)
        
        assertNotNull("Should have received a message", receivedMsg)
        assertEquals(msg, receivedMsg)
    }
}
