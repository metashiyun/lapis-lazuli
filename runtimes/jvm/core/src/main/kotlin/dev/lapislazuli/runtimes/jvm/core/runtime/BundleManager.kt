package dev.lapislazuli.runtimes.jvm.core.runtime

import dev.lapislazuli.runtimes.jvm.core.bundle.ScriptBundleLoader
import dev.lapislazuli.runtimes.jvm.core.host.HostServices
import dev.lapislazuli.runtimes.jvm.core.host.HostServicesFactory
import dev.lapislazuli.runtimes.jvm.core.host.RuntimeLogger
import java.nio.file.Path

class BundleManager(
    private val bundleLoader: ScriptBundleLoader,
    private val languageRuntimeRegistry: LanguageRuntimeRegistry,
    private val hostServicesFactory: HostServicesFactory,
    private val logger: RuntimeLogger,
) : AutoCloseable {
    private val runningBundles = linkedMapOf<String, RunningBundle>()

    @Throws(Exception::class)
    fun loadAll(bundlesRoot: Path): BundleLoadReport {
        val loadedBundles = mutableListOf<String>()
        val failedBundles = mutableListOf<String>()

        for (bundleDirectory in bundleLoader.listBundleDirectories(bundlesRoot)) {
            var hostServices: HostServices? = null

            try {
                val bundle = bundleLoader.load(bundleDirectory)
                val languageRuntime = languageRuntimeRegistry.require(bundle.manifest.engine)
                hostServices = hostServicesFactory.create(bundle)
                val loadedPlugin = languageRuntime.load(bundle, hostServices)
                loadedPlugin.enable()
                runningBundles[bundle.manifest.id] = RunningBundle(hostServices, loadedPlugin)
                loadedBundles += bundle.manifest.id
                logger.info("Loaded bundle ${bundle.manifest.id}")
            } catch (error: Exception) {
                failedBundles += bundleDirectory.fileName.toString()
                logger.error("Failed to load bundle at $bundleDirectory", error)
                hostServices?.let { safeClose(it, "host services for ${bundleDirectory.fileName}") }
            }
        }

        return BundleLoadReport(loadedBundles.toList(), failedBundles.toList())
    }

    @Throws(Exception::class)
    fun reloadAll(bundlesRoot: Path): BundleLoadReport {
        closeRunningBundles()
        return loadAll(bundlesRoot)
    }

    val loadedBundleIds: List<String>
        get() = runningBundles.keys.toList()

    override fun close() {
        closeRunningBundles()
    }

    private fun closeRunningBundles() {
        runningBundles.entries.toList().asReversed().forEach { (bundleId, runningBundle) ->
            safeClose(runningBundle.plugin, "plugin $bundleId")
            safeClose(runningBundle.hostServices, "host services for $bundleId")
        }
        runningBundles.clear()
    }

    private fun safeClose(closeable: AutoCloseable, label: String) {
        runCatching { closeable.close() }
            .onFailure { error -> logger.error("Failed to close $label", error) }
    }

    private data class RunningBundle(
        val hostServices: HostServices,
        val plugin: LoadedPlugin,
    )
}
