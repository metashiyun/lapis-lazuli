package dev.lapislazuli.runtime.core.integration

import dev.lapislazuli.runtime.core.bundle.BundleManifestParser
import dev.lapislazuli.runtime.core.bundle.ScriptBundleLoader
import dev.lapislazuli.runtime.core.host.Callback
import dev.lapislazuli.runtime.core.host.ConfigStore
import dev.lapislazuli.runtime.core.host.DataDirectory
import dev.lapislazuli.runtime.core.host.HostServices
import dev.lapislazuli.runtime.core.host.Registration
import dev.lapislazuli.runtime.core.host.RuntimeLogger
import dev.lapislazuli.runtime.core.host.TaskHandle
import dev.lapislazuli.runtime.core.js.JsLanguageRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ExampleBundleIntegrationTest {
    @Test
    fun bundlesExamplePluginAndLoadsItThroughTheRuntime() {
        val repoRoot = Paths.get("").toAbsolutePath().parent
        val exampleDir = repoRoot.resolve("examples/hello-ts")
        val bundleDir = exampleDir.resolve("dist/hello-ts")

        bundleExample(repoRoot)

        val bundle = ScriptBundleLoader(BundleManifestParser()).load(bundleDir)
        val hostServices = FakeHostServices(bundleDir.resolve("data-test"))
        val plugin = JsLanguageRuntime().load(bundle, hostServices)

        plugin.enable()

        assertEquals(listOf("Hello TS enabled."), hostServices.logMessages)

        val commandResult = hostServices.commandCallback.invoke(
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

        assertEquals(null, commandResult)
        assertEquals(listOf("Hello from TypeScript."), hostServices.sentMessages)

        hostServices.eventCallback.invoke(mapOf("playerName" to "Bob"))
        assertEquals(
            listOf("Hello TS enabled.", "Player joined: Bob"),
            hostServices.logMessages,
        )

        plugin.close()
        assertTrue(hostServices.logMessages.contains("Hello TS disabled."))
    }

    private fun bundleExample(repoRoot: Path) {
        val process = ProcessBuilder(
            "bun",
            "packages/cli/src/index.ts",
            "bundle",
            "examples/hello-ts",
        )
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            "Failed to bundle example plugin.\n$output"
        }
    }

    private class FakeHostServices(
        private val dataRoot: Path,
    ) : HostServices {
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

        override fun runNow(task: Callback): TaskHandle = TaskHandle {}

        override fun runLater(delayTicks: Long, task: Callback): TaskHandle = TaskHandle {}

        override fun runTimer(delayTicks: Long, intervalTicks: Long, task: Callback): TaskHandle = TaskHandle {}

        override fun config(): ConfigStore =
            object : ConfigStore {
                override fun get(path: String): Any? = null

                override fun set(path: String, value: Any?) {
                }

                override fun save() {
                }

                override fun reload() {
                }

                override fun keys(): List<String> = emptyList()
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
