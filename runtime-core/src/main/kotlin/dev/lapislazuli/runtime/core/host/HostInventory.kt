package dev.lapislazuli.runtime.core.host

interface HostInventory {
    fun id(): String?

    fun title(): String

    fun size(): Int

    fun getItem(slot: Int): HostItem?

    fun setItem(slot: Int, item: HostItem?)

    fun addItem(item: HostItem)

    fun clear()

    fun clearSlot(slot: Int)

    fun open(player: HostPlayer)

    fun backendHandle(): Any?
}
