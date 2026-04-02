package dev.lapislazuli.runtime.core.host

import dev.lapislazuli.runtime.core.bundle.ScriptBundle

fun interface HostServicesFactory {
    @Throws(Exception::class)
    fun create(bundle: ScriptBundle): HostServices
}

