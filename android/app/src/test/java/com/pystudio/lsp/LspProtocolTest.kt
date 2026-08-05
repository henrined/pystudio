package com.pystudio.lsp

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets

/**
 * S-5.4: Tests LSP — autocomplétion Python, diagnostics C++ en temps réel.
 *
 * These unit tests verify the JSON-RPC framing layer (Content-Length protocol)
 * that is the foundation of all LSP communication. They test:
 *   - Message encoding (Content-Length header generation)
 *   - Message decoding (Content-Length parsing + body extraction)
 *   - Round-trip (encode → decode → identical)
 *   - Edge cases (UTF-8 multibyte, empty body, multiple messages)
 *   - LSP request construction (initialize, didOpen, completion)
 */
class LspProtocolTest {

    // ─── Encoding tests ─────────────────────────────────────────────────────

    @Test
    fun testEncodeMessageAddsContentLengthHeader() {
        val body = """{"jsonrpc":"2.0","id":1,"method":"initialize"}"""
        val encoded = String(LspProtocol.encodeMessage(body), StandardCharsets.UTF_8)

        assertTrue("Should start with Content-Length header",
            encoded.startsWith("Content-Length:"))
        assertTrue("Should contain CRLFCRLF separator",
            encoded.contains("\r\n\r\n"))
        assertTrue("Should end with body",
            encoded.endsWith(body))
    }

    @Test
    fun testEncodeMessageContentLengthMatchesBodyBytes() {
        val body = """{"jsonrpc":"2.0","id":1,"result":null}"""
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val encoded = String(LspProtocol.encodeMessage(body), StandardCharsets.UTF_8)

        val expectedHeader = "Content-Length: ${bodyBytes.size}\r\n\r\n"
        assertTrue("Header should contain correct byte count",
            encoded.startsWith(expectedHeader))
    }

    @Test
    fun testEncodeMessageUtf8MultibyteCounting() {
        // UTF-8: "é" = 2 bytes, "日" = 3 bytes
        val body = """{"text":"café日本語"}"""
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val encoded = String(LspProtocol.encodeMessage(body), StandardCharsets.UTF_8)

        // Content-Length should count bytes, not characters
        assertTrue("Should count bytes not chars for multibyte",
            encoded.contains("Content-Length: ${bodyBytes.size}"))
    }

    // ─── Decoding tests ─────────────────────────────────────────────────────

    @Test
    fun testReadMessageParsesValidFrame() {
        val body = """{"jsonrpc":"2.0","id":1,"result":{"capabilities":{}}}"""
        val frame = LspProtocol.encodeMessage(body)
        val input = ByteArrayInputStream(frame)

        val result = LspProtocol.readMessage(input)
        assertEquals("Decoded body should match original", body, result)
    }

    @Test
    fun testReadMessageReturnsNullOnEmptyStream() {
        val input = ByteArrayInputStream(ByteArray(0))
        val result = LspProtocol.readMessage(input)
        assertNull("Should return null on empty stream", result)
    }

    @Test
    fun testReadMessageReturnsNullOnTruncatedHeader() {
        val truncated = "Content-Length: 42\r\n".toByteArray(StandardCharsets.UTF_8)
        val input = ByteArrayInputStream(truncated)
        // Stream ends before \r\n\r\n separator
        val result = LspProtocol.readMessage(input)
        assertNull("Should return null on truncated header", result)
    }

    @Test
    fun testReadMessageReturnsNullOnTruncatedBody() {
        // Header claims 100 bytes but body is only 10
        val header = "Content-Length: 100\r\n\r\n"
        val partialBody = "short"
        val bytes = (header + partialBody).toByteArray(StandardCharsets.UTF_8)
        val input = ByteArrayInputStream(bytes)
        val result = LspProtocol.readMessage(input)
        assertNull("Should return null on truncated body", result)
    }

    // ─── Round-trip tests ────────────────────────────────────────────────────

    @Test
    fun testRoundTripSimpleMessage() {
        val body = """{"jsonrpc":"2.0","method":"shutdown"}"""
        val frame = LspProtocol.encodeMessage(body)
        val decoded = LspProtocol.readMessage(ByteArrayInputStream(frame))
        assertEquals(body, decoded)
    }

