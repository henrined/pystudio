package com.pystudio.bridge

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.pystudio.notebook.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PyStudioJupyterBridgeModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val docService = InMemoryNotebookDocumentService()
    private val kernelServicesMap = ConcurrentHashMap<String, JupyterKernelService>()
    private val activeStatusListeners = ConcurrentHashMap<String, Boolean>()

    private class InMemoryNotebookDocumentService : NotebookDocumentService {
        private val cells = ConcurrentHashMap<String, ConcurrentHashMap<String, Cell>>()

        fun setCellSource(notebookId: String, cellId: String, source: String) {
            val map = cells.getOrPut(notebookId) { ConcurrentHashMap() }
            map[cellId] = Cell(id = cellId, type = CellType.CODE, source = source)
        }

        override suspend fun open(path: String): NotebookHandle = NotebookHandle(path, path)
        override suspend fun close(notebookId: String) {
            cells.remove(notebookId)
        }

        override suspend fun addCell(notebookId: String, type: CellType, index: Int): Cell {
            val id = UUID.randomUUID().toString()
            val cell = Cell(id, type, "")
            cells.getOrPut(notebookId) { ConcurrentHashMap() }[id] = cell
            return cell
        }

        override suspend fun updateCellSource(notebookId: String, cellId: String, source: String) {
            setCellSource(notebookId, cellId, source)
        }

        override suspend fun getCell(notebookId: String, cellId: String): Cell? {
            return cells[notebookId]?.get(cellId)
        }
    }

    override fun getName(): String = "PyStudioJupyterBridge"

    private fun getOrCreateService(notebookId: String): JupyterKernelService {
        return kernelServicesMap.getOrPut(notebookId) {
            val service = JupyterKernelService(docService = docService)
            if (activeStatusListeners.putIfAbsent(notebookId, true) == null) {
                listenToKernelStatus(notebookId, service)
            }
            service
        }
    }

    private fun listenToKernelStatus(notebookId: String, service: JupyterKernelService) {
        scope.launch {
            try {
                service.statusFlow(notebookId).collect { statusEvent ->
                    val map = Arguments.createMap()
                    map.putString("notebookId", statusEvent.notebookId)
                    map.putString("status", statusEvent.status)
                    map.putDouble("memoryBytes", statusEvent.memoryBytes.toDouble())

                    reactContext
                        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                        .emit("jupyterKernelStatus", map)
                }
            } catch (e: Exception) {
                // Ignore listener cancellation on shutdown
            }
        }
    }

    @ReactMethod
    fun executeCell(notebookId: String, cellId: String, code: String, promise: Promise) {
        scope.launch {
            try {
                if (notebookId.isEmpty() || cellId.isEmpty()) {
                    promise.reject("INVALID_ARGUMENT", "NotebookId and cellId must not be empty")
                    return@launch
                }

                val service = getOrCreateService(notebookId)
                docService.setCellSource(notebookId, cellId, code)

                val outputsList = mutableListOf<ReadableMap>()
                var isCompleted = false
                var executionCount = 1

                val collectorJob = scope.launch {
                    try {
                        service.outputFlow(notebookId).collect { event ->
                            if (event.cellId == cellId || event.cellId == "unknown") {
                                var mainMime = "text/plain"
                                var mainData = ""
                                if (event.output.data.isNotEmpty()) {
                                    val entry = event.output.data.entries.first()
                                    mainMime = entry.key
                                    mainData = entry.value
                                }

                                val outputMap = Arguments.createMap()
                                outputMap.putString("type", event.output.outputType)
                                outputMap.putString("mimeType", mainMime)
                                outputMap.putString("data", mainData)

                                synchronized(outputsList) {
                                    outputsList.add(outputMap)
                                }

                                val eventMap = Arguments.createMap()
                                eventMap.putString("notebookId", notebookId)
                                eventMap.putString("cellId", cellId)
                                eventMap.putString("type", event.output.outputType)
                                eventMap.putString("mimeType", mainMime)
                                eventMap.putString("data", mainData)
                                eventMap.putBoolean("isFinal", event.isFinal)

                                reactContext
                                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                                    .emit("jupyterCellOutput", eventMap)

                                if (event.isFinal) {
                                    isCompleted = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Collector job scope completed
                    }
                }

                val handle = service.executeCell(notebookId, cellId)
                executionCount = handle.executionCount

                var totalWaitMs = 0
                val timeoutMs = 30000
                while (!isCompleted && totalWaitMs < timeoutMs) {
                    delay(100)
                    totalWaitMs += 100
                }
                collectorJob.cancel()

                val resultMap = Arguments.createMap()
                resultMap.putString("status", if (isCompleted) "completed" else "timeout")
                resultMap.putInt("executionCount", executionCount)

                val outputsArray = Arguments.createArray()
                synchronized(outputsList) {
                    outputsList.forEach { outputsArray.pushMap(it) }
                }
                resultMap.putArray("outputs", outputsArray)

                promise.resolve(resultMap)
            } catch (e: Exception) {
                promise.reject("EXECUTE_CELL_ERROR", e.message ?: "Failed to execute cell", e)
            }
        }
    }

    @ReactMethod
    fun interruptKernel(notebookId: String, promise: Promise) {
        scope.launch {
            try {
                if (notebookId.isEmpty()) {
                    promise.reject("INVALID_ARGUMENT", "NotebookId must not be empty")
                    return@launch
                }
                val service = getOrCreateService(notebookId)
                service.interrupt(notebookId)

                val res = Arguments.createMap()
                res.putString("notebookId", notebookId)
                res.putString("status", "interrupted")
                res.putBoolean("success", true)
                promise.resolve(res)
            } catch (e: Exception) {
                promise.reject("INTERRUPT_KERNEL_ERROR", e.message ?: "Failed to interrupt kernel", e)
            }
        }
    }

    @ReactMethod
    fun restartKernel(notebookId: String, promise: Promise) {
        scope.launch {
            try {
                if (notebookId.isEmpty()) {
                    promise.reject("INVALID_ARGUMENT", "NotebookId must not be empty")
                    return@launch
                }
                val service = getOrCreateService(notebookId)
                service.restart(notebookId)

                val res = Arguments.createMap()
                res.putString("notebookId", notebookId)
                res.putString("status", "restarted")
                res.putBoolean("success", true)
                promise.resolve(res)
            } catch (e: Exception) {
                promise.reject("RESTART_KERNEL_ERROR", e.message ?: "Failed to restart kernel", e)
            }
        }
    }

    @ReactMethod
    fun getKernelStatus(notebookId: String, promise: Promise) {
        scope.launch {
            try {
                if (notebookId.isEmpty()) {
                    promise.reject("INVALID_ARGUMENT", "NotebookId must not be empty")
                    return@launch
                }
                val service = getOrCreateService(notebookId)
                val session = service.ensureKernelStarted(notebookId)
                val statusStr = when (session.status) {
                    "ready" -> "idle"
                    "running" -> "busy"
                    "starting" -> "starting"
                    "stopped", "interrupted" -> "dead"
                    else -> session.status
                }

                val res = Arguments.createMap()
                res.putString("notebookId", notebookId)
                res.putString("status", statusStr)
                res.putDouble("memoryBytes", session.memoryBytes.toDouble())
                promise.resolve(res)
            } catch (e: Exception) {
                promise.reject("GET_KERNEL_STATUS_ERROR", e.message ?: "Failed to get kernel status", e)
            }
        }
    }

    @ReactMethod
    fun listVariables(notebookId: String, promise: Promise) {
        scope.launch {
            try {
                if (notebookId.isEmpty()) {
                    promise.reject("INVALID_ARGUMENT", "NotebookId must not be empty")
                    return@launch
                }
                val service = getOrCreateService(notebookId)
                val variables = service.listVariables(notebookId)

                val array = Arguments.createArray()
                for (v in variables) {
                    val vMap = Arguments.createMap()
                    vMap.putString("name", v.name)
                    vMap.putString("typeName", v.typeName)
                    vMap.putString("reprPreview", v.reprPreview)
                    vMap.putDouble("sizeBytesEstimate", v.sizeBytesEstimate.toDouble())
                    array.pushMap(vMap)
                }
                promise.resolve(array)
            } catch (e: Exception) {
                promise.reject("LIST_VARIABLES_ERROR", e.message ?: "Failed to list variables", e)
            }
        }
    }

    @ReactMethod
    fun inspectVariable(notebookId: String, varName: String, promise: Promise) {
        scope.launch {
            try {
                if (notebookId.isEmpty() || varName.isEmpty()) {
                    promise.reject("INVALID_ARGUMENT", "NotebookId and varName must not be empty")
                    return@launch
                }
                val service = getOrCreateService(notebookId)
                val detail = service.inspect(notebookId, varName)

                val map = Arguments.createMap()
                map.putString("name", detail.name)
                map.putString("typeName", detail.typeName)
                map.putString("reprPreview", detail.reprPreview)
                map.putDouble("sizeBytesEstimate", detail.sizeBytesEstimate.toDouble())

                if (detail.shape != null) {
                    val shapeArray = Arguments.createArray()
                    detail.shape.forEach { shapeArray.pushInt(it) }
                    map.putArray("shape", shapeArray)
                } else {
                    map.putNull("shape")
                }

                if (detail.columns != null) {
                    val colsArray = Arguments.createArray()
                    detail.columns.forEach { colsArray.pushString(it) }
                    map.putArray("columns", colsArray)
                } else {
                    map.putNull("columns")
                }

                val detailDataMap = Arguments.createMap()
                detail.detailData.forEach { (k, v) -> detailDataMap.putString(k, v) }
                map.putMap("detailData", detailDataMap)

                promise.resolve(map)
            } catch (e: Exception) {
                promise.reject("INSPECT_VARIABLE_ERROR", e.message ?: "Failed to inspect variable", e)
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
}
