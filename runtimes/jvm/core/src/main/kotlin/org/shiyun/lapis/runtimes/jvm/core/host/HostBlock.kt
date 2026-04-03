package org.shiyun.lapis.runtimes.jvm.core.host

interface HostBlock {
    fun type(): String

    fun location(): HostLocation

    fun worldName(): String

    @Throws(Exception::class)
    fun setType(type: String): Boolean

    fun backendHandle(): Any?
}
