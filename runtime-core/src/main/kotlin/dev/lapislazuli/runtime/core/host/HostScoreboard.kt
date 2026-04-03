package dev.lapislazuli.runtime.core.host

interface HostScoreboard : AutoCloseable {
    fun id(): String?

    fun title(): String

    fun setTitle(title: String)

    fun setLine(score: Int, text: String)

    fun removeLine(score: Int)

    fun clear()

    fun viewers(): List<HostPlayer>

    fun show(player: HostPlayer)

    fun hide(player: HostPlayer)

    fun backendHandle(): Any?

    @Throws(Exception::class)
    override fun close() {
        viewers().forEach(::hide)
        clear()
    }
}
