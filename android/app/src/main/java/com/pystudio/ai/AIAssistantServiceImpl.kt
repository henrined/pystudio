package com.pystudio.ai

import com.pystudio.core.fs.FileSystemService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

class AIAssistantServiceImpl(
    private val contextBuilder: ContextBuilderService,
    private val inferenceGateway: InferenceRuntimeGateway,
    private val fileSystemService: FileSystemService,
    private val cloudClient: AICloudClient? = null // Optional fallback
) : AIAssistantService {

    private val progressFlow = MutableSharedFlow<AIActionProgressEvent>(extraBufferCapacity = 10)
    
    // Store active actions: actionId → (filePath, generatedDiff)
    private val activeActions = mutableMapOf<String, Pair<String, String>>()

    override suspend fun runAction(request: AIActionRequest): String {
        val actionId = UUID.randomUUID().toString()

        progressFlow.emit(AIActionProgressEvent(actionId, "building_context"))
        
        val context = contextBuilder.buildContext(request)
        
        progressFlow.emit(AIActionProgressEvent(actionId, "generating"))
        
        val generatedContent = StringBuilder()
        
        try {
            // Attempt to use local inference
            inferenceGateway.generateStream(context.promptText, context.grammar) { token ->
                generatedContent.append(token)
            }
        } catch (e: Exception) {
            // Fallback to cloud if configured and local fails
            if (cloudClient != null) {
                try {
                    val result = cloudClient.generateCompletion(context.promptText, context.systemPrompt)
                    generatedContent.append(result)
                } catch (ce: Exception) {
                    progressFlow.emit(AIActionProgressEvent(actionId, "error", errorCode = "AI_GENERATION_FAILED"))
                    return actionId
                }
            } else {
                progressFlow.emit(AIActionProgressEvent(actionId, "error", errorCode = "AI_MODEL_NOT_FOUND"))
                return actionId
            }
        }
        
        progressFlow.emit(AIActionProgressEvent(actionId, "parsing"))
        
        val generatedDiff = generatedContent.toString()
        
        // Store the generated diff alongside the file path for later application
        activeActions[actionId] = Pair(request.filePath, generatedDiff)
        
        progressFlow.emit(AIActionProgressEvent(actionId, "ready_for_review", diffPreview = generatedDiff))
        
        return actionId
    }

    override suspend fun applyActionResult(actionId: String, decision: String, editedDiff: String?) {
        val (filePath, generatedDiff) = activeActions[actionId] ?: return

        when (decision) {
            "accept" -> {
                val originalText = fileSystemService.readFile(filePath)
                val patchedContent = DiffApplicator.applyDiff(originalText, generatedDiff)
                fileSystemService.writeFile(filePath, patchedContent)
                activeActions.remove(actionId)
                progressFlow.emit(AIActionProgressEvent(actionId, "applied"))
            }
            "edit" -> {
                val diffToApply = editedDiff ?: generatedDiff
                val originalText = fileSystemService.readFile(filePath)
                val patchedContent = DiffApplicator.applyDiff(originalText, diffToApply)
                fileSystemService.writeFile(filePath, patchedContent)
                activeActions.remove(actionId)
                progressFlow.emit(AIActionProgressEvent(actionId, "applied"))
            }
            "reject" -> {
                activeActions.remove(actionId)
                progressFlow.emit(AIActionProgressEvent(actionId, "rejected"))
            }
        }
    }

    override fun actionProgress(): Flow<AIActionProgressEvent> {
        return progressFlow.asSharedFlow()
    }
}
