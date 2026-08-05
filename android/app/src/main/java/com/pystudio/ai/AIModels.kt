package com.pystudio.ai

import kotlinx.coroutines.flow.Flow

enum class AIFunction {
    CHAT, COMPLETION, EXPLAIN_ERROR, GENERATE_TESTS, REFACTOR, GENERATE_DOCS
}

data class AIActionRequest(
    val function: AIFunction,
    val filePath: String,
    val selectionStartLine: Int? = null,
    val selectionEndLine: Int? = null,
    val errorMessage: String? = null,
    val errorStackTrace: String? = null
)

data class AIActionProgressEvent(
    val actionId: String,
    val status: String, // 'building_context', 'generating', 'parsing', 'ready_for_review', 'error'
    val diffPreview: String? = null,
    val errorCode: String? = null
)

data class PromptContext(
    val promptText: String,
    val systemPrompt: String? = null,
    val grammar: String? = null
)

data class ChatTokenEvent(
    val conversationId: String,
    val token: String,
    val isFinal: Boolean,
    val source: String // 'local' or 'cloud'
)

interface AIAssistantService {
    suspend fun runAction(request: AIActionRequest): String
    suspend fun applyActionResult(actionId: String, decision: String, editedDiff: String?)
    fun actionProgress(): Flow<AIActionProgressEvent>
}

interface ContextBuilderService {
    suspend fun buildContext(request: AIActionRequest): PromptContext
}

interface InferenceRuntimeGateway {
    // Basic placeholder for the gateway defined in AI Runtime specs
    suspend fun generateStream(prompt: String, grammar: String?, onToken: (String) -> Unit)
}
