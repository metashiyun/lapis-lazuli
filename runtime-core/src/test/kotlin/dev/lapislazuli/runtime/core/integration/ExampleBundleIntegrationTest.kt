package dev.lapislazuli.runtime.core.integration

import dev.lapislazuli.runtime.core.bundle.BundleManifestParser
import dev.lapislazuli.runtime.core.bundle.ScriptBundleLoader
import dev.lapislazuli.runtime.core.host.Callback
import dev.lapislazuli.runtime.core.js.JsLanguageRuntime
import dev.lapislazuli.runtime.core.python.PythonLanguageRuntime
import dev.lapislazuli.runtime.core.testsupport.FakeHostServices
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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

        assertTrue(hostServices.logMessages.contains("Hello TS enabled."))
        assertTrue(hostServices.logMessages.contains("Server ready event observed."))

        val commandResult = hostServices.commandCallback.invoke(
            mapOf(
                "sender" to mapOf(
                    "name" to "Alice",
                    "type" to "player",
                    "id" to hostServices.player.id(),
                    "player" to hostServices.player,
                    "sendMessage" to Callback { payload ->
                        hostServices.sentMessages += payload.toString()
                        null
                    },
                    "hasPermission" to Callback { true },
                    "unsafe" to mapOf("handle" to Any()),
                ),
                "args" to emptyList<String>(),
                "label" to "hello",
                "command" to "hello",
            ),
        )

        assertEquals(null, commandResult)
        assertTrue(hostServices.sentMessages.contains("Hello from TypeScript."))

        hostServices.eventCallbacks.getValue("server.ready").invoke(hostServices.serverReadyPayload())
        hostServices.eventCallbacks.getValue("player.join").invoke(hostServices.playerJoinPayload("Bob"))
        assertTrue(hostServices.logMessages.contains("Player joined: Bob"))

        plugin.close()
        assertTrue(hostServices.logMessages.contains("Hello TS disabled."))
    }

    @Test
    fun bundlesExamplePythonPluginAndLoadsItThroughTheRuntime() {
        val repoRoot = Paths.get("").toAbsolutePath().parent
        val exampleDir = repoRoot.resolve("examples/hello-python")
        val bundleDir = exampleDir.resolve("dist/hello-python")

        bundleExample(repoRoot, "examples/hello-python")

        val bundle = ScriptBundleLoader(BundleManifestParser()).load(bundleDir)
        val hostServices = FakeHostServices(bundleDir.resolve("data-test"))
        val plugin = PythonLanguageRuntime().load(bundle, hostServices)

        plugin.enable()

        assertTrue(hostServices.logMessages.contains("Hello Python enabled."))
        assertTrue(hostServices.logMessages.contains("Server ready event observed."))

        val commandResult = hostServices.commandCallback.invoke(
            mapOf(
                "sender" to mapOf(
                    "name" to "Alice",
                    "type" to "player",
                    "id" to hostServices.player.id(),
                    "player" to hostServices.player,
                    "sendMessage" to Callback { payload ->
                        hostServices.sentMessages += payload.toString()
                        null
                    },
                    "hasPermission" to Callback { true },
                    "unsafe" to mapOf("handle" to Any()),
                ),
                "args" to emptyList<String>(),
                "label" to "hello",
                "command" to "hello",
            ),
        )

        assertEquals(null, commandResult)
        assertTrue(hostServices.sentMessages.contains("Hello from Python."))

        hostServices.eventCallbacks.getValue("server.ready").invoke(hostServices.serverReadyPayload())
        hostServices.eventCallbacks.getValue("player.join").invoke(hostServices.playerJoinPayload("Bob"))
        assertTrue(hostServices.logMessages.contains("Player joined: Bob"))

        plugin.close()
        assertTrue(hostServices.logMessages.contains("Hello Python disabled."))
    }

    private fun bundleExample(repoRoot: Path, examplePath: String = "examples/hello-ts") {
        val process = ProcessBuilder(
            "bun",
            "packages/cli/src/index.ts",
            "bundle",
            examplePath,
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
}
