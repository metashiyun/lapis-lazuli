package org.shiyun.lapis.runtimes.jvm.core.runtime

import org.shiyun.lapis.runtimes.jvm.core.bundle.ScriptBundle
import org.shiyun.lapis.runtimes.jvm.core.host.HostServices

interface LanguageRuntime {
    val engine: String

    @Throws(Exception::class)
    fun load(bundle: ScriptBundle, hostServices: HostServices): LoadedPlugin
}

