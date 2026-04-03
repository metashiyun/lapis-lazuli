package org.shiyun.lapis.runtimes.jvm.core.host

interface HostBossBar : AutoCloseable {
    fun id(): String?

    fun title(): String

    fun setTitle(title: String)

    fun progress(): Double

    fun setProgress(progress: Double)

    fun color(): String

    fun setColor(color: String)

    fun style(): String

    fun setStyle(style: String)

    fun players(): List<HostPlayer>

    fun addPlayer(player: HostPlayer)

    fun removePlayer(player: HostPlayer)

    fun removeAllPlayers()

    fun backendHandle(): Any?

    @Throws(Exception::class)
    override fun close() {
        removeAllPlayers()
    }
}
