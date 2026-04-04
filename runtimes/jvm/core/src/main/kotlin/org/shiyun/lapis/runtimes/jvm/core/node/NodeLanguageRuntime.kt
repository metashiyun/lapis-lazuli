package org.shiyun.lapis.runtimes.jvm.core.node

import org.shiyun.lapis.runtimes.jvm.core.bundle.BundleManifest
import org.shiyun.lapis.runtimes.jvm.core.bundle.ScriptBundle
import org.shiyun.lapis.runtimes.jvm.core.host.HostServices
import org.shiyun.lapis.runtimes.jvm.core.runtime.LanguageRuntime
import org.shiyun.lapis.runtimes.jvm.core.runtime.LoadedPlugin
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

class NodeLanguageRuntime(
    private val nodeCommand: String = System.getenv("LAPIS_NODE_COMMAND")
        ?.takeIf(String::isNotBlank)
        ?: "node",
) : LanguageRuntime {
    override val engine: String = "node"

    override fun load(bundle: ScriptBundle, hostServices: HostServices): LoadedPlugin {
        val bootstrapFile = materializeBootstrap()
        val bridgeHost = NodeBridgeHost(hostServices)

        val process = try {
            ProcessBuilder(nodeCommand, bootstrapFile.toString(), bundle.mainFile.toString())
                .directory(bundle.bundleDirectory.toFile())
                .start()
        } catch (error: IOException) {
            bootstrapFile.deleteIfExists()
            throw IllegalArgumentException(
                "Failed to start Node runtime using command \"$nodeCommand\": ${error.message}",
                error,
            )
        }

        val transport = NodeProcessTransport(process, bridgeHost::handleRequest)
        bridgeHost.attach(transport)

        try {
            transport.request("plugin.describe", null)
        } catch (error: Exception) {
            val stderr = transport.stderrText()
            bridgeHost.close()
            transport.close()
            process.destroyForcibly()
            bootstrapFile.deleteIfExists()
            val detail = if (stderr.isBlank()) {
                error.message ?: "Unknown error."
            } else {
                stderr
            }
            throw IllegalArgumentException("Failed to load Node bundle at ${bundle.mainFile}: $detail", error)
        }

        return NodeLoadedPlugin(
            manifest = bundle.manifest,
            transport = transport,
            bridgeHost = bridgeHost,
            process = process,
            bootstrapFile = bootstrapFile,
        )
    }

    private fun materializeBootstrap(): Path {
        val bootstrapUrl = requireNotNull(javaClass.getResource("/org/shiyun/lapis/runtimes/jvm/core/node/bootstrap.mjs")) {
            "Missing Node bootstrap resource."
        }
        val bootstrapText = bootstrapUrl.readText()
        val bootstrapFile = Files.createTempFile("lapis-node-bootstrap-", ".mjs")
        Files.writeString(bootstrapFile, bootstrapText)
        bootstrapFile.toFile().deleteOnExit()
        return bootstrapFile
    }

    private class NodeLoadedPlugin(
        override val manifest: BundleManifest,
        private val transport: NodeProcessTransport,
        private val bridgeHost: NodeBridgeHost,
        private val process: Process,
        private val bootstrapFile: Path,
    ) : LoadedPlugin {
        private var enabled = false
        private var closed = false

        override fun enable() {
            check(!closed) { "Node bundle ${manifest.id} is already closed." }
            if (enabled) {
                return
            }

            try {
                transport.request("plugin.enable", null)
                enabled = true
            } catch (error: Exception) {
                runCatching { transport.request("plugin.abort", null) }
                closeTransport()
                throw error
            }
        }

        override fun close() {
            if (closed) {
                return
            }
            closed = true

            try {
                if (enabled) {
                    runCatching { transport.request("plugin.disable", null) }
                }
            } finally {
                closeTransport()
            }
        }

        private fun closeTransport() {
            bridgeHost.close()
            transport.close()
            process.destroy()
            if (process.isAlive) {
                process.destroyForcibly()
            }
            bootstrapFile.deleteIfExists()
        }
    }
}
