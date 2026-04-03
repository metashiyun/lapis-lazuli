package org.shiyun.lapislazuli.runtime.core.host

interface RuntimeLogger {
    fun info(message: String)

    fun warn(message: String)

    fun error(message: String, error: Throwable?)
}
