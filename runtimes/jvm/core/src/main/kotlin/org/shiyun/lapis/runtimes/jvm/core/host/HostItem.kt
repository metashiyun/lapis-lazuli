package org.shiyun.lapis.runtimes.jvm.core.host

interface HostItem {
    fun type(): String

    fun amount(): Int

    fun setAmount(amount: Int)

    fun displayName(): String?

    fun setDisplayName(displayName: String?)

    fun lore(): List<String>

    fun setLore(lines: List<String>)

    fun enchantments(): Map<String, Int>

    fun setEnchantment(key: String, level: Int)

    fun removeEnchantment(key: String)

    fun cloneItem(): HostItem

    fun tags(): KeyValueStore?

    fun backendHandle(): Any?
}
