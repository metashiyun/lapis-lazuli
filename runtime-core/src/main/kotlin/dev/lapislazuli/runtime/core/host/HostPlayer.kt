package dev.lapislazuli.runtime.core.host

interface HostPlayer {
    fun id(): String

    fun name(): String

    fun worldName(): String

    fun location(): HostLocation

    fun sendMessage(message: String)

    fun sendActionBar(message: String)

    fun showTitle(
        title: String,
        subtitle: String,
        fadeInTicks: Int,
        stayTicks: Int,
        fadeOutTicks: Int,
    )

    fun hasPermission(permission: String): Boolean

    @Throws(Exception::class)
    fun teleport(location: HostLocation): Boolean

    fun inventory(): HostInventory

    fun tags(): KeyValueStore?

    fun backendHandle(): Any?
}
