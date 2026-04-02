package dev.lapislazuli.runtime.core.host

interface HostServices : AutoCloseable {
    fun logger(): RuntimeLogger

    @Throws(Exception::class)
    fun registerCommand(
        name: String,
        description: String,
        usage: String,
        aliases: List<String>,
        execute: Callback,
    ): Registration

    @Throws(Exception::class)
    fun registerEvent(eventKey: String, handler: Callback): Registration

    @Throws(Exception::class)
    fun runNow(task: Callback): TaskHandle

    @Throws(Exception::class)
    fun runLater(delayTicks: Long, task: Callback): TaskHandle

    @Throws(Exception::class)
    fun runTimer(delayTicks: Long, intervalTicks: Long, task: Callback): TaskHandle

    fun config(): ConfigStore

    fun dataDirectory(): DataDirectory

    @Throws(Exception::class)
    fun javaType(className: String): Class<*>

    @Throws(Exception::class)
    override fun close()
}

