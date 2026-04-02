package dev.lapislazuli.runtime.core.host

fun interface Registration : AutoCloseable {
    @Throws(Exception::class)
    fun unregister()

    @Throws(Exception::class)
    override fun close() {
        unregister()
    }
}

