package dev.lapislazuli.runtimes.jvm.core.python

import com.sun.net.httpserver.HttpServer
import dev.lapislazuli.runtimes.jvm.core.bundle.BundleManifest
import dev.lapislazuli.runtimes.jvm.core.bundle.ScriptBundle
import dev.lapislazuli.runtimes.jvm.core.runtime.LoadedPlugin
import dev.lapislazuli.runtimes.jvm.core.testsupport.FakeHostServices
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class PythonLanguageRuntimeTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun enablesBundleAndInvokesServiceBridge() {
        val bundleDir = tempDir.resolve("hello-python")
        val sourceDir = bundleDir.resolve("src")
        Files.createDirectories(sourceDir)
        Files.writeString(
            sourceDir.resolve("helpers.py"),
            """
                def greet(name):
                    return f"Hello {name}"
            """.trimIndent(),
        )
        Files.writeString(
            sourceDir.resolve("main.py"),
            """
                from helpers import greet
                
                name = "Hello Python"
                
                def on_enable(context):
                    context.app.log.info("enabled")
                    context.config.set("message", "Hello")
                    context.storage.plugin.set("count", 3)
                    context.storage.files.writeText("hello.txt", "world")
                
                    Duration = context.unsafe.java.type("java.time.Duration")
                    context.app.log.info(f"millis:{Duration.ofMillis(2500).toMillis()}")
                
                    def execute(command):
                        command.sender.sendMessage(greet(command.sender.name))
                        return True
                
                    context.commands.register({
                        "name": "hello",
                        "description": "Send a hello message.",
                        "execute": execute,
                    })
                
                    def on_join(event):
                        context.app.log.info("join:player")
                
                    context.events.on("player.join", on_join)
                
                def on_disable(context):
                    context.app.log.info("disabled")
            """.trimIndent(),
        )

        val bundle = ScriptBundle(
            bundleDirectory = bundleDir,
            manifestPath = bundleDir.resolve("lapis-plugin.json"),
            mainFile = sourceDir.resolve("main.py"),
            manifest = BundleManifest(
                id = "hello-python",
                name = "Hello Python",
                version = "1.0.0",
                engine = "python",
                main = "src/main.py",
                apiVersion = "1.0",
            ),
        )
        val hostServices = FakeHostServices(tempDir.resolve("data"))
        val plugin: LoadedPlugin = PythonLanguageRuntime().load(bundle, hostServices)

        plugin.enable()

        assertEquals("Hello", hostServices.configValues["message"])
        assertEquals(3, hostServices.storageValues["count"])
        assertTrue(Files.exists(tempDir.resolve("data").resolve("hello.txt")))
        assertEquals(listOf("enabled", "millis:2500"), hostServices.logMessages)

        val result = hostServices.commandCallback.invoke(
            mapOf(
                "sender" to mapOf(
                    "name" to "Alice",
                    "type" to "player",
                    "id" to hostServices.player.id(),
                    "player" to hostServices.player,
                    "sendMessage" to dev.lapislazuli.runtimes.jvm.core.host.Callback { payload ->
                        hostServices.sentMessages += payload.toString()
                        null
                    },
                    "hasPermission" to dev.lapislazuli.runtimes.jvm.core.host.Callback { true },
                    "unsafe" to mapOf("handle" to Any()),
                ),
                "args" to emptyList<String>(),
                "label" to "hello",
                "command" to "hello",
            ),
        )

        assertEquals(true, result)
        assertTrue(hostServices.sentMessages.contains("Hello Alice"))
        hostServices.eventCallbacks.getValue("player.join").invoke(hostServices.playerJoinPayload("Bob"))
        assertEquals(listOf("enabled", "millis:2500", "join:player"), hostServices.logMessages)

        plugin.close()
        assertEquals(listOf("enabled", "millis:2500", "join:player", "disabled"), hostServices.logMessages)
    }

    @Test
    fun reportsSyntaxErrorsAgainstTheBundleFile() {
        val bundleDir = tempDir.resolve("broken-python")
        val sourceDir = bundleDir.resolve("src")
        Files.createDirectories(sourceDir)
        Files.writeString(
            sourceDir.resolve("main.py"),
            """
                name = "Broken"
                
                def on_enable(context)
                    context.app.log.info("broken")
            """.trimIndent(),
        )

        val bundle = ScriptBundle(
            bundleDirectory = bundleDir,
            manifestPath = bundleDir.resolve("lapis-plugin.json"),
            mainFile = sourceDir.resolve("main.py"),
            manifest = BundleManifest(
                id = "broken-python",
                name = "Broken Python",
                version = "1.0.0",
                engine = "python",
                main = "src/main.py",
                apiVersion = "1.0",
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            PythonLanguageRuntime().load(bundle, FakeHostServices(tempDir.resolve("data")))
        }

        assertTrue(error.message!!.contains("Failed to parse Python bundle"))
        assertTrue(error.message!!.contains("main.py"))
    }

    @Test
    fun fetchesHttpThroughPythonSdk() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/echo") { exchange ->
            val requestBody = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
            val responseBody = """{"echo":"${exchange.requestHeaders.getFirst("x-lapis")}:$requestBody"}"""
            val responseBytes = responseBody.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        }
        server.start()

        try {
            val bundleDir = tempDir.resolve("python-http")
            val sourceDir = bundleDir.resolve("src")
            Files.createDirectories(sourceDir)
            Files.writeString(
                sourceDir.resolve("main.py"),
                """
                    import json

                    name = "Python Http"

                    def on_enable(context):
                        response = context.http.fetch({
                            "url": "${'$'}{url}",
                            "method": "POST",
                            "headers": {"x-lapis": "python"},
                            "body": "pong",
                        })
                        payload = json.loads(response.body)
                        context.app.log.info(f"http:{response.status}:{response.ok}:{payload['echo']}")
                """.trimIndent().replace("\${url}", "http://127.0.0.1:${server.address.port}/echo"),
            )

            val bundle = ScriptBundle(
                bundleDirectory = bundleDir,
                manifestPath = bundleDir.resolve("lapis-plugin.json"),
                mainFile = sourceDir.resolve("main.py"),
                manifest = BundleManifest(
                    id = "python-http",
                    name = "Python Http",
                    version = "1.0.0",
                    engine = "python",
                    main = "src/main.py",
                    apiVersion = "1.0",
                ),
            )
            val hostServices = FakeHostServices(tempDir.resolve("data-http"))
            val plugin: LoadedPlugin = PythonLanguageRuntime().load(bundle, hostServices)

            plugin.enable()

            assertTrue(
                hostServices.logMessages.any { it.contains("http:200") && it.contains("pong") },
                hostServices.logMessages.toString(),
            )
        } finally {
            server.stop(0)
        }
    }
}
