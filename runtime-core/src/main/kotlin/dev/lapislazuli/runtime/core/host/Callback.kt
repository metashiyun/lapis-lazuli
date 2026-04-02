package dev.lapislazuli.runtime.core.host

fun interface Callback {
    @Throws(Exception::class)
    fun invoke(payload: Any?): Any?
}

