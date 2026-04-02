package dev.lapislazuli.runtime.bukkit

import dev.lapislazuli.runtime.core.bundle.BundleManifestParser
import dev.lapislazuli.runtime.core.bundle.ScriptBundleLoader
import dev.lapislazuli.runtime.core.host.RuntimeLogger
import dev.lapislazuli.runtime.core.js.JsLanguageRuntime
import dev.lapislazuli.runtime.core.runtime.BundleManager
import dev.lapislazuli.runtime.core.runtime.LanguageRuntimeRegistry
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Files
import java.util.logging.Level

class LapisLazuliPlugin : JavaPlugin() {
    private var bundleManager: BundleManager? = null

    override fun onEnable() {
        runCatching {
            val bundlesRoot = dataFolder.toPath().resolve("bundles")
            Files.createDirectories(bundlesRoot)

            val runtimeLogger = object : RuntimeLogger {
                override fun info(message: String) {
                    logger.info(message)
                }

                override fun warn(message: String) {
                    logger.warning(message)
                }

                override fun error(message: String, error: Throwable?) {
                    if (error == null) {
                        logger.severe(message)
                        return
                    }
                    logger.log(Level.SEVERE, message, error)
                }
            }

            bundleManager = BundleManager(
                ScriptBundleLoader(BundleManifestParser()),
                LanguageRuntimeRegistry(listOf(JsLanguageRuntime())),
                { bundle -> BukkitHostServices(this, bundle) },
                runtimeLogger,
            )

            val report = bundleManager!!.loadAll(bundlesRoot)
            logger.info("Loaded ${report.loadedBundles.size} bundle(s), failed ${report.failedBundles.size}.")
        }.onFailure { error ->
            logger.log(Level.SEVERE, "Failed to start Lapis Lazuli.", error)
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        bundleManager?.close()
        bundleManager = null
    }
}
