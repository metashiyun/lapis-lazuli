package org.shiyun.lapislazuli.runtime.core.python

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.shiyun.lapislazuli.runtime.core.bundle.BundleManifest
import org.shiyun.lapislazuli.runtime.core.bundle.ScriptBundle
import org.shiyun.lapislazuli.runtime.core.host.Callback
import org.shiyun.lapislazuli.runtime.core.host.ConfigStore
import org.shiyun.lapislazuli.runtime.core.host.DataDirectory
import org.shiyun.lapislazuli.runtime.core.host.HostServices
import org.shiyun.lapislazuli.runtime.core.host.Registration
import org.shiyun.lapislazuli.runtime.core.host.RuntimeLogger
import org.shiyun.lapislazuli.runtime.core.host.TaskHandle
import org.shiyun.lapislazuli.runtime.core.runtime.LoadedPlugin
import java.nio.file.Files
import java.nio.file.Path

class PythonLanguageRuntimeTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun enablesBundleAndInvokesHostBridge() {
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
                    context.logger.info("enabled")
                    context.config.set("message", "Hello")
                    context.dataDir.writeText("hello.txt", "world")
                
                    Duration = context.javaInterop.type("java.time.Duration")
                    context.logger.info(f"millis:{Duration.ofMillis(2500).toMillis()}")
                
                    def execute(command):
                        command.sender.sendMessage(greet(command.sender.name))
                        return True
                
                    context.commands.register("hello", execute, "Send a hello message.")
                
                    def on_join(event):
                        context.logger.info(f"join:{event.playerName}")
                
                    context.events.on("playerJoin", on_join)
                
                def on_disable(context):
                    context.logger.info("disabled")
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
        assertTrue(Files.exists(tempDir.resolve("data").resolve("hello.txt")))
        assertEquals(listOf("enabled", "millis:2500"), hostServices.logMessages)

        val result = hostServices.commandCallback.invoke(
            mapOf(
                "sender" to mapOf(
                    "name" to "Alice",
                    "type" to "player",
                    "uuid" to "1234",
                    "sendMessage" to Callback { payload ->
                        hostServices.sentMessages += payload.toString()
                        null
                    },
                ),
                "args" to emptyList<String>(),
                "label" to "hello",
            ),
        )

        assertEquals(true, result)
        assertEquals(listOf("Hello Alice"), hostServices.sentMessages)

        hostServices.eventCallback.invoke(mapOf("playerName" to "Bob"))
        assertEquals(listOf("enabled", "millis:2500", "join:Bob"), hostServices.logMessages)

        plugin.close()
        assertEquals(listOf("enabled", "millis:2500", "join:Bob", "disabled"), hostServices.logMessages)
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
                    context.logger.info("broken")
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

    private class FakeHostServices(
        private val dataRoot: Path,
    ) : HostServices {
        val configValues = linkedMapOf<String, Any?>()
        val logMessages = mutableListOf<String>()
        val sentMessages = mutableListOf<String>()
        lateinit var commandCallback: Callback
        lateinit var eventCallback: Callback

        init {
            Files.createDirectories(dataRoot)
        }

        override fun logger(): RuntimeLogger =
            object : RuntimeLogger {
                override fun info(message: String) {
                    logMessages += message
                }

                override fun warn(message: String) {
                    logMessages += "warn:$message"
                }

                override fun error(message: String, error: Throwable?) {
                    logMessages += "error:$message"
                }
            }

        override fun registerCommand(
            name: String,
            description: String,
            usage: String,
            aliases: List<String>,
            execute: Callback,
        ): Registration {
            commandCallback = execute
            return Registration {}
        }

        override fun registerEvent(eventKey: String, handler: Callback): Registration {
            eventCallback = handler
            return Registration {}
        }

        override fun runNow(task: Callback): TaskHandle {
            task.invoke(null)
            return TaskHandle {}
        }

        override fun runLater(delayTicks: Long, task: Callback): TaskHandle {
            task.invoke(null)
            return TaskHandle {}
        }

        override fun runTimer(delayTicks: Long, intervalTicks: Long, task: Callback): TaskHandle =
            TaskHandle {}

        override fun config(): ConfigStore =
            object : ConfigStore {
                override fun get(path: String): Any? = configValues[path]

                override fun set(path: String, value: Any?) {
                    configValues[path] = value
                }

                override fun save() {
                }

                override fun reload() {
                }

                override fun keys(): List<String> = configValues.keys.toList()
            }

        override fun dataDirectory(): DataDirectory =
            object : DataDirectory {
                override fun path(): String = dataRoot.toString()

                override fun resolve(vararg segments: String): String =
                    segments.fold(dataRoot) { path, segment -> path.resolve(segment) }.toString()

                override fun readText(relativePath: String): String =
                    Files.readString(dataRoot.resolve(relativePath))

                override fun writeText(relativePath: String, contents: String) {
                    val target = dataRoot.resolve(relativePath)
                    target.parent?.let(Files::createDirectories)
                    Files.writeString(target, contents)
                }

                override fun exists(relativePath: String): Boolean =
                    Files.exists(dataRoot.resolve(relativePath))

                override fun mkdirs(relativePath: String) {
                    Files.createDirectories(dataRoot.resolve(relativePath))
                }
            }

        override fun javaType(className: String): Class<*> = Class.forName(className)

        override fun close() {
        }
    }
}
