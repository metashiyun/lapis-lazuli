package org.shiyun.lapislazuli.runtime.core.runtime

import org.shiyun.lapislazuli.runtime.core.bundle.ScriptBundle
import org.shiyun.lapislazuli.runtime.core.host.HostServices

interface LanguageRuntime {
    val engine: String

    @Throws(Exception::class)
    fun load(bundle: ScriptBundle, hostServices: HostServices): LoadedPlugin
}
