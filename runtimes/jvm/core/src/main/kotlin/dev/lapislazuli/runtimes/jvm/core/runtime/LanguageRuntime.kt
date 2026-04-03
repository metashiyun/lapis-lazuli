package dev.lapislazuli.runtimes.jvm.core.runtime

import dev.lapislazuli.runtimes.jvm.core.bundle.ScriptBundle
import dev.lapislazuli.runtimes.jvm.core.host.HostServices

interface LanguageRuntime {
    val engine: String

    @Throws(Exception::class)
    fun load(bundle: ScriptBundle, hostServices: HostServices): LoadedPlugin
}

