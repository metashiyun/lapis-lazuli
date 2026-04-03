package dev.lapislazuli.runtimes.jvm.core.host

fun interface Callback {
    @Throws(Exception::class)
    fun invoke(payload: Any?): Any?
}

