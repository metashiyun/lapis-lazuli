package org.shiyun.lapis.runtimes.jvm.core.host

fun interface Callback {
    @Throws(Exception::class)
    fun invoke(payload: Any?): Any?
}

