package org.shiyun.lapis.runtimes.jvm.core.host

data class HostItemSpec(
    val type: String,
    val amount: Int = 1,
    val displayName: String? = null,
    val lore: List<String> = emptyList(),
    val enchantments: Map<String, Int> = emptyMap(),
)
