package dev.lapislazuli.runtime.core.runtime

import dev.lapislazuli.runtime.core.bundle.BundleManifest
import dev.lapislazuli.runtime.core.bundle.BundleManifestParser
import dev.lapislazuli.runtime.core.bundle.ScriptBundle
import dev.lapislazuli.runtime.core.bundle.ScriptBundleLoader
import dev.lapislazuli.runtime.core.host.Callback
import dev.lapislazuli.runtime.core.host.ConfigStore
import dev.lapislazuli.runtime.core.host.DataDirectory
import dev.lapislazuli.runtime.core.host.HostServices
import dev.lapislazuli.runtime.core.host.RuntimeLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BundleManagerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun continuesLoadingAfterOneBundleFails() {
        val bundlesRoot = tempDir.resolve("bundles")
        createBundle(bundlesRoot, "broken")
        createBundle(bundlesRoot, "working")

        val enabled = mutableListOf<String>()
        val manager = BundleManager(
            bundleLoader = ScriptBundleLoader(BundleManifestParser()),
            languageRuntimeRegistry = LanguageRuntimeRegistry(listOf(FakeRuntime(enabled))),
            hostServicesFactory = dev.lapislazuli.runtime.core.host.HostServicesFactory { FakeHostServices() },
            logger = TestLogger(),
        )

        val report = manager.loadAll(bundlesRoot)

        assertEquals(listOf("working"), report.loadedBundles)
        assertEquals(listOf("broken"), report.failedBundles)
        assertEquals(listOf("working"), enabled)

        manager.close()
    }

    @Test
    fun reloadAllClosesRunningBundlesBeforeLoadingUpdatedOnes() {
        val bundlesRoot = tempDir.resolve("bundles")
        createBundle(bundlesRoot, "working", "version=1")

        val lifecycle = mutableListOf<String>()
        val manager = BundleManager(
            bundleLoader = ScriptBundleLoader(BundleManifestParser()),
            languageRuntimeRegistry = LanguageRuntimeRegistry(listOf(TrackingRuntime(lifecycle))),
            hostServicesFactory = dev.lapislazuli.runtime.core.host.HostServicesFactory { FakeHostServices() },
            logger = TestLogger(),
        )

        val initialReport = manager.loadAll(bundlesRoot)
        Files.writeString(bundlesRoot.resolve("working").resolve("main.js"), "version=2")
        val reloadReport = manager.reloadAll(bundlesRoot)

        assertEquals(listOf("working"), initialReport.loadedBundles)
        assertEquals(listOf("working"), reloadReport.loadedBundles)
        assertEquals(listOf("enable:version=1", "close:version=1", "enable:version=2"), lifecycle)

        manager.close()

        assertEquals(
            listOf("enable:version=1", "close:version=1", "enable:version=2", "close:version=2"),
            lifecycle,
        )
    }

    private fun createBundle(
        root: Path,
        id: String,
        mainContents: String = "module.exports = { default: { name: '$id' } };",
    ) {
        val bundleDir = root.resolve(id)
        Files.createDirectories(bundleDir)
        Files.writeString(bundleDir.resolve("main.js"), mainContents)
        Files.writeString(
            bundleDir.resolve("lapis-plugin.json"),
            """
                {
                  "id": "$id",
                  "name": "$id",
                  "version": "1.0.0",
                  "engine": "js",
                  "main": "main.js",
                  "apiVersion": "1.0"
                }
            """.trimIndent(),
        )
    }

    private class FakeRuntime(
        private val enabled: MutableList<String>,
    ) : LanguageRuntime {
        override val engine: String = "js"

        override fun load(bundle: ScriptBundle, hostServices: HostServices): LoadedPlugin {
            if (bundle.manifest.id == "broken") {
                error("boom")
            }

            return object : LoadedPlugin {
                override val manifest: BundleManifest = bundle.manifest

                override fun enable() {
                    enabled += bundle.manifest.id
                }

                override fun close() {
                }
            }
        }
    }

    private class TrackingRuntime(
        private val lifecycle: MutableList<String>,
    ) : LanguageRuntime {
        override val engine: String = "js"

        override fun load(bundle: ScriptBundle, hostServices: HostServices): LoadedPlugin {
            val version = Files.readString(bundle.mainFile)

            return object : LoadedPlugin {
                override val manifest: BundleManifest = bundle.manifest

                override fun enable() {
                    lifecycle += "enable:$version"
                }

                override fun close() {
                    lifecycle += "close:$version"
                }
            }
        }
    }

    private class FakeHostServices : HostServices {
        override fun logger(): RuntimeLogger = TestLogger()

        override fun registerCommand(
            name: String,
            description: String,
            usage: String,
            aliases: List<String>,
            execute: Callback,
        ) = dev.lapislazuli.runtime.core.host.Registration {}

        override fun registerEvent(eventKey: String, handler: Callback) =
            dev.lapislazuli.runtime.core.host.Registration {}

        override fun registerJavaEvent(eventClassName: String, handler: Callback) =
            dev.lapislazuli.runtime.core.host.Registration {}

        override fun runNow(task: Callback) = dev.lapislazuli.runtime.core.host.TaskHandle {}

        override fun runLater(delayTicks: Long, task: Callback) =
            dev.lapislazuli.runtime.core.host.TaskHandle {}

        override fun runTimer(delayTicks: Long, intervalTicks: Long, task: Callback) =
            dev.lapislazuli.runtime.core.host.TaskHandle {}

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
                override fun path(): String = ""

                override fun resolve(vararg segments: String): String = ""

                override fun readText(relativePath: String): String = ""

                override fun writeText(relativePath: String, contents: String) {
                }

                override fun exists(relativePath: String): Boolean = false

                override fun mkdirs(relativePath: String) {
                }
            }

        override fun javaType(className: String): Class<*> = Class.forName(className)

        override fun serverHandle(): Any = Any()

        override fun pluginHandle(): Any = Any()

        override fun consoleSenderHandle(): Any = Any()

        override fun dispatchConsoleCommand(command: String): Boolean = true

        override fun broadcastMessage(message: String): Int = 0

        override fun close() {
        }
    }

    private class TestLogger : RuntimeLogger {
        override fun info(message: String) {
        }

        override fun warn(message: String) {
        }

        override fun error(message: String, error: Throwable?) {
        }
    }
}
