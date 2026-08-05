package com.pystudio.notebook

import kotlinx.coroutines.flow.Flow

enum class CellType {
    CODE, MARKDOWN, RAW
}

data class CellOutput(
    val outputType: String, // 'execute_result', 'display_data', 'stream', 'error'
    val data: Map<String, String> // MIME type as key, content as value
)

data class Cell(
    val id: String,
    val type: CellType,
    val source: String,
    val executionCount: Int? = null,
    val outputs: List<CellOutput> = emptyList(),
    val stale: Boolean = false
)

data class NotebookHandle(
    val notebookId: String,
    val path: String
)

data class KernelSession(
    val kernelSessionId: String,
    val notebookId: String,
    val status: String,
    val pythonVersion: String,
    val memoryBytes: Long
)

data class KernelStatusEvent(
    val notebookId: String,
    val status: String, // 'starting', 'ready', 'running', 'interrupted', 'restarting', 'stopped'
    val memoryBytes: Long
)

data class ExecutionHandle(
    val cellId: String,
    val executionCount: Int,
    val status: String // 'queued', 'running', 'completed', 'error', 'interrupted'
)

data class CellOutputEvent(
    val cellId: String,
    val output: CellOutput,
    val isFinal: Boolean
)

data class VariableInfo(
    val name: String,
    val typeName: String,
    val reprPreview: String,
    val sizeBytesEstimate: Long
)

data class VariableDetail(
    val name: String,
    val typeName: String,
    val reprPreview: String,
    val sizeBytesEstimate: Long,
    val shape: List<Int>? = null,
    val columns: List<String>? = null,
    val detailData: Map<String, String> = emptyMap()
)

data class ExportOptions(
    val format: String, // 'html' or 'pdf'
    val includeCode: Boolean,
    val includeMarkdown: Boolean,
    val orientation: String = "portrait" // 'portrait' or 'landscape'
)

data class ExportResult(
    val filePath: String,
    val sizeBytes: Long,
    val staleWarningAcknowledged: Boolean
)

interface NotebookDocumentService {
    suspend fun open(path: String): NotebookHandle
    suspend fun close(notebookId: String)
    suspend fun addCell(notebookId: String, type: CellType, index: Int): Cell
    suspend fun updateCellSource(notebookId: String, cellId: String, source: String)
    suspend fun getCell(notebookId: String, cellId: String): Cell?
}

interface KernelManagerService {
    suspend fun ensureKernelStarted(notebookId: String): KernelSession
    suspend fun interrupt(notebookId: String)
    suspend fun restart(notebookId: String)
    fun statusFlow(notebookId: String): Flow<KernelStatusEvent>
}

data class CellResult(
    val cellId: String,
    val success: Boolean,
    val outputs: List<CellOutput>
)

interface ExecutionService {
    suspend fun executeCell(notebookId: String, cellId: String): ExecutionHandle
    suspend fun executeAll(notebookId: String, cells: List<Cell>, stopOnError: Boolean = true): List<CellResult>
    fun outputFlow(notebookId: String): Flow<CellOutputEvent>
}

interface VariableInspectorService {
    suspend fun listVariables(notebookId: String): List<VariableInfo>
    suspend fun inspect(notebookId: String, name: String): VariableDetail
}

interface ExportService {
    suspend fun exportHtml(notebookId: String, options: ExportOptions): ExportResult
    suspend fun exportPdf(notebookId: String, options: ExportOptions): ExportResult
}
