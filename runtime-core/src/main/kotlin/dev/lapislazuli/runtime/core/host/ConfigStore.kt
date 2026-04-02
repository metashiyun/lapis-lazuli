package dev.lapislazuli.runtime.core.host

interface ConfigStore {
    fun get(path: String): Any?

    fun set(path: String, value: Any?)

    @Throws(Exception::class)
    fun save()

    @Throws(Exception::class)
    fun reload()

    fun keys(): List<String>
}

