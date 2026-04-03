package org.shiyun.lapis.runtimes.jvm.core.host

import org.shiyun.lapis.runtimes.jvm.core.bundle.ScriptBundle

fun interface HostServicesFactory {
    @Throws(Exception::class)
    fun create(bundle: ScriptBundle): HostServices
}

