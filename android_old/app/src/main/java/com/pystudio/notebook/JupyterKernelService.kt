package com.pystudio.notebook

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Interface for running code, abstracting RunnerClient for testability
interface JupyterRunner {
    fun runString(code: String): Boolean
    fun setOutputHandler(handler: (String) -> Unit)
    fun start()
    fun stop()
}

class JupyterKernelService(
    private val docService: NotebookDocumentService? = null,
    private val runnerFactory: (String) -> JupyterRunner = { _ ->
        object : JupyterRunner {
            override fun runString(code: String) = true
            override fun setOutputHandler(handler: (String) -> Unit) {}
            override fun start() {}
            override fun stop() {}
        }
    }
) : KernelManagerService, ExecutionService, VariableInspectorService {

    private val kernels = ConcurrentHashMap<String, KernelSession>()
    private val statusFlows = ConcurrentHashMap<String, MutableSharedFlow<KernelStatusEvent>>()
    private val outputFlows = ConcurrentHashMap<String, MutableSharedFlow<CellOutputEvent>>()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val runners = ConcurrentHashMap<String, JupyterRunner>()
    private val executionCounters = ConcurrentHashMap<String, Int>()

    override suspend fun ensureKernelStarted(notebookId: String): KernelSession {
        return kernels.getOrPut(notebookId) {
            val sessionId = UUID.randomUUID().toString()
            val session = KernelSession(
                kernelSessionId = sessionId,
                notebookId = notebookId,
                status = "ready",
                pythonVersion = "3.13.2",
                memoryBytes = 0L
            )
            emitStatus(notebookId, "starting", 0L)
            
            // Initialize runner
            val runner = runnerFactory(notebookId)
            runner.setOutputHandler { output ->
                handleIpcMessage(notebookId, output)
            }
            runner.start()
            // Initialize jupyter adapter in the runner
            runner.runString("import sys; sys.path.append('/data/data/com.termux/files/home/pystudio/scripts'); import jupyter_adapter; jupyter_adapter.init_adapter()")
            runners[notebookId] = runner
            
            emitStatus(notebookId, "ready", 0L)
            session
        }
    }

    override suspend fun interrupt(notebookId: String) {
        kernels[notebookId]?.let {
            val newSession = it.copy(status = "interrupted")
            kernels[notebookId] = newSession
            emitStatus(notebookId, "interrupted", it.memoryBytes)
        }
    }

    override suspend fun restart(notebookId: String) {
        kernels[notebookId]?.let {
            emitStatus(notebookId, "restarting", 0L)
            runners[notebookId]?.stop()
            kernels.remove(notebookId)
            runners.remove(notebookId)
            ensureKernelStarted(notebookId)
        }
    }

    override fun statusFlow(notebookId: String): Flow<KernelStatusEvent> {
        return statusFlows.getOrPut(notebookId) { MutableSharedFlow() }.asSharedFlow()
    }

    override suspend fun executeCell(notebookId: String, cellId: String): ExecutionHandle {
        val session = ensureKernelStarted(notebookId)
        
        val runner = runners[notebookId]
        val cell = docService?.getCell(notebookId, cellId)
        val code = cell?.source ?: ""
        
        // Escape code for python execution
        val escapedCode = code.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        runner?.runString("jupyter_adapter.execute('$cellId', \"$escapedCode\")")
        
        val count = executionCounters.compute(notebookId) { _, v -> (v ?: 0) + 1 } ?: 1
        return ExecutionHandle(
            cellId = cellId,
            executionCount = count,
            status = "queued"
        )
    }

    override suspend fun executeAll(notebookId: String, cells: List<Cell>, stopOnError: Boolean): List<CellResult> {
        val session = ensureKernelStarted(notebookId)
        val results = mutableListOf<CellResult>()
        
        for (cell in cells) {
            if (cell.type == CellType.CODE) {
                // Execute cell
                executeCell(notebookId, cell.id)
                
                val outputs = mutableListOf<CellOutput>()
                var isCompleted = false
                var success = true
                
                val job = coroutineScope.launch {
                    outputFlow(notebookId).collect { event ->
                        if (event.cellId == cell.id || event.cellId == "unknown") {
                            // Only collect non-status outputs, or collect all
                            if (event.output.outputType != "status") {
                                outputs.add(event.output)
                            }
                            if (event.output.outputType == "error") {
                                success = false
                            }
                            if (event.isFinal) {
                                isCompleted = true
                            }
                        }
                    }
                }
                
                var wait = 0
                while (!isCompleted && wait < 60000) {
                    kotlinx.coroutines.delay(100)
                    wait += 100
                }
                job.cancel()
                
                results.add(CellResult(cell.id, success, outputs))
                if (!success && stopOnError) {
                    break
                }
            }
        }
        return results
    }

    override fun outputFlow(notebookId: String): Flow<CellOutputEvent> {
        return outputFlows.getOrPut(notebookId) { MutableSharedFlow() }.asSharedFlow()
    }

    override suspend fun listVariables(notebookId: String): List<VariableInfo> {
        val session = ensureKernelStarted(notebookId)
        val runner = runners[notebookId] ?: return emptyList()
        val cellId = "sys_listVariables"
        val code = "import json, sys\nprint(json.dumps([{'name': k, 'type': type(v).__name__, 'size': sys.getsizeof(v)} for k,v in globals().items() if not k.startswith('_')]))"
        val escapedCode = code.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        
        var jsonResult = ""
        var isCompleted = false
        val job = coroutineScope.launch {
            outputFlow(notebookId).collect { event ->
                if (event.cellId == cellId) {
                    if (event.output.outputType == "stream") {
                        jsonResult += event.output.data["text/plain"] ?: ""
                    }
                    if (event.isFinal) isCompleted = true
                }
            }
        }
        
        runner.runString("jupyter_adapter.execute('$cellId', \"$escapedCode\")")
        
        var wait = 0
        while (!isCompleted && wait < 5000) {
            kotlinx.coroutines.delay(100)
            wait += 100
        }
        job.cancel()
        
        val result = mutableListOf<VariableInfo>()
        try {
            val cleanedJson = jsonResult.trim()
            if (cleanedJson.isNotEmpty()) {
                val array = org.json.JSONArray(cleanedJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    result.add(VariableInfo(
                        name = obj.optString("name"),
                        typeName = obj.optString("type"),
                        reprPreview = "",
                        sizeBytesEstimate = obj.optLong("size")
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    override suspend fun inspect(notebookId: String, name: String): VariableDetail {
        val session = ensureKernelStarted(notebookId)
        val runner = runners[notebookId] ?: return VariableDetail(name, "Unknown", "", 0L)
        val cellId = "sys_inspect_$name"
        val code = """
import json, sys
if '$name' in globals():
    v = globals()['$name']
    d = {'name': '$name', 'type': type(v).__name__, 'size': sys.getsizeof(v), 'repr': repr(v)[:1000], 'doc': getattr(v, '__doc__', '')}
    if hasattr(v, 'shape'): d['shape'] = list(getattr(v, 'shape'))
    if hasattr(v, 'columns'): d['columns'] = list(getattr(v, 'columns'))
    print(json.dumps(d))
else:
    print('{}')
        """.trimIndent()
        
        val escapedCode = code.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        var jsonResult = ""
        var isCompleted = false
        val job = coroutineScope.launch {
            outputFlow(notebookId).collect { event ->
                if (event.cellId == cellId) {
                    if (event.output.outputType == "stream") {
                        jsonResult += event.output.data["text/plain"] ?: ""
                    }
                    if (event.isFinal) isCompleted = true
                }
            }
        }
        
        runner.runString("jupyter_adapter.execute('$cellId', \"$escapedCode\")")
        
        var wait = 0
        while (!isCompleted && wait < 5000) {
            kotlinx.coroutines.delay(100)
            wait += 100
        }
        job.cancel()
        
        try {
            val cleanedJson = jsonResult.trim()
            if (cleanedJson.isNotEmpty() && cleanedJson != "{}") {
                val obj = org.json.JSONObject(cleanedJson)
                val shapeArray = obj.optJSONArray("shape")
                val shapeList = if (shapeArray != null) {
                    List(shapeArray.length()) { i -> shapeArray.getInt(i) }
                } else null
                
                val columnsArray = obj.optJSONArray("columns")
                val columnsList = if (columnsArray != null) {
                    List(columnsArray.length()) { i -> columnsArray.getString(i) }
                } else null
                
                val doc = obj.optString("doc")
                val detailData = if (doc.isNotEmpty()) mapOf("docstring" to doc) else emptyMap()

                return VariableDetail(
                    name = obj.optString("name"),
                    typeName = obj.optString("type"),
                    reprPreview = obj.optString("repr"),
                    sizeBytesEstimate = obj.optLong("size"),
                    shape = shapeList,
                    columns = columnsList,
                    detailData = detailData
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return VariableDetail(name, "Unknown", "", 0L)
    }
    
    private suspend fun emitStatus(notebookId: String, status: String, memory: Long) {
        statusFlows.getOrPut(notebookId) { MutableSharedFlow() }
            .emit(KernelStatusEvent(notebookId, status, memory))
    }

    /**
     * S-10.4: Handle stdout from pyembed. 
     * Parses the JSON IPC protocol and maps display_data / execute_result to CellOutput.
     */
    fun handleIpcMessage(notebookId: String, rawStdout: String) {
        // rawStdout could contain multiple lines, some from jupyter, some from regular print
        val lines = rawStdout.split("\n")
        for (line in lines) {
            if (!line.startsWith("__PYSTUDIO_JUPYTER__:_")) {
                if (line.isNotEmpty()) {
                    // Regular stdout could be handled here if needed
                }
                continue
            }
            
            val jsonStr = line.removePrefix("__PYSTUDIO_JUPYTER__:").trim()
            try {
                val root = JSONObject(jsonStr)
                val channel = root.optString("channel")
                val cellId = root.optString("cell_id")
                val msg = root.optJSONObject("msg") ?: continue
                
                val msgType = msg.optString("msg_type")
                val content = msg.optJSONObject("content") ?: continue
                
                val outputData = mutableMapOf<String, String>()
                var outputType = "stream"
                var isFinal = false
                
                when (msgType) {
                    "execute_result", "display_data" -> {
                        outputType = msgType
                        val dataObj = content.optJSONObject("data")
                        dataObj?.keys()?.forEach { key ->
                            outputData[key] = dataObj.getString(key)
                        }
                    }
                    "stream" -> {
                        outputData["text/plain"] = content.optString("text")
                    }
                    "error" -> {
                        outputType = "error"
                        // Jupyter errors format the traceback as an array of strings
                        val traceback = content.optJSONArray("traceback")
                        if (traceback != null) {
                            val sb = StringBuilder()
                            for (i in 0 until traceback.length()) {
                                sb.append(traceback.getString(i)).append("\n")
                            }
                            outputData["text/plain"] = sb.toString()
                        } else {
                            outputData["text/plain"] = content.optString("evalue")
                        }
                    }
                    "execute_reply" -> {
                        // execution finished
                        isFinal = true
                        outputType = "status"
                        outputData["status"] = content.optString("status")
                    }
                    else -> continue // Ignore other messages
                }
                
                val output = CellOutput(outputType = outputType, data = outputData)
                
                coroutineScope.launch {
                    outputFlows.getOrPut(notebookId) { MutableSharedFlow() }
                        .emit(CellOutputEvent(cellId.ifEmpty { "unknown" }, output, isFinal))
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // For test compatibility since I changed signature of handleIpcMessage
    fun handleIpcMessage(notebookId: String, cellId: String, rawStdout: String) {
        // the new format has cell_id inside the JSON, so this is just for legacy tests
        // Actually I will update the tests to use the new JSON format.
        handleIpcMessage(notebookId, rawStdout)
    }
}
