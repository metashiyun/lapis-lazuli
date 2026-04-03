package dev.lapislazuli.runtimes.jvm.core.host

interface RuntimeLogger {
    fun info(message: String)

    fun warn(message: String)

    fun error(message: String, error: Throwable?)
}

