package org.shiyun.lapislazuli.runtime.core.host

interface DataDirectory {
    fun path(): String

    fun resolve(vararg segments: String): String

    @Throws(Exception::class)
    fun readText(relativePath: String): String

    @Throws(Exception::class)
    fun writeText(relativePath: String, contents: String)

    fun exists(relativePath: String): Boolean

    @Throws(Exception::class)
    fun mkdirs(relativePath: String)
}
