package org.shiyun.lapis.runtimes.jvm.core.runtime

import org.shiyun.lapis.runtimes.jvm.core.bundle.BundleManifest
import org.shiyun.lapis.runtimes.jvm.core.bundle.BundleManifestParser
import org.shiyun.lapis.runtimes.jvm.core.bundle.ScriptBundle
import org.shiyun.lapis.runtimes.jvm.core.bundle.ScriptBundleLoader
import org.shiyun.lapis.runtimes.jvm.core.host.HostServices
import org.shiyun.lapis.runtimes.jvm.core.host.RuntimeLogger
import org.shiyun.lapis.runtimes.jvm.core.testsupport.FakeHostServices
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
            hostServicesFactory = org.shiyun.lapis.runtimes.jvm.core.host.HostServicesFactory { FakeHostServices(tempDir.resolve("data")) },
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
            hostServicesFactory = org.shiyun.lapis.runtimes.jvm.core.host.HostServicesFactory { FakeHostServices(tempDir.resolve("data")) },
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

    private class TestLogger : RuntimeLogger {
        override fun info(message: String) {
        }

        override fun warn(message: String) {
        }

        override fun error(message: String, error: Throwable?) {
        }
    }
}
