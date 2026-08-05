package com.pystudio.notebook

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JupyterKernelServiceTest {

    @Test
    fun testHandleIpcMessage_DisplayData() = runTest {
        val service = JupyterKernelService()
        val notebookId = "test_nb"
        val cellId = "cell_1"
        
        // Mock stdout emitted by jupyter_adapter.py
        val jsonPayload = """__PYSTUDIO_JUPYTER__:{"channel":"iopub","cell_id":"$cellId","msg":{"msg_type":"display_data","content":{"data":{"image/png":"iVBORw0K...","text/plain":"<Figure size 640x480 with 1 Axes>"}}}}"""
        
        service.handleIpcMessage(notebookId, jsonPayload)
        
        val event = service.outputFlow(notebookId).first()
        assertEquals(cellId, event.cellId)
        assertEquals("display_data", event.output.outputType)
        assertTrue(event.output.data.containsKey("image/png"))
        assertEquals("iVBORw0K...", event.output.data["image/png"])
    }
    
    @Test
    fun testHandleIpcMessage_Stream() = runTest {
        val service = JupyterKernelService()
        val notebookId = "test_nb"
        val cellId = "cell_1"
        
        val jsonPayload = """__PYSTUDIO_JUPYTER__:{"channel":"iopub","cell_id":"$cellId","msg":{"msg_type":"stream","content":{"text":"hello world\n"}}}"""
        
        service.handleIpcMessage(notebookId, jsonPayload)
        
        val event = service.outputFlow(notebookId).first()
        assertEquals(cellId, event.cellId)
        assertEquals("stream", event.output.outputType)
        assertEquals("hello world\n", event.output.data["text/plain"])
    }
    
    @Test
    fun testExecuteCell_SendsEscapedCodeToRunner() = runTest {
        var lastCommand = ""
        val mockRunner = object : JupyterRunner {
            override fun runString(code: String): Boolean {
                lastCommand = code
                return true
            }
            override fun setOutputHandler(handler: (String) -> Unit) {}
            override fun start() {}
            override fun stop() {}
        }
        
        val mockDocService = object : NotebookDocumentService {
            override suspend fun open(path: String) = NotebookHandle("", "")
            override suspend fun close(notebookId: String) {}
            override suspend fun addCell(notebookId: String, type: CellType, index: Int) = Cell("", CellType.CODE, "")
            override suspend fun updateCellSource(notebookId: String, cellId: String, source: String) {}
            override suspend fun getCell(notebookId: String, cellId: String): Cell? {
                return Cell(cellId, CellType.CODE, "print(\"Hello\\nWorld\")")
            }
        }
        
        val service = JupyterKernelService(
            docService = mockDocService,
            runnerFactory = { mockRunner }
        )
        
        val handle = service.executeCell("nb_1", "cell_xyz")
        assertEquals("cell_xyz", handle.cellId)
        
        // Ensure that the adapter init and the execute call were made.
        // runString is called during ensureKernelStarted, then again for executeCell.
        // The last command should be the execution command.
        val expectedCall = """jupyter_adapter.execute('cell_xyz', "print(\"Hello\\nWorld\")")"""
        assertEquals(expectedCall, lastCommand)
    }

    @Test
    fun testExecuteAll_MultipleCells() = runTest {
        var callCount = 0
        val mockRunner = object : JupyterRunner {
            private var outputHandler: ((String) -> Unit)? = null
            
            override fun runString(code: String): Boolean {
                if (code.contains("sys.path.append")) return true // init
                
                // Extract cell_id from jupyter_adapter.execute('cell_id', ...)
                val match = Regex("jupyter_adapter\\.execute\\('(cell_\\d+)',").find(code)
                val cellId = match?.groupValues?.get(1) ?: "unknown"
                
                // Simulate output
                val jsonPayloadStream = """__PYSTUDIO_JUPYTER__:{"channel":"iopub","cell_id":"$cellId","msg":{"msg_type":"stream","content":{"text":"output $cellId"}}}"""
                val jsonPayloadReply = """__PYSTUDIO_JUPYTER__:{"channel":"iopub","cell_id":"$cellId","msg":{"msg_type":"execute_reply","content":{"status":"ok"}}}"""
                outputHandler?.invoke(jsonPayloadStream)
                outputHandler?.invoke(jsonPayloadReply)
                
                callCount++
                return true
            }
            override fun setOutputHandler(handler: (String) -> Unit) {
                outputHandler = handler
            }
            override fun start() {}
            override fun stop() {}
        }
        
        val mockDocService = object : NotebookDocumentService {
            override suspend fun open(path: String) = NotebookHandle("", "")
            override suspend fun close(notebookId: String) {}
            override suspend fun addCell(notebookId: String, type: CellType, index: Int) = Cell("", CellType.CODE, "")
            override suspend fun updateCellSource(notebookId: String, cellId: String, source: String) {}
            override suspend fun getCell(notebookId: String, cellId: String): Cell? {
                return Cell(cellId, CellType.CODE, "print('$cellId')")
            }
        }
        
        val service = JupyterKernelService(
            docService = mockDocService,
            runnerFactory = { mockRunner }
        )
        
        val cells = listOf(
            Cell("cell_1", CellType.CODE, "print('cell_1')"),
            Cell("cell_2", CellType.CODE, "print('cell_2')"),
            Cell("cell_3", CellType.CODE, "print('cell_3')")
        )
        
        val results = service.executeAll("nb_1", cells, stopOnError = true)
        
        assertEquals(3, callCount)
        assertEquals(3, results.size)
        
        for (i in 0..2) {
            val res = results[i]
            assertEquals("cell_${i + 1}", res.cellId)
            assertTrue(res.success)
            assertEquals(1, res.outputs.size)
            assertEquals("stream", res.outputs[0].outputType)
            assertEquals("output cell_${i + 1}", res.outputs[0].data["text/plain"])
        }
    }
    
    @Test
    fun testListVariables() = runTest {
        val mockRunner = object : JupyterRunner {
            private var outputHandler: ((String) -> Unit)? = null
            
            override fun runString(code: String): Boolean {
                if (code.contains("sys.path.append")) return true // init
                
                val cellId = "sys_listVariables"
                val variablesJson = "[{\"name\": \"x\", \"type\": \"int\", \"size\": 28}, {\"name\": \"df\", \"type\": \"DataFrame\", \"size\": 1024}]"
                
                val jsonPayloadStream = """__PYSTUDIO_JUPYTER__:{"channel":"iopub","cell_id":"$cellId","msg":{"msg_type":"stream","content":{"text":"$variablesJson"}}}"""
                val jsonPayloadReply = """__PYSTUDIO_JUPYTER__:{"channel":"iopub","cell_id":"$cellId","msg":{"msg_type":"execute_reply","content":{"status":"ok"}}}"""
                outputHandler?.invoke(jsonPayloadStream)
                outputHandler?.invoke(jsonPayloadReply)
                
                return true
            }
            override fun setOutputHandler(handler: (String) -> Unit) {
                outputHandler = handler
            }
            override fun start() {}
            override fun stop() {}
        }
        
        val service = JupyterKernelService(
            docService = null,
            runnerFactory = { mockRunner }
        )
        
        val vars = service.listVariables("nb_1")
        assertEquals(2, vars.size)
        
        assertEquals("x", vars[0].name)
        assertEquals("int", vars[0].typeName)
        assertEquals(28L, vars[0].sizeBytesEstimate)
        
        assertEquals("df", vars[1].name)
        assertEquals("DataFrame", vars[1].typeName)
        assertEquals(1024L, vars[1].sizeBytesEstimate)
    }
    
    @Test
    fun testInspect() = runTest {
        val mockRunner = object : JupyterRunner {
            private var outputHandler: ((String) -> Unit)? = null
            
            override fun runString(code: String): Boolean {
                if (code.contains("sys.path.append")) return true // init
                
                val cellId = "sys_inspect_x"
                val inspectJson = "{\"name\": \"x\", \"type\": \"int\", \"size\": 28, \"repr\": \"42\", \"doc\": \"int(x=0) -> integer\"}"
                
                // Note: The escape quotes inside JSON for stream output
                val jsonPayloadStream = """__PYSTUDIO_JUPYTER__:{"channel":"iopub","cell_id":"$cellId","msg":{"msg_type":"stream","content":{"text":"{\"name\": \"x\", \"type\": \"int\", \"size\": 28, \"repr\": \"42\", \"doc\": \"int(x=0) -> integer\"}"}}}"""
                val jsonPayloadReply = """__PYSTUDIO_JUPYTER__:{"channel":"iopub","cell_id":"$cellId","msg":{"msg_type":"execute_reply","content":{"status":"ok"}}}"""
                outputHandler?.invoke(jsonPayloadStream)
                outputHandler?.invoke(jsonPayloadReply)
                
                return true
            }
            override fun setOutputHandler(handler: (String) -> Unit) {
                outputHandler = handler
            }
            override fun start() {}
            override fun stop() {}
        }
        
        val service = JupyterKernelService(
            docService = null,
            runnerFactory = { mockRunner }
        )
        
        val detail = service.inspect("nb_1", "x")
        
        assertEquals("x", detail.name)
        assertEquals("int", detail.typeName)
        assertEquals("42", detail.reprPreview)
        assertEquals(28L, detail.sizeBytesEstimate)
        assertEquals("int(x=0) -> integer", detail.detailData["docstring"])
    }
}
