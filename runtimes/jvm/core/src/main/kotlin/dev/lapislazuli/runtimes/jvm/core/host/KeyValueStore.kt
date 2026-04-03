package dev.lapislazuli.runtimes.jvm.core.host

interface KeyValueStore {
    fun get(path: String): Any?

    fun set(path: String, value: Any?)

    fun delete(path: String)

    @Throws(Exception::class)
    fun save()

    @Throws(Exception::class)
    fun reload()

    fun keys(): List<String>
}