    @Test
    fun testRoundTripMultipleMessages() {
        val messages = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"initialized","params":{}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/completion","params":{}}"""
        )

        // Concatenate all frames
        val allFrames = ByteArrayOutputStream()
        messages.forEach { allFrames.write(LspProtocol.encodeMessage(it)) }
        val input = ByteArrayInputStream(allFrames.toByteArray())

        // Read them back one by one
        val decoded = mutableListOf<String>()
        LspProtocol.readLoop(input, onMessage = { decoded.add(it) })

        assertEquals("Should decode all 3 messages", 3, decoded.size)
        assertEquals(messages, decoded)
    }

    @Test
    fun testRoundTripUtf8Content() {
        val body = """{"jsonrpc":"2.0","id":1,"result":{"label":"café ☕ 日本語"}}"""
        val frame = LspProtocol.encodeMessage(body)
        val decoded = LspProtocol.readMessage(ByteArrayInputStream(frame))
        assertEquals("UTF-8 round trip should be lossless", body, decoded)
    }

    // ─── sendMessage tests ───────────────────────────────────────────────────

    @Test
    fun testSendMessageWritesToStream() {
        val output = ByteArrayOutputStream()
        val body = """{"jsonrpc":"2.0","method":"exit"}"""

        val result = LspProtocol.sendMessage(output, body)

        assertTrue("sendMessage should return true", result)
        // Verify we can decode what was written
        val decoded = LspProtocol.readMessage(ByteArrayInputStream(output.toByteArray()))
        assertEquals(body, decoded)
    }

    @Test
    fun testSendMessageReturnsFalseForNullStream() {
        val result = LspProtocol.sendMessage(null, """{"jsonrpc":"2.0"}""")
        assertFalse("sendMessage should return false for null stream", result)
    }

    // ─── LSP request construction tests ──────────────────────────────────────

    @Test
    fun testBuildInitializeRequestIsValidJsonRpc() {
        val request = LspProtocol.buildInitializeRequest(1, "/workspace/project")

        assertTrue("Should contain jsonrpc version", request.contains("\"jsonrpc\":\"2.0\""))
        assertTrue("Should contain request id", request.contains("\"id\":1"))
        assertTrue("Should contain initialize method", request.contains("\"method\":\"initialize\""))
        assertTrue("Should contain rootUri", request.contains("\"rootUri\":\"file:///workspace/project\""))
        assertTrue("Should contain capabilities", request.contains("\"capabilities\""))
        assertTrue("Should contain completion support", request.contains("\"completion\""))
        assertTrue("Should contain hover support", request.contains("\"hover\""))
        assertTrue("Should contain diagnostics support", request.contains("\"publishDiagnostics\""))
    }

    @Test
    fun testBuildDidOpenNotificationIsValidJsonRpc() {
        val notification = LspProtocol.buildDidOpenNotification(
            uri = "file:///project/main.py",
            languageId = "python",
            version = 1,
            text = "import os\nprint('hello')\n"
        )

        assertTrue("Should be a notification (no id)", !notification.contains("\"id\""))
        assertTrue("Should contain didOpen method",
            notification.contains("\"method\":\"textDocument/didOpen\""))
        assertTrue("Should contain uri",
            notification.contains("\"uri\":\"file:///project/main.py\""))
        assertTrue("Should contain languageId python",
            notification.contains("\"languageId\":\"python\""))
    }

    @Test
    fun testBuildDidOpenEscapesSpecialChars() {
        val notification = LspProtocol.buildDidOpenNotification(
            uri = "file:///project/test.py",
            languageId = "python",
            version = 1,
            text = "msg = \"hello\\nworld\"\n"
        )

        // Should not break JSON (no unescaped newlines or quotes in text field)
        assertFalse("Text should not contain raw newlines",
            notification.contains("\n") && notification.indexOf("\n") > notification.indexOf("\"text\":"))
    }

    @Test
    fun testBuildCompletionRequestIsValidJsonRpc() {
        val request = LspProtocol.buildCompletionRequest(
            requestId = 42,
            uri = "file:///project/main.py",
            line = 5,
            character = 10
        )

        assertTrue("Should contain id 42", request.contains("\"id\":42"))
        assertTrue("Should contain completion method",
            request.contains("\"method\":\"textDocument/completion\""))
        assertTrue("Should contain position",
            request.contains("\"line\":5") && request.contains("\"character\":10"))
    }

    // ─── Integration: full LSP handshake simulation ──────────────────────────

    @Test
    fun testFullLspHandshakeSimulation() {
        // Simulate: client sends initialize → server responds → client sends didOpen
        val pipe = ByteArrayOutputStream()

        // 1. Client sends initialize
        val initReq = LspProtocol.buildInitializeRequest(1, "/workspace")
        LspProtocol.sendMessage(pipe, initReq)

        // 2. Simulate server response
        val initResponse = """{"jsonrpc":"2.0","id":1,"result":{"capabilities":{"completionProvider":{"triggerCharacters":["."]},"hoverProvider":true,"textDocumentSync":1}}}"""
        pipe.write(LspProtocol.encodeMessage(initResponse))

        // 3. Client sends didOpen
        val didOpen = LspProtocol.buildDidOpenNotification(
            "file:///workspace/main.py", "python", 1, "import os\n")
        pipe.write(LspProtocol.encodeMessage(didOpen))

        // 4. Client sends completion request
        val completion = LspProtocol.buildCompletionRequest(2, "file:///workspace/main.py", 1, 0)
        pipe.write(LspProtocol.encodeMessage(completion))

        // Verify all 4 messages can be decoded
        val input = ByteArrayInputStream(pipe.toByteArray())
        val messages = mutableListOf<String>()
        LspProtocol.readLoop(input, onMessage = { messages.add(it) })

        assertEquals("Should decode 4 messages in handshake", 4, messages.size)
        assertEquals("First should be initialize request", initReq, messages[0])
        assertEquals("Second should be server response", initResponse, messages[1])
        assertTrue("Fourth should be completion request", messages[3].contains("textDocument/completion"))
    }

    // ─── Python-specific tests ───────────────────────────────────────────────

    @Test
    fun testPythonCompletionRequestForImport() {
        // S-5.4: test de l'autocomplétion Python
        val req = LspProtocol.buildCompletionRequest(
            requestId = 10,
            uri = "file:///project/main.py",
            line = 0,      // first line: "import o|" cursor after 'o'
            character = 8
        )

        assertTrue(req.contains("\"method\":\"textDocument/completion\""))
        assertTrue(req.contains("\"line\":0"))
        assertTrue(req.contains("\"character\":8"))

        // Verify it can be framed and decoded
        val frame = LspProtocol.encodeMessage(req)
        val decoded = LspProtocol.readMessage(ByteArrayInputStream(frame))
        assertEquals(req, decoded)
    }

    // ─── C++ diagnostic test ─────────────────────────────────────────────────

    @Test
    fun testCppDiagnosticsNotificationParsing() {
        // S-5.4: test des diagnostics C++ en temps réel
        // Simulate what clangd would send for a syntax error
        val diagnostic = """{"jsonrpc":"2.0","method":"textDocument/publishDiagnostics","params":{"uri":"file:///project/main.cpp","diagnostics":[{"range":{"start":{"line":4,"character":0},"end":{"line":4,"character":5}},"severity":1,"source":"clang","message":"expected ';' after top level declarator"}]}}"""

        // Verify framing round-trip works for diagnostic messages
        val frame = LspProtocol.encodeMessage(diagnostic)
        val decoded = LspProtocol.readMessage(ByteArrayInputStream(frame))
        assertEquals("Diagnostic message round-trip", diagnostic, decoded)

        // Verify key fields
        assertTrue(decoded!!.contains("publishDiagnostics"))
        assertTrue(decoded.contains("main.cpp"))
        assertTrue(decoded.contains("expected ';'"))
        assertTrue(decoded.contains("\"severity\":1"))
    }
}
