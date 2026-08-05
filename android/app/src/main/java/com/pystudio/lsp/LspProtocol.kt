package com.pystudio.lsp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * S-5.3: JSON-RPC over LSP protocol framing.
 *
 * Handles Content-Length based message framing as specified by the
 * Language Server Protocol (LSP) specification §Base Protocol.
 * Extracted from LspService for testability.
 */
object LspProtocol {

    /**
     * Encodes a JSON-RPC message with Content-Length header.
     * Returns the full framed message as bytes.
     */
    fun encodeMessage(jsonBody: String): ByteArray {
        val bodyBytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${bodyBytes.size}\r\n\r\n"
        return header.toByteArray(StandardCharsets.UTF_8) + bodyBytes
    }

    /**
     * Writes a framed JSON-RPC message to the given output stream.
     * Returns true on success, false if the stream is null or an error occurs.
     */
    fun sendMessage(output: OutputStream?, jsonBody: String): Boolean {
        val out = output ?: return false
        return try {
            out.write(encodeMessage(jsonBody))
            out.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads a single framed JSON-RPC message from the input stream.
     * Blocks until a complete message is received or the stream ends.
     * Returns null if the stream ends before a complete message.
     */
    fun readMessage(input: InputStream): String? {
        val headerBuilder = StringBuilder()

        while (true) {
            val b = input.read()
            if (b == -1) return null

            headerBuilder.append(b.toChar())

            if (headerBuilder.endsWith("\r\n\r\n")) {
                val headers = headerBuilder.toString()
                val match = Regex("Content-Length: (\\d+)").find(headers)
                    ?: return null

                val contentLength = match.groupValues[1].toInt()
                val bodyBytes = ByteArray(contentLength)
                var readCount = 0
                while (readCount < contentLength) {
                    val res = input.read(bodyBytes, readCount, contentLength - readCount)
                    if (res == -1) return null
                    readCount += res
                }
                return String(bodyBytes, StandardCharsets.UTF_8)
            }
        }
    }

    /**
     * Continuously reads framed messages from an input stream,
     * calling onMessage for each complete message received.
     * Returns when the stream ends or an error occurs.
     */
    fun readLoop(input: InputStream, onMessage: (String) -> Unit, onError: ((String) -> Unit)? = null) {
        try {
            while (true) {
                val msg = readMessage(input) ?: break
                onMessage(msg)
            }
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "Unknown error")
        }
    }

    /**
     * Builds the LSP 'initialize' request JSON-RPC message.
     */
    fun buildInitializeRequest(requestId: Int, workspacePath: String): String {
        return """{"jsonrpc":"2.0","id":$requestId,"method":"initialize","params":{"processId":null,"rootUri":"file://$workspacePath","capabilities":{"textDocument":{"completion":{"completionItem":{"snippetSupport":true}},"hover":{"contentFormat":["markdown","plaintext"]},"publishDiagnostics":{"relatedInformation":true}}}}}"""
    }

    /**
     * Builds a textDocument/didOpen notification.
     */
    fun buildDidOpenNotification(uri: String, languageId: String, version: Int, text: String): String {
        // Escape the text for JSON
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        return """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","languageId":"$languageId","version":$version,"text":"$escaped"}}}"""
    }

    /**
     * Builds a textDocument/completion request.
     */
    fun buildCompletionRequest(requestId: Int, uri: String, line: Int, character: Int): String {
        return """{"jsonrpc":"2.0","id":$requestId,"method":"textDocument/completion","params":{"textDocument":{"uri":"$uri"},"position":{"line":$line,"character":$character}}}"""
    }
}
