package dev.lapislazuli.runtime.core.host

interface HostEntity {
    fun id(): String

    fun type(): String

    fun customName(): String?

    fun worldName(): String

    fun location(): HostLocation

    @Throws(Exception::class)
    fun teleport(location: HostLocation): Boolean

    fun remove()

    fun tags(): KeyValueStore?

    fun backendHandle(): Any?
}
