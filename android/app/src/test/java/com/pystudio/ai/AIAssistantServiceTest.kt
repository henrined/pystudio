package com.pystudio.ai

import com.pystudio.core.fs.FileSystemService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AIAssistantServiceTest {

    @Test
    fun testDiffApplicator_SimpleReplace() {
        val originalCode = """
            def example():
                print("hello")
                return True
        """.trimIndent()
        
        val diff = """
            --- a/script.py
            +++ b/script.py
            @@ -1,3 +1,3 @@
             def example():
            -    print("hello")
            +    print("world")
                 return True
        """.trimIndent()
        
        val expectedCode = """
            def example():
                print("world")
                return True
        """.trimIndent()
        
        val result = DiffApplicator.applyDiff(originalCode, diff)
        assertEquals(expectedCode, result)
    }

    @Test
    fun testAIAssistantServiceImpl_runAction_withMockContextBuilder() = runTest {
        val fs = object : FileSystemService {
            override fun readFile(path: String) = ""
            override fun writeFile(path: String, content: String) {}
        }
        val mockContextBuilder = object : ContextBuilderService {
            override suspend fun buildContext(request: AIActionRequest): AIContext {
                return AIContext(promptText = "Mocked Prompt", systemPrompt = "Mocked System Prompt")
            }
        }
        val mockGateway = object : InferenceRuntimeGateway {
            override suspend fun generateStream(prompt: String, grammar: String?, onToken: (String) -> Unit) {
                onToken("generated diff content")
            }
        }

        val service = AIAssistantServiceImpl(mockContextBuilder, mockGateway, fs)
        
        val request = AIActionRequest(
            function = AIFunction.REFACTOR,
            filePath = "/main.py",
            selectionStartLine = 1,
            selectionEndLine = 2
        )
        
        val events = mutableListOf<AIActionProgressEvent>()
        val job = launch(UnconfinedTestDispatcher()) {
            service.actionProgress().toList(events)
        }

        val actionId = service.runAction(request)
        assertTrue(actionId.isNotEmpty())
        
        val statusList = events.map { it.status }
        assertTrue(statusList.contains("building_context"))
        assertTrue(statusList.contains("generating"))
        assertTrue(statusList.contains("ready_for_review"))
        
        job.cancel()
    }

    @Test
    fun testApplyActionResult_Accept() = runTest {
        var writtenPath = ""
        var writtenContent = ""
        val fs = object : FileSystemService {
            override fun readFile(path: String) = "def old_func(): pass"
            override fun writeFile(path: String, content: String) {
                writtenPath = path
                writtenContent = content
            }
        }
        val mockContextBuilder = object : ContextBuilderService {
            override suspend fun buildContext(request: AIActionRequest) = AIContext("p", "s")
        }
        val mockGateway = object : InferenceRuntimeGateway {
            override suspend fun generateStream(prompt: String, grammar: String?, onToken: (String) -> Unit) {
                val diff = """
                    --- a/main.py
                    +++ b/main.py
                    @@ -1,1 +1,1 @@
                    -def old_func(): pass
                    +def new_func(): pass
                """.trimIndent()
                onToken(diff)
            }
        }
        val service = AIAssistantServiceImpl(mockContextBuilder, mockGateway, fs)
        val actionId = service.runAction(AIActionRequest(AIFunction.REFACTOR, "/main.py", 1, 1))
        
        service.applyActionResult(actionId, "accept", null)
        
        assertEquals("/main.py", writtenPath)
        assertEquals("def new_func(): pass", writtenContent.trim())
    }

    @Test
    fun testApplyActionResult_Reject() = runTest {
        var writeCalled = false
        val fs = object : FileSystemService {
            override fun readFile(path: String) = "def old_func(): pass"
            override fun writeFile(path: String, content: String) {
                writeCalled = true
            }
        }
        val mockContextBuilder = object : ContextBuilderService {
            override suspend fun buildContext(request: AIActionRequest) = AIContext("p", "s")
        }
        val mockGateway = object : InferenceRuntimeGateway {
            override suspend fun generateStream(prompt: String, grammar: String?, onToken: (String) -> Unit) {
                onToken("dummy diff")
            }
        }
        val service = AIAssistantServiceImpl(mockContextBuilder, mockGateway, fs)
        val actionId = service.runAction(AIActionRequest(AIFunction.REFACTOR, "/main.py", 1, 1))
        
        service.applyActionResult(actionId, "reject", null)
        
        assertTrue(!writeCalled)
    }

    @Test
    fun testCloudFallback_WhenLocalFails() = runTest {
        val fs = object : FileSystemService {
            override fun readFile(path: String) = ""
            override fun writeFile(path: String, content: String) {}
        }
        val mockContextBuilder = object : ContextBuilderService {
            override suspend fun buildContext(request: AIActionRequest) = AIContext("p", "s")
        }
        val failingLocalGateway = object : InferenceRuntimeGateway {
            override suspend fun generateStream(prompt: String, grammar: String?, onToken: (String) -> Unit) {
                throw RuntimeException("Local model OOM")
            }
        }
        val mockCloudClient = object : AICloudClient {
            override suspend fun generateCompletion(prompt: String, systemPrompt: String?): String {
                return "cloud diff content"
            }
        }
        val service = AIAssistantServiceImpl(mockContextBuilder, failingLocalGateway, fs, mockCloudClient)
        
        val events = mutableListOf<AIActionProgressEvent>()
        val job = launch(UnconfinedTestDispatcher()) {
            service.actionProgress().toList(events)
        }

        val actionId = service.runAction(AIActionRequest(AIFunction.REFACTOR, "/main.py", 1, 1))
        
        val readyEvent = events.find { it.status == "ready_for_review" }
        assertTrue(readyEvent != null)
        assertEquals("cloud diff content", readyEvent?.diffPreview)
        
        job.cancel()
    }
}
