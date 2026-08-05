package com.pystudio.bridge

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.*
import com.pystudio.ai.*
import com.pystudio.core.fs.FileSystemService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class PyStudioAIBridgeModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pendingJobs = ConcurrentLinkedQueue<Job>()
    private val jobToConversationId = ConcurrentHashMap<Job, String>()
    
    private val fsService = FileSystemService()
    private val contextBuilder = ContextBuilderServiceImpl(fsService)
    
    private val cloudClient = AICloudClient(reactContext)
    
    private val inferenceGateway = object : InferenceRuntimeGateway {
        override suspend fun generateStream(prompt: String, grammar: String?, onToken: (String) -> Unit) {
            // Throw exception to trigger cloud fallback in AIAssistantServiceImpl
            throw UnsupportedOperationException("Local inference not yet available, falling back to cloud")
        }
    }
    
    private val aiService = AIAssistantServiceImpl(contextBuilder, inferenceGateway, fsService, cloudClient)

    init {
        scope.launch {
            aiService.actionProgress().collect { event ->
                // Map the pending job to the actionId on the first event
                val job = pendingJobs.poll()
                if (job != null) {
                    activeJobs[event.actionId] = job
                }

                val params = Arguments.createMap().apply {
                    putString("actionId", event.actionId)
                    putString("status", event.status)
                    event.diffPreview?.let { putString("diffPreview", it) }
                    event.errorCode?.let { putString("errorCode", it) }
                }
                emitEvent("aiProgress", params)
            }
        }
    }

    private fun emitEvent(eventName: String, params: Any?) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(eventName, params)
    }

    override fun getName(): String = "PyStudioAIBridge"

    @ReactMethod
    fun newConversation(promise: Promise) {
        promise.resolve(java.util.UUID.randomUUID().toString())
    }

    @ReactMethod
    fun sendMessage(conversationId: String, message: String, contextParams: ReadableMap?, promise: Promise) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val functionName = contextParams?.getString("function")
                val function = try {
                    if (functionName != null) AIFunction.valueOf(functionName) else AIFunction.CHAT
                } catch (e: Exception) {
                    AIFunction.CHAT
                }
                
                val filePath = contextParams?.getString("filePath") ?: ""
                val selectionStartLine = if (contextParams?.hasKey("selectionStartLine") == true) contextParams.getInt("selectionStartLine") else null
                val selectionEndLine = if (contextParams?.hasKey("selectionEndLine") == true) contextParams.getInt("selectionEndLine") else null
                
                val request = AIActionRequest(
                    function = function,
                    filePath = filePath,
                    selectionStartLine = selectionStartLine,
                    selectionEndLine = selectionEndLine,
                    errorMessage = if (contextParams?.hasKey("errorMessage") == true) contextParams.getString("errorMessage") else null,
                    errorStackTrace = if (contextParams?.hasKey("errorStackTrace") == true) contextParams.getString("errorStackTrace") else null
                )
                
                val actionId = aiService.runAction(request)
                
                val result = Arguments.createMap().apply {
                    putString("actionId", actionId)
                    putString("status", "completed")
                }
                promise.resolve(result)
            } catch (e: CancellationException) {
                promise.reject("AI_CANCELLED", "Action was cancelled")
            } catch (e: Exception) {
                promise.reject("AI_ERROR", e.message, e)
                val errorParams = Arguments.createMap().apply {
                    putString("conversationId", conversationId)
                    putString("error", e.message)
                }
                emitEvent("aiError", errorParams)
            } finally {
                val currentJob = coroutineContext[Job]
                if (currentJob != null) {
                    jobToConversationId.remove(currentJob)
                    val iterator = activeJobs.entries.iterator()
                    while (iterator.hasNext()) {
                        if (iterator.next().value == currentJob) {
                            iterator.remove()
                        }
                    }
                }
            }
        }
        
        jobToConversationId[job] = conversationId
        pendingJobs.add(job)
        job.start()
    }

    @ReactMethod
    fun runAction(request: ReadableMap, promise: Promise) {
        // Kept for backward compatibility if needed, though sendMessage is the main entry point now.
        promise.resolve("action_" + java.util.UUID.randomUUID().toString())
    }

    @ReactMethod
    fun applyPatch(actionId: String, decision: String, editedDiff: String?, promise: Promise) {
        scope.launch {
            try {
                aiService.applyActionResult(actionId, decision, editedDiff)
                val result = Arguments.createMap().apply {
                    putBoolean("applied", decision == "accept" || decision == "edit")
                    putString("filePath", "") // Original file path would ideally be returned here
                }
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("PATCH_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun cancelRequest(actionId: String, promise: Promise) {
        val job = activeJobs[actionId]
        if (job != null) {
            job.cancel()
            activeJobs.remove(actionId)
            jobToConversationId.remove(job)
            promise.resolve(true)
        } else {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun getConversationHistory(conversationId: String, promise: Promise) {
        val history = Arguments.createArray()
        promise.resolve(history)
    }

    @ReactMethod
    fun setModel(modelConfig: ReadableMap, promise: Promise) {
        val modelType = if (modelConfig.hasKey("type")) modelConfig.getString("type") else "local"
        val modelPath = if (modelConfig.hasKey("path")) modelConfig.getString("path") else ""
        promise.resolve(true)
    }
}
