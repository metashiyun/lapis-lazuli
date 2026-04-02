package dev.lapislazuli.runtime.core.runtime

import dev.lapislazuli.runtime.core.bundle.ScriptBundle
import dev.lapislazuli.runtime.core.host.HostServices

interface LanguageRuntime {
    val engine: String

    @Throws(Exception::class)
    fun load(bundle: ScriptBundle, hostServices: HostServices): LoadedPlugin
}

