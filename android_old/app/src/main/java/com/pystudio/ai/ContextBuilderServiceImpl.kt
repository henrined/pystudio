package com.pystudio.ai

import com.pystudio.core.fs.FileSystemService

class ContextBuilderServiceImpl(private val fileSystemService: FileSystemService) : ContextBuilderService {

    override suspend fun buildContext(request: AIActionRequest): PromptContext {
        val fileContent = try {
            fileSystemService.readFile(request.filePath)
        } catch (e: Exception) {
            "// Could not read file: ${e.message}"
        }

        val selectionContext = if (request.selectionStartLine != null && request.selectionEndLine != null) {
            "Selected lines ${request.selectionStartLine} to ${request.selectionEndLine}."
        } else {
            "Entire file context."
        }

        val promptBuilder = java.lang.StringBuilder()
        var systemPrompt: String? = null
        var grammar: String? = null

        when (request.function) {
            AIFunction.EXPLAIN_ERROR -> {
                systemPrompt = "You are an expert developer helping to debug errors."
                promptBuilder.append("Explain the following error:\n")
                promptBuilder.append("${request.errorMessage}\n")
                if (request.errorStackTrace != null) {
                    promptBuilder.append("Stack trace:\n${request.errorStackTrace}\n")
                }
                promptBuilder.append("\nContext:\n$selectionContext\n$fileContent")
            }
            AIFunction.GENERATE_TESTS -> {
                systemPrompt = "You are an expert developer writing unit tests in pytest format."
                grammar = "diff.gbnf" // Forces output to be a diff
                promptBuilder.append("Generate tests for the following code:\n")
                promptBuilder.append("\nContext:\n$selectionContext\n$fileContent")
            }
            AIFunction.REFACTOR -> {
                systemPrompt = "You are an expert developer refactoring code."
                grammar = "diff.gbnf"
                promptBuilder.append("Refactor the following code to improve readability and performance:\n")
                promptBuilder.append("\nContext:\n$selectionContext\n$fileContent")
            }
            AIFunction.GENERATE_DOCS -> {
                systemPrompt = "You are an expert developer writing documentation strings."
                grammar = "diff.gbnf"
                promptBuilder.append("Generate Google style docstrings for the following code:\n")
                promptBuilder.append("\nContext:\n$selectionContext\n$fileContent")
            }
            else -> {
                promptBuilder.append("Context:\n$selectionContext\n$fileContent")
            }
        }

        return PromptContext(
            promptText = promptBuilder.toString(),
            systemPrompt = systemPrompt,
            grammar = grammar
        )
    }
}
