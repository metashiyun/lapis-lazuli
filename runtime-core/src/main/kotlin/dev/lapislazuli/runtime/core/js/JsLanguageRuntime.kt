package org.shiyun.lapislazuli.runtime.core.js

import org.shiyun.lapislazuli.runtime.core.bundle.BundleManifest
import org.shiyun.lapislazuli.runtime.core.bundle.ScriptBundle
import org.shiyun.lapislazuli.runtime.core.host.HostServices
import org.shiyun.lapislazuli.runtime.core.runtime.LanguageRuntime
import org.shiyun.lapislazuli.runtime.core.runtime.LoadedPlugin
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.nio.file.Files
import java.nio.file.Path

class JsLanguageRuntime(
    private val bridgeFactory: JsBridgeFactory = JsBridgeFactory(),
) : LanguageRuntime {
    override val engine: String = "js"

    override fun load(bundle: ScriptBundle, hostServices: HostServices): LoadedPlugin {
        val context = Context.newBuilder("js")
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup { true }
            .option("js.ecmascript-version", "latest")
            .allowIO(true)
            .allowAllAccess(true)
            .build()

        val pluginContext = bridgeFactory.createPluginContext(hostServices)
        val sourceText = Files.readString(bundle.mainFile)

        try {
            bootstrapCommonJs(context)
            context.eval(
                Source.newBuilder("js", sourceText, bundle.mainFile.toString()).build(),
            )
            val exports = requireNotNull(context.getBindings("js").getMember("module")?.getMember("exports")) {
                "Script bundle did not populate CommonJS exports."
            }
            val pluginValue = extractPlugin(exports)

            return JsLoadedPlugin(
                manifest = bundle.manifest,
                context = context,
                pluginValue = pluginValue,
                pluginContext = pluginContext,
            )
        } catch (error: PolyglotException) {
            context.close(true)
            throw IllegalArgumentException(formatLoadError(bundle.mainFile, sourceText, error), error)
        } catch (error: Exception) {
            context.close(true)
            throw error
        }
    }

    private fun bootstrapCommonJs(context: Context) {
        context.eval(
            Source.newBuilder(
                "js",
                "var module = { exports: {} }; var exports = module.exports;",
                "<lapis-cjs-bootstrap>",
            ).build(),
        )
    }

    private fun formatLoadError(mainFile: Path, sourceText: String, error: PolyglotException): String {
        val location = error.sourceLocation ?: return "Failed to evaluate JavaScript bundle at $mainFile: ${error.message ?: "Unknown error."}"
        val line = location.startLine
        val column = location.startColumn
        val sourceLine = sourceText.lineSequence().drop(line - 1).firstOrNull()
        val header = if (error.isSyntaxError) {
            "Failed to parse JavaScript bundle at $mainFile:$line:$column"
        } else {
            "Failed to evaluate JavaScript bundle at $mainFile:$line:$column"
        }
        val detail = error.message ?: "Unknown error."

        if (sourceLine == null) {
            return "$header: $detail"
        }

        val caretPadding = " ".repeat((column - 1).coerceAtLeast(0))
        return buildString {
            append(header)
            append(": ")
            append(detail)
            appendLine()
            append(sourceLine)
            appendLine()
            append(caretPadding)
            append("^")
        }
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
