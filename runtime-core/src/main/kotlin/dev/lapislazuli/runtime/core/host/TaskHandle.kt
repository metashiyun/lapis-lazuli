package dev.lapislazuli.runtime.core.host

fun interface TaskHandle : AutoCloseable {
    @Throws(Exception::class)
    fun cancel()

    @Throws(Exception::class)
    override fun close() {
        cancel()
    }
}

