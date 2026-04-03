package org.shiyun.lapis.runtimes.jvm.core.host

fun interface TaskHandle : AutoCloseable {
    @Throws(Exception::class)
    fun cancel()

    @Throws(Exception::class)
    override fun close() {
        cancel()
    }
}

