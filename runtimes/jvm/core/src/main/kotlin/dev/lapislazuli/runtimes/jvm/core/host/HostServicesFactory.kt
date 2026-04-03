package dev.lapislazuli.runtimes.jvm.core.host

import dev.lapislazuli.runtimes.jvm.core.bundle.ScriptBundle

fun interface HostServicesFactory {
    @Throws(Exception::class)
    fun create(bundle: ScriptBundle): HostServices
}

