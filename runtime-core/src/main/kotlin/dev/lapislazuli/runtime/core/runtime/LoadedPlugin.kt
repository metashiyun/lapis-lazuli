package org.shiyun.lapislazuli.runtime.core.runtime

import org.shiyun.lapislazuli.runtime.core.bundle.BundleManifest

interface LoadedPlugin : AutoCloseable {
    val manifest: BundleManifest

    @Throws(Exception::class)
    fun enable()

    @Throws(Exception::class)
    override fun close()
}
