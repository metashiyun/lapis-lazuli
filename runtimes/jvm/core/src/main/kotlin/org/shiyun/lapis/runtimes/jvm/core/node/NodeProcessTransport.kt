package org.shiyun.lapis.runtimes.jvm.core.node

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class NodeProcessTransport(
    private val process: Process,
    private val requestHandler: (method: String, params: Any?) -> Any?,
) : AutoCloseable {
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val pending = ConcurrentHashMap<String, CompletableFuture<Any?>>()
    private val closed = AtomicBoolean(false)
    private val requestExecutor = Executors.newCachedThreadPool()
    private val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
    private val writer = BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8))
    private val stderrReader = BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8))
    private val stderrBuffer = StringBuilder()
    private val writeLock = Any()
    private val stdoutThread = Thread(::readLoop, "lapis-node-stdout").apply {
        isDaemon = true
        start()
    }
    private val stderrThread = Thread(::readStderr, "lapis-node-stderr").apply {
        isDaemon = true
        start()
    }
    private val processWaitThread = Thread(::waitForExit, "lapis-node-exit").apply {
        isDaemon = true
        start()
    }

    fun request(method: String, params: Any?): Any? {
        check(!closed.get()) { "Node runtime transport is closed." }

        val requestId = UUID.randomUUID().toString()
        val future = CompletableFuture<Any?>()
        pending[requestId] = future
        writeMessage(
            mapOf(
                "type" to "request",
                "id" to requestId,
                "method" to method,
                "params" to params,
            ),
        )

        return try {
            future.get()
        } finally {
            pending.remove(requestId)
        }
    }

    fun stderrText(): String = synchronized(stderrBuffer) { stderrBuffer.toString().trim() }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        pending.values.forEach { future ->
            future.completeExceptionally(IllegalStateException("Node runtime transport closed."))
        }
        pending.clear()

        runCatching { writer.close() }
        runCatching { reader.close() }
        runCatching { stderrReader.close() }
        requestExecutor.shutdownNow()
    }

    private fun readLoop() {
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) {
                    continue
                }

                @Suppress("UNCHECKED_CAST")
                val message = mapper.readValue(line, Map::class.java) as Map<String, Any?>
                when (message["type"]) {
                    "response" -> handleResponse(message)
                    "request" -> handleRequest(message)
                }
            }
        } catch (_: Exception) {
            // Process shutdown is handled in waitForExit.
        }
    }

    private fun readStderr() {
        try {
            while (true) {
                val line = stderrReader.readLine() ?: break
                synchronized(stderrBuffer) {
                    if (stderrBuffer.isNotEmpty()) {
                        stderrBuffer.append('\n')
                    }
                    stderrBuffer.append(line)
                }
            }
        } catch (_: Exception) {
            // Best effort stderr capture only.
        }
    }

    private fun waitForExit() {
        val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
        val message = buildString {
            append("Node runtime process exited")
            if (exitCode >= 0) {
                append(" with code $exitCode")
            }
            val stderr = stderrText()
            if (stderr.isNotBlank()) {
                append(".\n")
                append(stderr)
            }
        }
        val error = IllegalStateException(message)

        if (closed.compareAndSet(false, true)) {
            pending.values.forEach { future -> future.completeExceptionally(error) }
            pending.clear()
            requestExecutor.shutdownNow()
        }
    }

    private fun handleResponse(message: Map<String, Any?>) {
        val requestId = message["id"] as? String ?: return
        val future = pending[requestId] ?: return
        val ok = message["ok"] as? Boolean ?: false
        if (ok) {
            future.complete(message["result"])
            return
        }

        val error = message["error"] as? Map<*, *>
        val messageText = error?.get("message")?.toString() ?: "Node runtime request failed."
        future.completeExceptionally(IllegalStateException(messageText))
    }

    private fun handleRequest(message: Map<String, Any?>) {
        val requestId = message["id"] as? String ?: return
        val method = message["method"] as? String ?: return
        val params = message["params"]

        requestExecutor.submit {
            try {
                val result = requestHandler(method, params)
                writeMessage(
                    mapOf(
                        "type" to "response",
                        "id" to requestId,
                        "ok" to true,
                        "result" to result,
                    ),
                )
            } catch (error: Exception) {
                writeMessage(
                    mapOf(
                        "type" to "response",
                        "id" to requestId,
                        "ok" to false,
                        "error" to mapOf(
                            "message" to (error.message ?: error::class.java.simpleName),
                        ),
                    ),
                )
            }
        }
    }

    private fun writeMessage(message: Map<String, Any?>) {
        if (closed.get()) {
            return
        }

        val encoded = mapper.writeValueAsString(message)
        synchronized(writeLock) {
            writer.write(encoded)
            writer.newLine()
            writer.flush()
        }
    }
}
