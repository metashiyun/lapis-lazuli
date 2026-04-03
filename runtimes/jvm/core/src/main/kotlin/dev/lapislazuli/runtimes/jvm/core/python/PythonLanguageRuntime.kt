package dev.lapislazuli.runtimes.jvm.core.python

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import dev.lapislazuli.runtimes.jvm.core.bundle.BundleManifest
import dev.lapislazuli.runtimes.jvm.core.bundle.ScriptBundle
import dev.lapislazuli.runtimes.jvm.core.host.HostServices
import dev.lapislazuli.runtimes.jvm.core.js.JsBridgeFactory
import dev.lapislazuli.runtimes.jvm.core.runtime.LanguageRuntime
import dev.lapislazuli.runtimes.jvm.core.runtime.LoadedPlugin
import java.nio.file.Path

class PythonLanguageRuntime(
    private val bridgeFactory: JsBridgeFactory = JsBridgeFactory(),
) : LanguageRuntime {
    override val engine: String = "python"

    override fun load(bundle: ScriptBundle, hostServices: HostServices): LoadedPlugin {
        val context = Context.newBuilder("python")
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup { true }
            .allowAllAccess(true)
            .currentWorkingDirectory(bundle.bundleDirectory)
            .build()

        val pluginContext = bridgeFactory.createPluginContext(hostServices)

        try {
            val bindings = context.getBindings("python")
            bindings.putMember("__lapis_bundle_dir__", bundle.bundleDirectory.toString())
            bindings.putMember("__lapis_main_file__", bundle.mainFile.toString())

            context.eval(
                Source.newBuilder("python", PYTHON_BOOTSTRAP, "<lapis-python-bootstrap>").build(),
            )

            val pluginValue = extractPlugin(bindings)

            return PythonLoadedPlugin(
                manifest = bundle.manifest,
                context = context,
                pluginValue = pluginValue,
                pluginContext = pluginContext,
            )
        } catch (error: PolyglotException) {
            context.close(true)
            throw IllegalArgumentException(formatLoadError(bundle.mainFile, error), error)
        } catch (error: Exception) {
            context.close(true)
            throw error
        }
    }

    private fun extractPlugin(bindings: Value): Value {
        val pluginValue = bindings.member("__lapis_plugin__")
            ?.takeUnless(Value::isNull)
            ?: requireNotNull(bindings.member("__lapis_module__")) {
                "Python bundle bootstrap did not expose the loaded module."
            }

        require(pluginValue.hasMembers() || pluginValue.hasHashEntries()) {
            "Python bundle must expose plugin members."
        }

        val name = pluginValue.member("name")
        require(name != null && name.isString) {
            "Python bundle must expose a plugin name."
        }

        return pluginValue
    }

    private fun formatLoadError(mainFile: Path, error: PolyglotException): String {
        val detail = error.message ?: "Unknown error."
        val isParseError = error.isSyntaxError || detail.contains("SyntaxError")
        val location = error.sourceLocation ?: return buildString {
            append(
                if (isParseError) {
                    "Failed to parse Python bundle at $mainFile"
                } else {
                    "Failed to evaluate Python bundle at $mainFile"
                },
            )
            append(": ")
            append(detail)
        }

        val prefix = if (isParseError) {
            "Failed to parse Python bundle"
        } else {
            "Failed to evaluate Python bundle"
        }

        return "$prefix at ${location.source.name}:${location.startLine}:${location.startColumn}: $detail"
    }

    private fun Value.member(name: String): Value? =
        when {
            hasMembers() && hasMember(name) -> getMember(name)
            hasHashEntries() && hasHashEntry(name) -> getHashValue(name)
            else -> null
        }

    private class PythonLoadedPlugin(
        override val manifest: BundleManifest,
        private val context: org.graalvm.polyglot.Context,
        private val pluginValue: Value,
        private val pluginContext: Any?,
    ) : LoadedPlugin {
        private var enabled = false

        override fun enable() {
            if (enabled) {
                return
            }

            lifecycleHook("onEnable", "on_enable")
                ?.takeIf(Value::canExecute)
                ?.execute(pluginContext)
            enabled = true
        }

        override fun close() {
            try {
                if (enabled) {
                    lifecycleHook("onDisable", "on_disable")
                        ?.takeIf(Value::canExecute)
                        ?.execute(pluginContext)
                }
            } finally {
                context.close(true)
            }
        }

        private fun lifecycleHook(camelCaseName: String, snakeCaseName: String): Value? =
            pluginValue.member(camelCaseName) ?: pluginValue.member(snakeCaseName)

        private fun Value.member(name: String): Value? =
            when {
                hasMembers() && hasMember(name) -> getMember(name)
                hasHashEntries() && hasHashEntry(name) -> getHashValue(name)
                else -> null
            }
    }

    private companion object {
        private val PYTHON_BOOTSTRAP = """
            import importlib.util
            import pathlib
            import sys
            
            _bundle_dir = pathlib.Path(__lapis_bundle_dir__)
            _main_file = pathlib.Path(__lapis_main_file__)
            _main_parent = _main_file.parent
            
            for _search_path in (str(_main_parent), str(_bundle_dir)):
                if _search_path not in sys.path:
                    sys.path.insert(0, _search_path)
            
            _spec = importlib.util.spec_from_file_location("__lapis_plugin__", str(_main_file))
            if _spec is None or _spec.loader is None:
                raise RuntimeError(f"Unable to load Python bundle from {_main_file}")
            
            _module = importlib.util.module_from_spec(_spec)
            sys.modules[_spec.name] = _module
            _spec.loader.exec_module(_module)
            
            __lapis_module__ = _module
            __lapis_plugin__ = getattr(_module, "plugin", None)
        """.trimIndent()
    }
}
