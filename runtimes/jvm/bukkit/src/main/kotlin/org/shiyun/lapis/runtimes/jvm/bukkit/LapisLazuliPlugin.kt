package org.shiyun.lapis.runtimes.jvm.bukkit

import org.shiyun.lapis.runtimes.jvm.core.bundle.BundleManifestParser
import org.shiyun.lapis.runtimes.jvm.core.bundle.ScriptBundleLoader
import org.shiyun.lapis.runtimes.jvm.core.host.RuntimeLogger
import org.shiyun.lapis.runtimes.jvm.core.js.JsLanguageRuntime
import org.shiyun.lapis.runtimes.jvm.core.node.NodeLanguageRuntime
import org.shiyun.lapis.runtimes.jvm.core.python.PythonLanguageRuntime
import org.shiyun.lapis.runtimes.jvm.core.runtime.BundleManager
import org.shiyun.lapis.runtimes.jvm.core.runtime.LanguageRuntimeRegistry
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level

class LapisLazuliPlugin : JavaPlugin() {
    private var bundleManager: BundleManager? = null
    private var hotReloadSnapshot: List<String> = emptyList()
    private var hotReloadTaskId: Int? = null
    private lateinit var bundlesRoot: Path

    override fun onEnable() {
        runCatching {
            saveDefaultConfig()

            bundlesRoot = dataFolder.toPath().resolve("bundles")
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
                LanguageRuntimeRegistry(
                    listOf(
                        JsLanguageRuntime(),
                        NodeLanguageRuntime(
                            config.getString("node.command")
                                ?.takeIf(String::isNotBlank)
                                ?: "node",
                        ),
                        PythonLanguageRuntime(),
                    ),
                ),
                { bundle -> BukkitHostServices(this, bundle) },
                runtimeLogger,
            )

            val report = bundleManager!!.loadAll(bundlesRoot)
            hotReloadSnapshot = BundleDirectorySnapshot.capture(bundlesRoot)
            logger.info("Loaded ${report.loadedBundles.size} bundle(s), failed ${report.failedBundles.size}.")
            startHotReloadPolling()
        }.onFailure { error ->
            logger.log(Level.SEVERE, "Failed to start Lapis Lazuli.", error)
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        hotReloadTaskId?.let(server.scheduler::cancelTask)
        hotReloadTaskId = null
        bundleManager?.close()
        bundleManager = null
        hotReloadSnapshot = emptyList()
    }

    private fun startHotReloadPolling() {
        if (!config.getBoolean("hotReload.enabled", true)) {
            logger.info("Bundle hot reload is disabled.")
            return
        }

        val pollIntervalTicks = config.getLong("hotReload.pollIntervalTicks", 20L).coerceAtLeast(1L)
        hotReloadTaskId = server.scheduler.runTaskTimer(
            this,
            Runnable { pollBundleChanges() },
            pollIntervalTicks,
            pollIntervalTicks,
        ).taskId
        logger.info("Watching bundle directory for hot reload every $pollIntervalTicks tick(s).")
    }

    private fun pollBundleChanges() {
        val manager = bundleManager ?: return
        val currentSnapshot = runCatching { BundleDirectorySnapshot.capture(bundlesRoot) }
            .getOrElse { error ->
                logger.log(Level.SEVERE, "Failed to scan bundles for hot reload.", error)
                return
            }

        if (currentSnapshot == hotReloadSnapshot) {
            return
        }

        hotReloadSnapshot = currentSnapshot

        val report = runCatching { manager.reloadAll(bundlesRoot) }
            .getOrElse { error ->
                logger.log(Level.SEVERE, "Failed to hot reload bundles.", error)
                return
            }

        logger.info(
            "Hot reloaded bundles after detecting changes. Loaded ${report.loadedBundles.size} bundle(s), failed ${report.failedBundles.size}.",
        )
    }
}
