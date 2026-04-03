package dev.lapislazuli.runtimes.jvm.core.js

import dev.lapislazuli.runtimes.jvm.core.bundle.BundleManifest
import dev.lapislazuli.runtimes.jvm.core.bundle.ScriptBundle
import dev.lapislazuli.runtimes.jvm.core.host.HostServices
import dev.lapislazuli.runtimes.jvm.core.runtime.LanguageRuntime
import dev.lapislazuli.runtimes.jvm.core.runtime.LoadedPlugin
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.nio.file.Files

class JsLanguageRuntime(
    private val bridgeFactory: JsBridgeFactory = JsBridgeFactory(),
) : LanguageRuntime {
    override val engine: String = "js"

    override fun load(bundle: ScriptBundle, hostServices: HostServices): LoadedPlugin {
        val context = Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup { true }
            .allowIO(true)
            .allowAllAccess(true)
            .build()

        val pluginContext = bridgeFactory.createPluginContext(hostServices)
        val sourceText = Files.readString(bundle.mainFile)
        val wrappedSource = wrapCommonJs(sourceText)
        val exports = context.eval(
            Source.newBuilder("js", wrappedSource, bundle.mainFile.toString()).build(),
        )
        val pluginValue = extractPlugin(exports)

        return JsLoadedPlugin(
            manifest = bundle.manifest,
            context = context,
            pluginValue = pluginValue,
            pluginContext = pluginContext,
        )
    }

    private fun wrapCommonJs(sourceText: String): String =
        buildString {
            appendLine("(function(){")
            appendLine("const module = { exports: {} };")
            appendLine("const exports = module.exports;")
            append(sourceText)
            appendLine()
            appendLine("return module.exports;")
            append("})();")
        }

    private fun extractPlugin(exports: Value): Value {
        val pluginValue = if (exports.hasMembers() && exports.hasMember("default")) {
            exports.getMember("default")
        } else {
            exports
        }

        require(pluginValue != null && pluginValue.hasMembers()) {
            "Script bundle must export a plugin object."
        }

        val name = pluginValue.getMember("name")
        require(name != null && name.isString) {
            "Script bundle must export a plugin object with a name."
        }

        return pluginValue
    }

    private class JsLoadedPlugin(
        override val manifest: BundleManifest,
        private val context: Context,
        private val pluginValue: Value,
        private val pluginContext: Any?,
    ) : LoadedPlugin {
        private var enabled = false

        override fun enable() {
            if (enabled) {
                return
            }

            pluginValue.getMember("onEnable")
                ?.takeIf(Value::canExecute)
                ?.execute(pluginContext)
            enabled = true
        }

        override fun close() {
            try {
                if (enabled) {
                    pluginValue.getMember("onDisable")
                        ?.takeIf(Value::canExecute)
                        ?.execute(pluginContext)
                }
            } finally {
                context.close(true)
            }
        }
    }
}
