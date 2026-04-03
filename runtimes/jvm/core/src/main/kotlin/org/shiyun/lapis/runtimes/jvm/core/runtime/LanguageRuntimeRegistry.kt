package org.shiyun.lapis.runtimes.jvm.core.runtime

class LanguageRuntimeRegistry(
    runtimes: Iterable<LanguageRuntime>,
) {
    private val runtimeByEngine = linkedMapOf<String, LanguageRuntime>()

    init {
        runtimes.forEach { runtimeByEngine[it.engine] = it }
    }

    fun require(engine: String): LanguageRuntime =
        runtimeByEngine[engine] ?: throw IllegalArgumentException("No runtime registered for engine \"$engine\"")
}

