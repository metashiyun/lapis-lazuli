package org.shiyun.lapislazuli.runtime.core.host

import org.shiyun.lapislazuli.runtime.core.bundle.ScriptBundle

fun interface HostServicesFactory {
    @Throws(Exception::class)
    fun create(bundle: ScriptBundle): HostServices
}
