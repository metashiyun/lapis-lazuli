package dev.lapislazuli.runtimes.jvm.core.host

interface HostWorld {
    fun name(): String

    fun environment(): String

    fun players(): List<HostPlayer>

    fun entities(): List<HostEntity>

    fun spawnLocation(): HostLocation

    fun time(): Long

    fun setTime(time: Long)

    fun storming(): Boolean

    fun setStorming(storming: Boolean)

    fun blockAt(location: HostLocation): HostBlock?

    fun backendHandle(): Any?
}
