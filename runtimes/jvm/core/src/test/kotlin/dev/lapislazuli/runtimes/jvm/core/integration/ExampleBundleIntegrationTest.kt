package dev.lapislazuli.runtimes.jvm.core.integration

import dev.lapislazuli.runtimes.jvm.core.bundle.BundleManifestParser
import dev.lapislazuli.runtimes.jvm.core.bundle.ScriptBundleLoader
import dev.lapislazuli.runtimes.jvm.core.host.Callback
import dev.lapislazuli.runtimes.jvm.core.js.JsLanguageRuntime
import dev.lapislazuli.runtimes.jvm.core.python.PythonLanguageRuntime
import dev.lapislazuli.runtimes.jvm.core.testsupport.FakeHostServices
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths

class ExampleBundleIntegrationTest {
    @Test
    fun bundlesExamplePluginAndLoadsItThroughTheRuntime() {
        val repoRoot = findRepoRoot()
        val exampleDir = repoRoot.resolve("examples/hello-ts")
        val bundleDir = exampleDir.resolve("dist/hello-ts")

        bundleExample(repoRoot)

        val bundle = ScriptBundleLoader(BundleManifestParser()).load(bundleDir)
        val hostServices = FakeHostServices(bundleDir.resolve("data-test"))
        val plugin = JsLanguageRuntime().load(bundle, hostServices)

        plugin.enable()

        assertTrue(hostServices.logMessages.contains("Hello TS enabled."))
        assertTrue(hostServices.broadcastMessages.contains("Hello TS is ready for 1 online player(s)."))

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

        assertEquals(true, commandResult)
        assertTrue(hostServices.sentMessages.any { it.contains("TypeScript") })

        hostServices.eventCallbacks.getValue("server.ready").invoke(hostServices.serverReadyPayload())
        hostServices.eventCallbacks.getValue("player.join").invoke(hostServices.playerJoinPayload("Bob"))
        assertTrue(hostServices.logMessages.contains("Player joined: Bob"))

        plugin.close()
        assertTrue(hostServices.logMessages.contains("Hello TS disabled."))
    }

    @Test
    fun bundlesExamplePythonPluginAndLoadsItThroughTheRuntime() {
        val repoRoot = findRepoRoot()
        val exampleDir = repoRoot.resolve("examples/hello-python")
        val bundleDir = exampleDir.resolve("dist/hello-python")

        bundleExample(repoRoot, "examples/hello-python")

        val bundle = ScriptBundleLoader(BundleManifestParser()).load(bundleDir)
        val hostServices = FakeHostServices(bundleDir.resolve("data-test"))
        val plugin = PythonLanguageRuntime().load(bundle, hostServices)

        plugin.enable()

        assertTrue(hostServices.logMessages.contains("Hello Python enabled."))
        assertTrue(hostServices.broadcastMessages.contains("Hello Python is ready for 1 online player(s)."))

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

        assertEquals(true, commandResult)
        assertTrue(hostServices.sentMessages.isNotEmpty())

        hostServices.eventCallbacks.getValue("server.ready").invoke(hostServices.serverReadyPayload())
        hostServices.eventCallbacks.getValue("player.join").invoke(hostServices.playerJoinPayload("Bob"))
        assertTrue(hostServices.logMessages.contains("Player joined: Bob"))

        plugin.close()
        assertTrue(hostServices.logMessages.contains("Hello Python disabled."))
    }

    private fun bundleExample(repoRoot: Path, examplePath: String = "examples/hello-ts") {
        val process = ProcessBuilder(
            "bun",
            "tooling/cli/src/index.ts",
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

    private fun findRepoRoot(): Path {
        val workingDir = Paths.get("").toAbsolutePath()
        return generateSequence(workingDir) { current -> current.parent }
            .firstOrNull { candidate -> candidate.resolve("settings.gradle.kts").toFile().isFile }
            ?: error("Could not locate repo root from $workingDir")
    }
}
